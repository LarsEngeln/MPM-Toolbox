package mpmToolbox.projectData;

import com.kitfox.svg.RenderableElement;
import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGElement;
import com.kitfox.svg.SVGException;
import com.kitfox.svg.SVGUniverse;
import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.ParsingException;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data model for an SVG file used in MPM Toolbox.
 * Holds both the nu.xom XML representation (for tree display) and
 * the svg-salamander SVGDiagram (for rendering as overlay).
 *
 * @author Lars Engeln
 */
public class SvgData {

    private final File file;
    private final Element xmlRoot;      // nu.xom root element for tree display
    private final SVGDiagram diagram;   // svg-salamander diagram for rendering
    private Element highlightedElement = null;  // the currently highlighted XOM element (tree ↔ score sync)
    private Element hoveredElement = null;       // the currently hovered XOM element (hover in svg tree → score glow)

    /**
     * Constructor – parses the given SVG file.
     *
     * @param file the SVG file to load
     * @throws IOException      on I/O errors
     * @throws ParsingException if the SVG is not well-formed XML
     */
    public SvgData(File file) throws IOException, ParsingException {
        this.file = file;

        // parse XML via nu.xom for the tree view
        Builder builder = new Builder();
        Document doc = builder.build(file);
        this.xmlRoot = doc.getRootElement();

        // ensure every element has an id so that svg-salamander can look it up
        ensureIds(this.xmlRoot);

        // parse via svg-salamander for rendering – use the ID-enriched XML so that
        // every element is addressable via its (possibly newly assigned) id.
        SVGUniverse universe = new SVGUniverse();
        String enrichedXml = doc.toXML();
        URI svgUri = universe.loadSVG(new java.io.StringReader(enrichedXml), file.getName());
        this.diagram = universe.getDiagram(svgUri);
        if (this.diagram != null) {
            this.diagram.setIgnoringClipHeuristic(true);
        }
    }

    /**
     * The SVG source file.
     * @return file
     */
    public File getFile() {
        return this.file;
    }

    /**
     * The nu.xom root element of the SVG document (for tree display).
     * @return root element
     */
    public Element getXmlRoot() {
        return this.xmlRoot;
    }

    /**
     * The svg-salamander diagram (for rendering as overlay).
     * @return diagram, may be {@code null} if parsing failed
     */
    public SVGDiagram getDiagram() {
        return this.diagram;
    }

    /**
     * Returns the display name (file name without path).
     * @return name
     */
    public String getName() {
        return this.file.getName();
    }

    /**
     * Render this SVG scaled to the given target width and height using Graphics2D.
     * The SVG is scaled uniformly to fit within the target rectangle.
     *
     * @param g2     the Graphics2D context (already transformed for the score coordinate system)
     * @param targetWidth  the target width in pixels
     * @param targetHeight the target height in pixels
     */
    public void render(Graphics2D g2, int targetWidth, int targetHeight) {
        if (this.diagram == null)
            return;

        float svgW = this.diagram.getWidth();
        float svgH = this.diagram.getHeight();
        if (svgW <= 0 || svgH <= 0)
            return;

        double scaleX = targetWidth / svgW;
        double scaleY = targetHeight / svgH;

        Graphics2D g2Copy = (Graphics2D) g2.create();
        try {
            g2Copy.scale(scaleX, scaleY);
            this.diagram.render(g2Copy);
        } catch (SVGException e) {
            e.printStackTrace();
        } finally {
            g2Copy.dispose();
        }
    }

    /**
     * Get the currently highlighted XOM element (used for tree ↔ score synchronisation).
     */
    public Element getHighlightedElement() {
        return this.highlightedElement;
    }

    /**
     * Set the currently highlighted XOM element.
     * Pass {@code null} to clear the highlight.
     */
    public void setHighlightedElement(Element element) {
        this.highlightedElement = element;
    }

    /**
     * Get the currently hovered XOM element (mouse hovers over its tree node).
     */
    public Element getHoveredElement() {
        return this.hoveredElement;
    }

    /**
     * Set the currently hovered XOM element.
     * Pass {@code null} to clear the hover.
     */
    public void setHoveredElement(Element element) {
        this.hoveredElement = element;
    }

