package mpmToolbox.projectData.annotations;

import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Node;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts annotations from and to the <code>&lt;annotations&gt;</code> section of an MPR.
 * The <code>annotations</code> element itself has no namespace. It contains a <code>score</code> element that declares the
 * MEI namespace as default namespace, so it and all of its descendants are MEI elements (no inner mei container, no prefix),
 * followed by one non-MEI <code>placement</code> element per annotation placement:
 * <code>&lt;placement id="annot-id" tab="score" source="page.svg" x="-0.2" y="0.4" w="0.1" h="0.2"/&gt;</code>
 * (relative values, except x of the audio tab, which is a time in milliseconds; start and end instead of x, y, w, h denote a range, x and y only a point; source is the score page or audio file).
 * Times (when, clip) are written into one <code>recording</code> per audio file, which names the file in an <code>avFile</code>.
 * <p>
 * Mapping:
 * <ul>
 * <li>id, title, text, category, part: annot/@xml:id, annot/@label, annot/p, annot/@type, annot/locus</li>
 * <li>createdAt, updatedAt: annot/date[@type='created'|'updated']/@isodate</li>
 * <li>measureFrom, measureTo: annot/@tstamp and annot/@tstamp2, written as "{measure}m+0"</li>
 * <li>links: annot/@plist, in their original order; the first and the last MSM/MPM link are also annot/@startid and annot/@endid
 *     <ul>
 *     <li>MSM, MPM: "#id" (there is exactly one MSM and one MPM per MPR, so no file is stored)</li>
 *     <li>SVG: "file.svg#id" (target of the link has the same form)</li>
 *     <li>TIMESTAMP: "#id" of a when (abstype "smil", absolute "{ms}ms")</li>
 *     <li>TIME_RANGE: "#id" of a clip (betype "smil", begin/end "{ms}ms"); the link target is "begin,end" in milliseconds</li>
 *     <li>SCORE_AREA: "#id" of a zone type="annotationReference" in a surface type="scorePage";
 *         the link target is "x,y,width,height,pagePath" with relative coordinates</li>
 *     <li>SPECTROGRAM_AREA: "#id" of a zone type="annotationReference" in a surface type="audioSpectrogram";
 *         the link target is "x,y,width,height" with relative coordinates</li>
 *     </ul></li>
 * </ul>
 * Area coordinates (0..1) are mapped to integers 0..{@value #COORDINATE_RANGE} (rounded, clamped to the page).
 * IDs of generated helper elements are opaque; the meaning of a reference is always determined by the referenced element.
 */
public class MeiAnnotationConverter {
    public static final String MEI_NS = "http://www.music-encoding.org/ns/mei";
    public static final int COORDINATE_RANGE = 1_000_000;

    private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";
    private static final String SURFACE_SCORE_PAGE = "scorePage";
    private static final String SURFACE_AUDIO = "audioSpectrogram";
    private static final String ZONE_REFERENCE = "annotationReference";
    private static final String TIME_TYPE = "smil";

    private static final Pattern XML_ID = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_.\\-]*");
    private static final Pattern SMIL_MS = Pattern.compile("(\\d+(?:\\.\\d+)?)ms");
    private static final Pattern MEASURE = Pattern.compile("(-?\\d+|\\?)m\\+0");

    private MeiAnnotationConverter() {
    }

    // ---------------------------------------------------------------- annotation -> XML

    /**
     * Convert annotations to an <code>annotations</code> element in the MEI namespace.
     * @param annotations the annotations
     * @return the XML element
     * @throws IllegalArgumentException if an annotation cannot be represented validly
     */
    public static Element toXML(Collection<? extends AnnotationData> annotations) {
        Objects.requireNonNull(annotations, "annotations");
        Element root = new Element("annotations");
        Element score = mei("score");
        Element facsimile = mei("facsimile");

        Set<String> usedIds = new HashSet<>();
        for (AnnotationData annotation : annotations) {
            requireXmlId(annotation.getId(), "annotation id");
            if (!usedIds.add(annotation.getId())) {
                throw new IllegalArgumentException("duplicate xml:id " + annotation.getId());
            }
        }

        Map<String, Element> surfaces = new LinkedHashMap<>();
        Map<String, Element> recordings = new LinkedHashMap<>();
        List<Element> annotElements = new ArrayList<>();
        List<Element> placements = new ArrayList<>();
        for (AnnotationData annotation : annotations) {
            annotElements.add(annotationToXml(annotation, recordings, surfaces, usedIds));
            for (AnnotationPlacement placement : annotation.getPlacements()) {
                placements.add(placementToXml(annotation.getId(), placement));
            }
        }

        for (Element recording : recordings.values()) score.appendChild(recording);
        if (!surfaces.isEmpty()) {
            for (Element surface : surfaces.values()) facsimile.appendChild(surface);
            score.appendChild(facsimile);
        }
        for (Element annot : annotElements) score.appendChild(annot);
        root.appendChild(score);
        for (Element placement : placements) root.appendChild(placement);
        return root;
    }

    private static Element annotationToXml(AnnotationData data, Map<String, Element> recordings, Map<String, Element> surfaces, Set<String> usedIds) {
        Element annot = mei("annot");
        setXmlId(annot, data.getId());
        annot.addAttribute(new Attribute("label", data.getTitle()));
        if (!data.getCategory().isEmpty()) {
            annot.addAttribute(new Attribute("type", data.getCategory()));
        }

        writeMeasures(annot, data);

        List<String> plist = new ArrayList<>();
        String startId = null;
        String endId = null;
        for (AnnotationLink link : data.getLinks()) {
            plist.add(linkToToken(link, recordings, surfaces, usedIds));
            if ((link.getType() == AnnotationLink.LinkType.MSM) || (link.getType() == AnnotationLink.LinkType.MPM)) {
                if (startId == null) startId = "#" + link.getTarget();
                endId = "#" + link.getTarget();
            }
        }
        if (startId != null) {
            annot.addAttribute(new Attribute("startid", startId));
            annot.addAttribute(new Attribute("endid", endId));
        }
        if (!plist.isEmpty()) {
            annot.addAttribute(new Attribute("plist", String.join(" ", plist)));
        }

        if (!data.getPart().isEmpty()) {
            Element locus = mei("locus");
            locus.appendChild(data.getPart());
            annot.appendChild(locus);
        }
        Element p = mei("p");
        p.appendChild(data.getText());
        annot.appendChild(p);
        annot.appendChild(dateToXml("created", data.getCreatedAt()));
        annot.appendChild(dateToXml("updated", data.getUpdatedAt()));
        return annot;
    }

    private static Element dateToXml(String type, long epochMillis) {
        Element date = mei("date");
        date.addAttribute(new Attribute("type", type));
        date.addAttribute(new Attribute("isodate", Instant.ofEpochMilli(epochMillis).toString()));
        return date;
    }

    private static void writeMeasures(Element annot, AnnotationData data) {
        if (!data.getMeasureFrom().isEmpty()) {
            annot.addAttribute(new Attribute("tstamp", data.getMeasureFrom() + "m+0"));
            if (!data.getMeasureTo().isEmpty()) {
                annot.addAttribute(new Attribute("tstamp2", data.getMeasureTo() + "m+0"));
            }
        }
    }

    /**
     * Write the helper elements of a link, if it needs some, and return its plist token.
     */
    private static String linkToToken(AnnotationLink link, Map<String, Element> recordings, Map<String, Element> surfaces, Set<String> usedIds) {
        String target = link.getTarget();
        switch (link.getType()) {
            case MSM:
            case MPM:
                requireXmlId(target, link.getType() + " element id");
                return "#" + target;
            case SVG:
                return svgTargetToUri(target);
            case TIMESTAMP:
                return "#" + timestampToWhen(target, getRecording(recordings, link.getSource(), usedIds), usedIds);
            case TIME_RANGE:
                return "#" + timeRangeToClip(target, getRecording(recordings, link.getSource(), usedIds), usedIds);
            case SCORE_AREA:
            case SPECTROGRAM_AREA:
                return "#" + areaToZone(link, surfaces, usedIds);
            default:
                throw new IllegalArgumentException("unsupported link type " + link.getType());
        }
    }

    /**
     * There is one recording per audio file; times without a known audio file go to a recording without avFile.
     */
    private static Element getRecording(Map<String, Element> recordings, String source, Set<String> usedIds) {
        Element recording = recordings.get(source);
        if (recording == null) {
            recording = mei("recording");
            setXmlId(recording, newId("recording", usedIds));
            if (!source.isEmpty()) {
                Element avFile = mei("avFile");
                avFile.addAttribute(new Attribute("target", source));
                String mimeType = mimeTypeOf(source);
                if (mimeType != null) {
                    avFile.addAttribute(new Attribute("mimetype", mimeType));
                }
                recording.appendChild(avFile);
            }
            recordings.put(source, recording);
        }
        return recording;
    }

    private static String mimeTypeOf(String source) {
        String lower = source.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".aif") || lower.endsWith(".aiff")) return "audio/aiff";
        return null;
    }

    private static String timestampToWhen(String target, Element recording, Set<String> usedIds) {
        double ms = parseMillis(target);
        Element when = mei("when");
        String id = newId("time", usedIds);
        setXmlId(when, id);
        when.addAttribute(new Attribute("absolute", millisToSmil(ms)));
        when.addAttribute(new Attribute("abstype", TIME_TYPE));
        recording.appendChild(when);
        return id;
    }

    private static String timeRangeToClip(String target, Element recording, Set<String> usedIds) {
        String[] parts = target.split(",", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid time range '" + target + "', expected 'begin,end'");
        }
        double begin = parseMillis(parts[0]);
        double end = parseMillis(parts[1]);
        if (begin >= end) {
            throw new IllegalArgumentException("invalid clip, begin must be less than end: " + target);
        }
        Element clip = mei("clip");
        String id = newId("clip", usedIds);
        setXmlId(clip, id);
        clip.addAttribute(new Attribute("begin", millisToSmil(begin)));
        clip.addAttribute(new Attribute("end", millisToSmil(end)));
        clip.addAttribute(new Attribute("betype", TIME_TYPE));
        recording.appendChild(clip);
        return id;
    }

    private static String areaToZone(AnnotationLink link, Map<String, Element> surfaces, Set<String> usedIds) {
        boolean score = link.getType() == AnnotationLink.LinkType.SCORE_AREA;
        String[] parts = link.getTarget().split(",", score ? 5 : 4);
        if (parts.length != (score ? 5 : 4)) {
            throw new IllegalArgumentException("invalid area '" + link.getTarget() + "', expected 'x,y,width,height" + (score ? ",pagePath" : "") + "'");
        }
        double[] rect = new double[4];
        for (int i = 0; i < 4; ++i) {
            rect[i] = parseDouble(parts[i], "area coordinate");
        }
        String page = score ? parts[4] : "";
        if (score && page.isEmpty()) {
            throw new IllegalArgumentException("score area without page: " + link.getTarget());
        }
        Element zone = newZone(getSurface(surfaces, score ? SURFACE_SCORE_PAGE : SURFACE_AUDIO, page), ZONE_REFERENCE, usedIds);
        writeRect(zone, rect[0], rect[1], rect[2], rect[3]);
        return getXmlId(zone);
    }

    private static Element placementToXml(String annotationId, AnnotationPlacement placement) {
        Element e = new Element("placement");
        e.addAttribute(new Attribute("id", annotationId));
        e.addAttribute(new Attribute("tab", placement.getTabType().getXmlValue()));
        if (!placement.getPagePath().isEmpty()) {
            e.addAttribute(new Attribute("source", placement.getPagePath()));
        }
        switch (placement.getPlacementType()) {
            case RANGE:
                e.addAttribute(new Attribute("start", finite(placement.getStart())));
                e.addAttribute(new Attribute("end", finite(placement.getEnd())));
                break;
            case RECT:
                e.addAttribute(new Attribute("x", finite(placement.getX())));
                e.addAttribute(new Attribute("y", finite(placement.getY())));
                e.addAttribute(new Attribute("w", finite(placement.getWidth())));
                e.addAttribute(new Attribute("h", finite(placement.getHeight())));
                break;
            case POINT:
            default:
                e.addAttribute(new Attribute("x", finite(placement.getX())));
                e.addAttribute(new Attribute("y", finite(placement.getY())));
        }
        return e;
    }

    private static String finite(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("invalid placement value " + value);
        }
        return Double.toString(value);
    }

    private static Element getSurface(Map<String, Element> surfaces, String type, String page) {
        String key = type + "\u0000" + page;
        Element surface = surfaces.get(key);
        if (surface == null) {
            surface = mei("surface");
            setXmlId(surface, "surface-" + UUID.randomUUID());
            surface.addAttribute(new Attribute("type", type));
            surface.addAttribute(new Attribute("ulx", "0"));
            surface.addAttribute(new Attribute("uly", "0"));
            surface.addAttribute(new Attribute("lrx", Integer.toString(COORDINATE_RANGE)));
            surface.addAttribute(new Attribute("lry", Integer.toString(COORDINATE_RANGE)));
            if (!page.isEmpty()) {
                Element graphic = mei("graphic");
                graphic.addAttribute(new Attribute("target", page));
                surface.appendChild(graphic);
            }
            surfaces.put(key, surface);
        }
        return surface;
    }

    private static Element newZone(Element surface, String type, Set<String> usedIds) {
        Element zone = mei("zone");
        setXmlId(zone, newId("zone", usedIds));
        zone.addAttribute(new Attribute("type", type));
        surface.appendChild(zone);
        return zone;
    }

    // ---------------------------------------------------------------- XML -> annotation

    /**
     * Convert an <code>annotations</code> element back to annotations.
     * @param annotations the annotations element (MEI namespace)
     * @param msmHasId tells whether the MSM of the MPR contains an element with the given id, null if there is no MSM
     * @param mpmHasId tells whether the MPM of the MPR contains an element with the given id, null if there is no MPM
     * @return the annotations
     * @throws IllegalArgumentException if the XML is invalid, contains dangling, unknown or ambiguous references
     */
    public static ArrayList<AnnotationData> fromXML(Element annotations, Predicate<String> msmHasId, Predicate<String> mpmHasId) {
        if ((annotations == null) || !"annotations".equals(annotations.getLocalName())) {
            throw new IllegalArgumentException("expected an annotations element");
        }
        Predicate<String> msm = (msmHasId == null) ? id -> false : msmHasId;
        Predicate<String> mpm = (mpmHasId == null) ? id -> false : mpmHasId;

        Element score = annotations.getFirstChildElement("score", MEI_NS);
        if (score == null) {
            throw new IllegalArgumentException("annotations without a score element in the namespace " + MEI_NS);
        }

        // phase 1: index all xml:ids and the surfaces of the zones
        Map<String, Element> ids = new HashMap<>();
        Map<Element, Element> zoneSurfaces = new IdentityHashMap<>();
        indexIds(score, ids, zoneSurfaces);

        // phase 2: parse the annotations and resolve their references
        ArrayList<AnnotationData> result = new ArrayList<>();
        Map<String, AnnotationData> byId = new HashMap<>();
        Elements annots = score.getChildElements("annot", MEI_NS);
        for (int i = 0; i < annots.size(); ++i) {
            AnnotationData data = annotationFromXml(annots.get(i), ids, zoneSurfaces, msm, mpm);
            result.add(data);
            byId.put(data.getId(), data);
        }

        Elements placements = annotations.getChildElements("placement");
        for (int i = 0; i < placements.size(); ++i) {
            Element placement = placements.get(i);
            String id = placement.getAttributeValue("id");
            AnnotationData owner = (id == null) ? null : byId.get(id);
            if (owner == null) {
                throw new IllegalArgumentException("dangling reference: placement for unknown annotation " + id);
            }
            owner.getPlacements().add(placementFromXml(placement));
        }
        return result;
    }

    private static AnnotationPlacement placementFromXml(Element e) {
        AnnotationPlacement.TabType tab = AnnotationPlacement.TabType.fromXmlValue(e.getAttributeValue("tab"));
        AnnotationPlacement.PlacementType kind;
        if (e.getAttribute("start") != null) {
            kind = AnnotationPlacement.PlacementType.RANGE;
        } else if (e.getAttribute("w") != null) {
            kind = AnnotationPlacement.PlacementType.RECT;
        } else {
            kind = AnnotationPlacement.PlacementType.POINT;
        }
        AnnotationPlacement placement = new AnnotationPlacement(tab, kind);
        switch (kind) {
            case RANGE:
                placement.setStart(parseDouble(e.getAttributeValue("start"), "placement start"));
                placement.setEnd(parseDouble(e.getAttributeValue("end"), "placement end"));
                break;
            case RECT:
                placement.setWidth(parseDouble(e.getAttributeValue("w"), "placement w"));
                placement.setHeight(parseDouble(e.getAttributeValue("h"), "placement h"));
                placement.setX(parseDouble(e.getAttributeValue("x"), "placement x"));
                placement.setY(parseDouble(e.getAttributeValue("y"), "placement y"));
                break;
            default:
                placement.setX(parseDouble(e.getAttributeValue("x"), "placement x"));
                placement.setY(parseDouble(e.getAttributeValue("y"), "placement y"));
        }
        placement.setPagePath(e.getAttributeValue("source"));
        return placement;
    }

    private static void indexIds(Element parent, Map<String, Element> ids, Map<Element, Element> zoneSurfaces) {
        Elements children = parent.getChildElements();
        for (int i = 0; i < children.size(); ++i) {
            Element child = children.get(i);
            String id = child.getAttributeValue("id", XML_NS);
            if (id != null) {
                requireXmlId(id, "xml:id");
                if (ids.put(id, child) != null) {
                    throw new IllegalArgumentException("duplicate xml:id " + id);
                }
            }
            if ("zone".equals(child.getLocalName()) && "surface".equals(parent.getLocalName())) {
                zoneSurfaces.put(child, parent);
            }
            indexIds(child, ids, zoneSurfaces);
        }
    }

    private static AnnotationData annotationFromXml(Element annot, Map<String, Element> ids, Map<Element, Element> zoneSurfaces,
                                                    Predicate<String> msm, Predicate<String> mpm) {
        String id = annot.getAttributeValue("id", XML_NS);
        if (id == null) {
            throw new IllegalArgumentException("annot without xml:id");
        }

        StringBuilder text = new StringBuilder();
        Elements paragraphs = annot.getChildElements("p", MEI_NS);
        for (int i = 0; i < paragraphs.size(); ++i) {
            if (i > 0) text.append('\n');
            text.append(paragraphs.get(i).getValue());
        }

        AnnotationData data = new AnnotationData(text.toString());
        data.setId(id);
        data.setTitle(attributeOrEmpty(annot, "label"));
        data.setCategory(attributeOrEmpty(annot, "type"));

        Element locus = annot.getFirstChildElement("locus", MEI_NS);
        if (locus != null) {
            data.setPart(locus.getValue());
        }

        readMeasures(annot, data);

        // plist: heterogeneous references in their original order
        for (String token : splitList(annot.getAttributeValue("plist"))) {
            data.getLinks().add(tokenToLink(token, ids, zoneSurfaces, msm, mpm));
        }

        // startid, endid: complete the links if they are not part of the plist
        String startToken = annot.getAttributeValue("startid");
        if (startToken != null) {
            AnnotationLink start = musicLink(startToken, ids, msm, mpm);
            if (!containsLink(data.getLinks(), start)) data.getLinks().add(0, start);
        }
        String endToken = annot.getAttributeValue("endid");
        if (endToken != null) {
            AnnotationLink end = musicLink(endToken, ids, msm, mpm);
            if (!containsLink(data.getLinks(), end)) data.getLinks().add(end);
        }

        readDates(annot, data);
        return data;
    }

    private static void readMeasures(Element annot, AnnotationData data) {
        String from = annot.getAttributeValue("tstamp");
        String to = annot.getAttributeValue("tstamp2");
        if (from == null) {
            if (to != null) {
                throw new IllegalArgumentException("tstamp2 without tstamp in annot " + data.getId());
            }
            return;
        }
        data.setMeasureFrom(measureFromAttribute(from));
        if (to != null) {
            data.setMeasureTo(measureFromAttribute(to));
        }
    }

    private static String measureFromAttribute(String value) {
        Matcher matcher = MEASURE.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid tstamp '" + value + "', expected '{measure}m+0'");
        }
        return matcher.group(1);
    }

    private static void readDates(Element annot, AnnotationData data) {
        Long created = null;
        Long updated = null;
        Elements dates = annot.getChildElements("date", MEI_NS);
        for (int i = 0; i < dates.size(); ++i) {
            Element date = dates.get(i);
            String type = date.getAttributeValue("type");
            if (!"created".equals(type) && !"updated".equals(type)) {
                continue;
            }
            String iso = date.getAttributeValue("isodate");
            if (iso == null) {
                throw new IllegalArgumentException("date without isodate in annot " + data.getId());
            }
            try {
                long millis = Instant.parse(iso).toEpochMilli();
                if ("created".equals(type)) created = millis;
                else updated = millis;
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("invalid isodate '" + iso + "'", ex);
            }
        }
        if (created != null) data.setCreatedAt(created);
        if (updated != null) data.setUpdatedAt(updated);
        else if (created != null) data.setUpdatedAt(created);
    }

    /**
     * Interpret a reference of a plist; the meaning of a local reference is defined by the element it points to.
     */
    private static AnnotationLink tokenToLink(String token, Map<String, Element> ids, Map<Element, Element> zoneSurfaces,
                                              Predicate<String> msm, Predicate<String> mpm) {
        if (!token.startsWith("#")) {
            return new AnnotationLink(AnnotationLink.LinkType.SVG, svgUriToTarget(token));
        }
        String id = fragmentOf(token);
        Element element = ids.get(id);
        if (element == null) {
            return new AnnotationLink(musicType(id, msm, mpm), id);
        }
        if (msm.test(id) || mpm.test(id)) {
            throw new IllegalArgumentException("ambiguous reference " + token + ": the id exists in the annotations and in the MSM/MPM");
        }
        switch (element.getLocalName()) {
            case "when":
                return withSource(new AnnotationLink(AnnotationLink.LinkType.TIMESTAMP, whenToTarget(element)), element);
            case "clip":
                return withSource(new AnnotationLink(AnnotationLink.LinkType.TIME_RANGE, clipToTarget(element)), element);
            case "zone":
                return zoneToAreaLink(element, zoneSurfaces.get(element));
            default:
                throw new IllegalArgumentException("unknown local resource " + token + " (" + element.getLocalName() + ")");
        }
    }

    private static AnnotationLink withSource(AnnotationLink link, Element timeElement) {
        Node parent = timeElement.getParent();
        if ((parent instanceof Element) && "recording".equals(((Element) parent).getLocalName())) {
            Element avFile = ((Element) parent).getFirstChildElement("avFile", MEI_NS);
            if (avFile != null) {
                link.setSource(avFile.getAttributeValue("target"));
            }
        }
        return link;
    }

    private static AnnotationLink musicLink(String token, Map<String, Element> ids, Predicate<String> msm, Predicate<String> mpm) {
        if (!token.startsWith("#")) {
            throw new IllegalArgumentException("startid/endid must be a local reference: " + token);
        }
        String id = fragmentOf(token);
        if (ids.containsKey(id)) {
            throw new IllegalArgumentException("startid/endid " + token + " does not reference a musical element");
        }
        return new AnnotationLink(musicType(id, msm, mpm), id);
    }

    /**
     * Decide whether an id belongs to the MSM or the MPM, using the documents only.
     */
    private static AnnotationLink.LinkType musicType(String id, Predicate<String> msm, Predicate<String> mpm) {
        boolean inMsm = msm.test(id);
        boolean inMpm = mpm.test(id);
        if (inMsm && inMpm) {
            throw new IllegalArgumentException("ambiguous reference #" + id + ": the id exists in the MSM and in the MPM");
        }
        if (inMsm) return AnnotationLink.LinkType.MSM;
        if (inMpm) return AnnotationLink.LinkType.MPM;
        throw new IllegalArgumentException("dangling reference #" + id);
    }

    private static boolean containsLink(List<AnnotationLink> links, AnnotationLink link) {
        for (AnnotationLink existing : links) {
            if ((existing.getType() == link.getType()) && existing.getTarget().equals(link.getTarget())) {
                return true;
            }
        }
        return false;
    }

    private static Element localElement(String token, Map<String, Element> ids) {
        if (!token.startsWith("#")) {
            throw new IllegalArgumentException("expected a local reference: " + token);
        }
        Element element = ids.get(fragmentOf(token));
        if (element == null) {
            throw new IllegalArgumentException("dangling reference " + token);
        }
        return element;
    }

    private static String whenToTarget(Element when) {
        if (!TIME_TYPE.equals(when.getAttributeValue("abstype"))) {
            throw new IllegalArgumentException("when " + getXmlId(when) + " must have abstype=\"" + TIME_TYPE + "\"");
        }
        String absolute = when.getAttributeValue("absolute");
        if (absolute == null) {
            throw new IllegalArgumentException("when " + getXmlId(when) + " without absolute");
        }
        return Double.toString(smilToMillis(absolute));
    }

    private static String clipToTarget(Element clip) {
        if (!TIME_TYPE.equals(clip.getAttributeValue("betype"))) {
            throw new IllegalArgumentException("clip " + getXmlId(clip) + " must have betype=\"" + TIME_TYPE + "\"");
        }
        String begin = clip.getAttributeValue("begin");
        String end = clip.getAttributeValue("end");
        if ((begin == null) || (end == null)) {
            throw new IllegalArgumentException("clip " + getXmlId(clip) + " needs begin and end");
        }
        double b = smilToMillis(begin);
        double e = smilToMillis(end);
        if (b >= e) {
            throw new IllegalArgumentException("invalid clip " + getXmlId(clip) + ", begin must be less than end");
        }
        return Double.toString(b) + "," + Double.toString(e);
    }

    private static AnnotationLink zoneToAreaLink(Element zone, Element surface) {
        String[] types = splitList(zone.getAttributeValue("type")).toArray(new String[0]);
        if ((types.length != 1) || !ZONE_REFERENCE.equals(types[0])) {
            throw new IllegalArgumentException("zone " + getXmlId(zone) + " is not an " + ZONE_REFERENCE);
        }
        String surfaceType = surfaceType(zone, surface);
        double[] rect = readRect(zone);
        String coordinates = rect[0] + "," + rect[1] + "," + rect[2] + "," + rect[3];
        if (SURFACE_SCORE_PAGE.equals(surfaceType)) {
            String page = pageOf(surface);
            if (page.isEmpty()) {
                throw new IllegalArgumentException("score page surface of zone " + getXmlId(zone) + " has no graphic target");
            }
            return new AnnotationLink(AnnotationLink.LinkType.SCORE_AREA, coordinates + "," + page);
        }
        if (SURFACE_AUDIO.equals(surfaceType)) {
            return new AnnotationLink(AnnotationLink.LinkType.SPECTROGRAM_AREA, coordinates);
        }
        throw new IllegalArgumentException("zone " + getXmlId(zone) + " cannot be an area reference in a " + surfaceType + " surface");
    }

    private static String surfaceType(Element zone, Element surface) {
        if (surface == null) {
            throw new IllegalArgumentException("zone " + getXmlId(zone) + " is not a direct child of a surface");
        }
        String type = surface.getAttributeValue("type");
        if (type == null) {
            throw new IllegalArgumentException("surface of zone " + getXmlId(zone) + " has no type");
        }
        return type;
    }

    private static String pageOf(Element surface) {
        Element graphic = surface.getFirstChildElement("graphic", MEI_NS);
        String target = (graphic == null) ? null : graphic.getAttributeValue("target");
        return (target == null) ? "" : target;
    }

    // ---------------------------------------------------------------- coordinates

    private static void writeRect(Element zone, double x, double y, double width, double height) {
        int ulx = toCoordinate(x);
        int uly = toCoordinate(y);
        int lrx = Math.max(ulx, toCoordinate(x + width));
        int lry = Math.max(uly, toCoordinate(y + height));
        zone.addAttribute(new Attribute("ulx", Integer.toString(ulx)));
        zone.addAttribute(new Attribute("uly", Integer.toString(uly)));
        zone.addAttribute(new Attribute("lrx", Integer.toString(lrx)));
        zone.addAttribute(new Attribute("lry", Integer.toString(lry)));
    }

    /**
     * @return x, y, width, height as relative values
     */
    private static double[] readRect(Element zone) {
        int ulx = readCoordinate(zone, "ulx");
        int uly = readCoordinate(zone, "uly");
        int lrx = readCoordinate(zone, "lrx");
        int lry = readCoordinate(zone, "lry");
        if ((lrx < ulx) || (lry < uly)) {
            throw new IllegalArgumentException("zone " + getXmlId(zone) + " has inverted corners");
        }
        return new double[]{fromCoordinate(ulx), fromCoordinate(uly), fromCoordinate(lrx - ulx), fromCoordinate(lry - uly)};
    }

    private static int toCoordinate(double relative) {
        if (Double.isNaN(relative) || Double.isInfinite(relative)) {
            throw new IllegalArgumentException("invalid relative coordinate " + relative);
        }
        return (int) Math.round(Math.max(0.0, Math.min(1.0, relative)) * COORDINATE_RANGE);
    }

    private static double fromCoordinate(int coordinate) {
        return coordinate / (double) COORDINATE_RANGE;
    }

    private static int readCoordinate(Element zone, String name) {
        String value = zone.getAttributeValue(name);
        if (value == null) {
            throw new IllegalArgumentException("zone " + getXmlId(zone) + " without " + name);
        }
        try {
            int coordinate = Integer.parseInt(value);
            if ((coordinate < 0) || (coordinate > COORDINATE_RANGE)) {
                throw new IllegalArgumentException("coordinate " + name + "=" + value + " of zone " + getXmlId(zone) + " is out of range");
            }
            return coordinate;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid coordinate " + name + "=" + value + " in zone " + getXmlId(zone), ex);
        }
    }

    // ---------------------------------------------------------------- time

    private static double parseMillis(String value) {
        double ms = parseDouble(value, "time");
        if (ms < 0.0) {
            throw new IllegalArgumentException("negative time " + value);
        }
        return ms;
    }

    private static String millisToSmil(double ms) {
        return BigDecimal.valueOf(ms).toPlainString() + "ms";
    }

    private static double smilToMillis(String value) {
        Matcher matcher = SMIL_MS.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid smil time '" + value + "', expected '{milliseconds}ms'");
        }
        return Double.parseDouble(matcher.group(1));
    }

    private static double parseDouble(String value, String what) {
        try {
            double d = Double.parseDouble(value.trim());
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("invalid " + what + " '" + value + "'");
            }
            return d;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid " + what + " '" + value + "'", ex);
        }
    }

    // ---------------------------------------------------------------- references and URIs

    /**
     * @return the part after the first '#' of a reference
     */
    private static String fragmentOf(String reference) {
        int index = reference.indexOf('#');
        String fragment = (index < 0) ? "" : reference.substring(index + 1);
        if (fragment.isEmpty()) {
            throw new IllegalArgumentException("reference without fragment: " + reference);
        }
        return fragment;
    }

    private static String svgTargetToUri(String target) {
        int index = target.indexOf('#');
        if ((index <= 0) || (index == target.length() - 1) || (target.indexOf('#', index + 1) >= 0)) {
            throw new IllegalArgumentException("invalid SVG URI '" + target + "', expected 'file.svg#id'");
        }
        String file = target.substring(0, index);
        String fragment = target.substring(index + 1);
        if (!file.toLowerCase().endsWith(".svg") || containsWhitespace(fragment)) {
            throw new IllegalArgumentException("invalid SVG URI '" + target + "', expected 'file.svg#id'");
        }
        try {
            return new URI(null, null, file, fragment).toASCIIString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("invalid SVG URI '" + target + "'", ex);
        }
    }

    private static String svgUriToTarget(String reference) {
        try {
            URI uri = new URI(reference);
            String path = uri.getPath();
            String fragment = uri.getFragment();
            if ((uri.getScheme() != null) || (uri.getAuthority() != null) || (path == null) || path.isEmpty()
                    || !path.toLowerCase().endsWith(".svg") || (fragment == null) || fragment.isEmpty()) {
                throw new IllegalArgumentException("invalid SVG URI '" + reference + "', expected 'file.svg#id'");
            }
            return path + "#" + fragment;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("invalid SVG URI '" + reference + "'", ex);
        }
    }

    private static boolean containsWhitespace(String s) {
        for (int i = 0; i < s.length(); ++i) {
            if (Character.isWhitespace(s.charAt(i))) return true;
        }
        return false;
    }

    private static List<String> splitList(String value) {
        List<String> tokens = new ArrayList<>();
        if (value != null) {
            for (String token : value.trim().split("\\s+")) {
                if (!token.isEmpty()) tokens.add(token);
            }
        }
        return tokens;
    }

    // ---------------------------------------------------------------- xml helpers

    private static Element mei(String name) {
        return new Element(name, MEI_NS);
    }

    private static String newId(String prefix, Set<String> usedIds) {
        String id;
        do {
            id = prefix + "-" + UUID.randomUUID();
        } while (!usedIds.add(id));
        return id;
    }

    private static void setXmlId(Element element, String id) {
        element.addAttribute(new Attribute("xml:id", XML_NS, id));
    }

    private static String getXmlId(Element element) {
        return element.getAttributeValue("id", XML_NS);
    }

    private static void requireXmlId(String id, String what) {
        if ((id == null) || !XML_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid " + what + " '" + id + "', it must be a valid xml:id");
        }
    }

    private static String attributeOrEmpty(Element element, String name) {
        String value = element.getAttributeValue(name);
        return (value == null) ? "" : value;
    }
}
