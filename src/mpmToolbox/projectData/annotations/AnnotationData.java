package mpmToolbox.projectData.annotations;

import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Elements;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Project-wide annotation entity with links and visual placements.
 */
public class AnnotationData {
    private String id;
    private String text;
    private long createdAt;
    private long updatedAt;
    private final ArrayList<AnnotationLink> links = new ArrayList<>();
    private final ArrayList<AnnotationPlacement> placements = new ArrayList<>();

    public AnnotationData() {
        this("Annotation");
    }

    public AnnotationData(String text) {
        this.id = "annotation-" + UUID.randomUUID();
        this.text = (text == null) ? "" : text;
        long now = Instant.now().toEpochMilli();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        if ((id != null) && !id.trim().isEmpty()) {
            this.id = id;
        }
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = (text == null) ? "" : text;
        this.touch();
    }

    public long getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return this.updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public ArrayList<AnnotationLink> getLinks() {
        return this.links;
    }

    public ArrayList<AnnotationPlacement> getPlacements() {
        return this.placements;
    }

    public void addLink(AnnotationLink link) {
        if (link == null) {
            return;
        }
        this.links.add(link);
        this.touch();
    }

    public void removeLink(int index) {
        if ((index < 0) || (index >= this.links.size())) {
            return;
        }
        this.links.remove(index);
        this.touch();
    }

    public void addPlacement(AnnotationPlacement placement) {
        if (placement == null) {
            return;
        }
        this.placements.add(placement);
        this.touch();
    }

    public void removePlacement(int index) {
        if ((index < 0) || (index >= this.placements.size())) {
            return;
        }
        this.placements.remove(index);
        this.touch();
    }

    public AnnotationPlacement findNearestPlacement(AnnotationPlacement.TabType tabType, double x, double y, String pagePath, double maxDistSquared) {
        AnnotationPlacement nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (AnnotationPlacement placement : this.placements) {
            if (placement.getTabType() != tabType) {
                continue;
            }
            if (tabType == AnnotationPlacement.TabType.SCORE) {
                String placementPage = placement.getPagePath();
                String sought = (pagePath == null) ? "" : pagePath;
                if (!placementPage.equals(sought)) {
                    continue;
                }
            }
            double dist;
            switch (placement.getPlacementType()) {
                case RECT: {
                    double width = Math.max(1.0, placement.getWidth());
                    double height = Math.max(1.0, placement.getHeight());
                    double dx = 0.0;
                    if (x < placement.getX()) dx = placement.getX() - x;
                    else if (x > (placement.getX() + width)) dx = x - (placement.getX() + width);
                    double dy = 0.0;
                    if (y < placement.getY()) dy = placement.getY() - y;
                    else if (y > (placement.getY() + height)) dy = y - (placement.getY() + height);
                    dist = (dx * dx) + (dy * dy);
                    break;
                }
                case POINT:
                default: {
                    double dx = placement.getX() - x;
                    double dy = placement.getY() - y;
                    dist = (dx * dx) + (dy * dy);
                }
            }
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = placement;
            }
        }
        if ((nearest == null) || (nearestDist > maxDistSquared)) {
            return null;
        }
        return nearest;
    }

    public void touch() {
        this.updatedAt = Instant.now().toEpochMilli();
    }

    public String getDisplayText() {
        if ((this.text == null) || this.text.trim().isEmpty()) {
            return "(Annotation)";
        }
        return this.text;
    }

    public Element toXml() {
        Element e = new Element("annotation");
        e.addAttribute(new Attribute("id", this.id));
        e.addAttribute(new Attribute("createdAt", Long.toString(this.createdAt)));
        e.addAttribute(new Attribute("updatedAt", Long.toString(this.updatedAt)));
        if (!this.text.isEmpty()) {
            e.addAttribute(new Attribute("text", this.text));
        }

        if (!this.links.isEmpty()) {
            Element linksElement = new Element("links");
            for (AnnotationLink link : this.links) {
                linksElement.appendChild(link.toXml());
            }
            e.appendChild(linksElement);
        }

        if (!this.placements.isEmpty()) {
            Element placementsElement = new Element("placements");
            for (AnnotationPlacement placement : this.placements) {
                placementsElement.appendChild(placement.toXml());
            }
            e.appendChild(placementsElement);
        }

        return e;
    }

    public static AnnotationData fromXml(Element element) {
        AnnotationData data = new AnnotationData(element.getAttributeValue("text"));
        data.setId(element.getAttributeValue("id"));
        data.setCreatedAt(parseLong(element.getAttributeValue("createdAt"), data.getCreatedAt()));
        data.setUpdatedAt(parseLong(element.getAttributeValue("updatedAt"), data.getUpdatedAt()));

        Element linksElement = element.getFirstChildElement("links");
        if (linksElement != null) {
            Elements linkChildren = linksElement.getChildElements("link");
            for (int i = 0; i < linkChildren.size(); ++i) {
                Element child = linkChildren.get(i);
                data.getLinks().add(AnnotationLink.fromXml(child));
            }
        }

        Element placementsElement = element.getFirstChildElement("placements");
        if (placementsElement != null) {
            Elements placementChildren = placementsElement.getChildElements("placement");
            for (int i = 0; i < placementChildren.size(); ++i) {
                Element child = placementChildren.get(i);
                data.getPlacements().add(AnnotationPlacement.fromXml(child));
            }
        }

        return data;
    }

    private static long parseLong(String raw, long fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    @Override
    public String toString() {
        return this.text.isEmpty() ? this.id : this.text;
    }
}