    /**
     * Returns the bounding rectangle of the SVG element with the given {@code id},
     * scaled to image-pixel coordinates (using {@code imageW}/{@code imageH}).
     * Returns {@code null} if the element cannot be found or has no bounding box.
     */
    public Rectangle2D getBoundsInImageSpace(String id, int imageW, int imageH) {
        if (this.diagram == null || id == null || id.isEmpty())
            return null;
        float svgW = this.diagram.getWidth();
        float svgH = this.diagram.getHeight();
        if (svgW <= 0 || svgH <= 0)
            return null;
        SVGElement svgEl = this.diagram.getElement(id);
        if (svgEl == null || !(svgEl instanceof RenderableElement))
            return null;
        try {
            Rectangle2D bounds = ((RenderableElement) svgEl).getBoundingBox();
            if (bounds == null)
                return null;
            double scaleX = imageW / svgW;
            double scaleY = imageH / svgH;
            return new Rectangle2D.Double(
                    bounds.getX()      * scaleX,
                    bounds.getY()      * scaleY,
                    bounds.getWidth()  * scaleX,
                    bounds.getHeight() * scaleY);
        } catch (SVGException e) {
            return null;
        }
    }

    /**
     * Returns the bounding rectangle for a XOM {@link Element}, scaled to image-pixel
     * coordinates. If the element has no {@code id} attribute (or its id is not known
     * to svg-salamander), the method walks up the XOM parent chain until it finds an
     * ancestor with a usable bounding box.
     *
     * @param element the XOM element to look up
     * @param imageW  total image width in pixels
     * @param imageH  total image height in pixels
     * @return bounds in image-pixel space, or {@code null} if nothing could be resolved
     */
    public Rectangle2D getBoundsInImageSpace(nu.xom.Node element, int imageW, int imageH) {
        if (this.diagram == null || imageW <= 0 || imageH <= 0)
            return null;
        float svgW = this.diagram.getWidth();
        float svgH = this.diagram.getHeight();
        if (svgW <= 0 || svgH <= 0)
            return null;

        // walk up the XOM tree until we find a node whose id svg-salamander knows
        nu.xom.Node current = element;
        while (current instanceof Element) {
            String id = ((Element) current).getAttributeValue("id");
            if (id != null && !id.isEmpty()) {
                SVGElement svgEl = this.diagram.getElement(id);
                if (svgEl instanceof RenderableElement) {
                    try {
                        Rectangle2D bounds = ((RenderableElement) svgEl).getBoundingBox();
                        if (bounds != null && bounds.getWidth() > 0 && bounds.getHeight() > 0) {
                            double scaleX = imageW / svgW;
                            double scaleY = imageH / svgH;
                            return new Rectangle2D.Double(
                                    bounds.getX()      * scaleX,
                                    bounds.getY()      * scaleY,
                                    bounds.getWidth()  * scaleX,
                                    bounds.getHeight() * scaleY);
                        }
                    } catch (SVGException e) {
                        // try parent
                    }
                }
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Render a hotpink stroke highlight bounding box for the currently highlighted element.
     * If the element is a {@code <g>} group, all leaf descendants are included.
     * Builds the cumulative SVG transform chain from the XOM ancestor tree so that
     * elements inside nested {@code <g transform="...">} groups are positioned correctly.
     *
     * @param g2           the Graphics2D context (already transformed to image space)
     * @param targetWidth  the target width in pixels (same as passed to render())
     * @param targetHeight the target height in pixels (same as passed to render())
     */
    public void renderHighlight(Graphics2D g2, int targetWidth, int targetHeight) {
        if (this.diagram == null || this.highlightedElement == null)
            return;

        float svgW = this.diagram.getWidth();
        float svgH = this.diagram.getHeight();
        if (svgW <= 0 || svgH <= 0)
            return;

        // Collect all leaf XOM elements (traverses into <g> groups)
        List<Element> leaves = new ArrayList<>();
        collectLeafElements(this.highlightedElement, leaves);
        if (leaves.isEmpty())
            return;

        // Build cumulative AffineTransform from XOM ancestor chain (root → element parent).
        // This corrects the position when leaf elements are inside <g transform="..."> groups.
        java.awt.geom.AffineTransform ancestorTransform = buildAncestorTransform(this.highlightedElement);

        // Use a small temp image for pixel-scanning (performance: avoid full-res scan)
        int tempW = Math.min(targetWidth,  600);
        int tempH = Math.min(targetHeight, 800);
        double tempScaleX = (double) tempW / svgW;
        double tempScaleY = (double) tempH / svgH;

        int overallMinX = tempW, overallMinY = tempH, overallMaxX = -1, overallMaxY = -1;

        for (Element el : leaves) {
            String id = el.getAttributeValue("id");
            if (id == null || id.isEmpty())
                continue;
            SVGElement svgEl = this.diagram.getElement(id);
            if (!(svgEl instanceof RenderableElement))
                continue;

            // --- fast path: try getBoundingBox() first (SVG user coordinates) ---
            Rectangle2D svgBounds = null;
            try {
                svgBounds = ((RenderableElement) svgEl).getBoundingBox();
            } catch (SVGException ignored) { }

            if (svgBounds != null && svgBounds.getWidth() > 0 && svgBounds.getHeight() > 0) {
                // Transform SVG bounding box through the ancestor chain → SVG root coords
                java.awt.geom.Point2D tl = ancestorTransform.transform(
                        new java.awt.geom.Point2D.Double(svgBounds.getX(), svgBounds.getY()), null);
                java.awt.geom.Point2D br = ancestorTransform.transform(
                        new java.awt.geom.Point2D.Double(svgBounds.getMaxX(), svgBounds.getMaxY()), null);
                // Convert SVG root coords → temp image coords
                int px1 = (int)(tl.getX() * tempScaleX);
                int py1 = (int)(tl.getY() * tempScaleY);
                int px2 = (int)(br.getX() * tempScaleX);
                int py2 = (int)(br.getY() * tempScaleY);
                overallMinX = Math.min(overallMinX, Math.min(px1, px2));
                overallMinY = Math.min(overallMinY, Math.min(py1, py2));
                overallMaxX = Math.max(overallMaxX, Math.max(px1, px2));
                overallMaxY = Math.max(overallMaxY, Math.max(py1, py2));
                continue;
            }

            // --- fallback: render element to temp image and scan pixels ---
            java.awt.image.BufferedImage tempImg = new java.awt.image.BufferedImage(
                    tempW, tempH, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D tg = tempImg.createGraphics();
            tg.scale(tempScaleX, tempScaleY);
            tg.transform(ancestorTransform);
            try {
                ((RenderableElement) svgEl).render(tg);
            } catch (SVGException e) {
                tg.dispose();
                continue;
            }
            tg.dispose();

            for (int y = 0; y < tempH; y++) {
                for (int x = 0; x < tempW; x++) {
                    if ((tempImg.getRGB(x, y) & 0xFF000000) != 0) {
                        if (x < overallMinX) overallMinX = x;
                        if (y < overallMinY) overallMinY = y;
                        if (x > overallMaxX) overallMaxX = x;
                        if (y > overallMaxY) overallMaxY = y;
                    }
                }
            }
        }

        if (overallMaxX < overallMinX || overallMaxY < overallMinY)
            return; // nothing found

        // Scale found bounds from temp image space → target image space, then draw on g2.
        // g2 has affineTransform applied (pan/zoom), so drawing in image-pixel coords is correct.
        double scaleBackX = (double) targetWidth  / tempW;
        double scaleBackY = (double) targetHeight / tempH;
        int imgX = (int)(overallMinX * scaleBackX);
        int imgY = (int)(overallMinY * scaleBackY);
        int imgW = (int)((overallMaxX - overallMinX) * scaleBackX);
        int imgH = (int)((overallMaxY - overallMinY) * scaleBackY);

        Color prevColor = g2.getColor();
        Stroke prevStroke = g2.getStroke();
        g2.setColor(Color.decode("#FF69B4")); // hotpink
        g2.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawRect(imgX, imgY, imgW, imgH);
        g2.setColor(prevColor);
        g2.setStroke(prevStroke);
    }

    /**
     * Builds a cumulative {@link java.awt.geom.AffineTransform} by walking up the XOM ancestor
     * chain of {@code element} and composing all {@code transform} attributes found on ancestors.
     * The resulting transform converts from the element's local coordinate space to SVG root space.
     */
    private static java.awt.geom.AffineTransform buildAncestorTransform(Element element) {
        java.util.Deque<java.awt.geom.AffineTransform> stack = new java.util.ArrayDeque<>();
        for (nu.xom.Node n = element.getParent(); n instanceof Element; n = n.getParent()) {
            String t = ((Element) n).getAttributeValue("transform");
            if (t != null && !t.isEmpty()) {
                java.awt.geom.AffineTransform at = parseSvgTransform(t);
                if (at != null)
                    stack.push(at); // push = add to front; outermost ancestor ends up at back
            }
        }
        java.awt.geom.AffineTransform result = new java.awt.geom.AffineTransform();
        // apply in order outermost → innermost (descendingIterator gives back→front = outermost first)
        java.util.Iterator<java.awt.geom.AffineTransform> it = stack.descendingIterator();
        while (it.hasNext())
            result.concatenate(it.next());
        return result;
    }

    /**
     * Parses an SVG {@code transform} attribute string into an {@link java.awt.geom.AffineTransform}.
     * Supports {@code translate}, {@code scale}, {@code rotate}, {@code matrix}, {@code skewX}, {@code skewY}.
     * Returns {@code null} if the string contains no recognised transforms.
     */
    private static java.awt.geom.AffineTransform parseSvgTransform(String transform) {
        java.awt.geom.AffineTransform result = new java.awt.geom.AffineTransform();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(matrix|translate|scale|rotate|skewX|skewY)\\s*\\(([^)]*)\\)").matcher(transform);
        boolean found = false;
        while (m.find()) {
            found = true;
            String type = m.group(1);
            String[] parts = m.group(2).trim().split("[,\\s]+");
            try {
                double[] v = new double[parts.length];
                for (int i = 0; i < parts.length; i++) v[i] = Double.parseDouble(parts[i]);
                switch (type) {
                    case "translate": result.translate(v[0], v.length > 1 ? v[1] : 0); break;
                    case "scale":     result.scale(v[0], v.length > 1 ? v[1] : v[0]); break;
                    case "rotate":
                        if (v.length >= 3) result.rotate(Math.toRadians(v[0]), v[1], v[2]);
                        else               result.rotate(Math.toRadians(v[0]));
                        break;
                    case "matrix":
                        if (v.length >= 6) result.concatenate(
                                new java.awt.geom.AffineTransform(v[0], v[1], v[2], v[3], v[4], v[5]));
                        break;
                    case "skewX": result.shear(Math.tan(Math.toRadians(v[0])), 0); break;
                    case "skewY": result.shear(0, Math.tan(Math.toRadians(v[0]))); break;
                    default: break;
                }
            } catch (NumberFormatException ignored) { }
        }
        return found ? result : null;
    }

    /**
     * Recursively collects all non-group leaf elements from the subtree rooted at {@code element}.
     * {@code <g>} elements are traversed but not added themselves.
     */
    private static void collectLeafElements(Element element, List<Element> result) {
        if (element.getLocalName().equals("g")) {
            for (int i = 0; i < element.getChildCount(); i++) {
                nu.xom.Node child = element.getChild(i);
                if (child instanceof Element)
                    collectLeafElements((Element) child, result);
            }
        } else {
            result.add(element);
        }
    }

    /**
     * Hit-test at position ({@code imageX}, {@code imageY}) in image-pixel coordinates.
     * Returns the topmost XOM {@link Element} whose SVG counterpart covers that point,
     * or {@code null} if none is found.
     *
     * @param imageX  x position in image pixels
     * @param imageY  y position in image pixels
     * @param imageW  total image width in pixels
     * @param imageH  total image height in pixels
     */
    public Element pickElementAt(float imageX, float imageY, int imageW, int imageH) {
        if (this.diagram == null)
            return null;
        float svgW = this.diagram.getWidth();
        float svgH = this.diagram.getHeight();
        if (svgW <= 0 || svgH <= 0)
            return null;

        float svgX = imageX * svgW / imageW;
        float svgY = imageY * svgH / imageH;

        List<List<SVGElement>> retVec = new ArrayList<>();
        try {
            this.diagram.pick(new Point2D.Float(svgX, svgY), false, retVec);
        } catch (SVGException e) {
            return null;
        }

        // find the deepest element with an id that matches a XOM element
        for (int i = retVec.size() - 1; i >= 0; i--) {
            List<SVGElement> path = retVec.get(i);
            for (int j = path.size() - 1; j >= 0; j--) {
                SVGElement svgEl = path.get(j);
                String id = svgEl.getId();
                if (id != null && !id.isEmpty()) {
                    Element found = findElementById(this.xmlRoot, id);
                    if (found != null)
                        return found;
                }
            }
        }
        return null;
    }

    /**
     * Recursively ensures that every {@link Element} in the XOM subtree has an
     * {@code id} attribute. Elements that already carry an {@code id} are left
     * untouched; all others receive a freshly generated UUID via
     * {@link Helper#addUUID(Element)}.
     *
     * @param element the root of the subtree to process
     */
    private static void ensureIds(Element element) {
        String id = element.getAttributeValue("id");
        if (id == null || id.isEmpty()) {
            // plain "id" attribute (no namespace) – required by SVG and svg-salamander
            element.addAttribute(new Attribute("id", "mpm_" + UUID.randomUUID().toString()));
        }
        for (int i = 0; i < element.getChildCount(); i++) {
            nu.xom.Node child = element.getChild(i);
            if (child instanceof Element) {
                ensureIds((Element) child);
            }
        }
    }

    /**
     * Recursively search the XOM tree for an element with the given {@code id} attribute.
     */
    private static Element findElementById(Element root, String id) {
        if (id.equals(root.getAttributeValue("id")))
            return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChild(i) instanceof Element) {
                Element found = findElementById((Element) root.getChild(i), id);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    /**
     * Human-readable representation.
     * @return file name
     */
    @Override
    public String toString() {
        return this.getName();
    }
}


