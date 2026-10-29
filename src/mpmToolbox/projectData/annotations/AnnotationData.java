package mpmToolbox.projectData.annotations;


import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Project-wide annotation entity with links and visual placements.
 * @author Lars Engeln
 */
public class AnnotationData {
    private String id;
    private String title;
    private String part = "";
    private String category = "";   // not used by the GUI yet
    private String text;
    private String measureFrom = "";
    private String measureTo = "";
    private long createdAt;
    private long updatedAt;
    private final ArrayList<AnnotationLink> links = new ArrayList<>();
    private final ArrayList<AnnotationPlacement> placements = new ArrayList<>();

    public AnnotationData() {
        this("Annotation");
    }

    public AnnotationData(String text) {
        this.id = "annotation-" + UUID.randomUUID();
        this.title = "Annotation";
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

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = (title == null) ? "" : title;
        this.touch();
    }

    public String getPart() {
        return this.part;
    }

    public void setPart(String part) {
        this.part = (part == null) ? "" : part;
        this.touch();
    }

    public String getCategory() {
        return this.category;
    }

    public void setCategory(String category) {
        this.category = (category == null) ? "" : category;
        this.touch();
    }

    public String getMeasureFrom() {
        return this.measureFrom;
    }

    public void setMeasureFrom(String measureFrom) {
        this.measureFrom = normalizeMeasure(measureFrom);
        if (this.measureFrom.isEmpty()) {
            this.measureTo = "";
        }
        this.touch();
    }

    public String getMeasureTo() {
        return this.measureTo;
    }

    public void setMeasureTo(String measureTo) {
        this.measureTo = this.measureFrom.isEmpty() ? "" : normalizeMeasure(measureTo);
        this.touch();
    }

    public String getDisplayTitle() {
        String displayTitle = (this.title == null) ? "" : this.title;
        StringBuilder details = new StringBuilder();
        if ((this.part != null) && !this.part.isEmpty()) {
            details.append(this.part);
        }
        if (!this.measureFrom.isEmpty()) {
            if (details.length() > 0) {
                details.append(": ");
            }
            details.append(this.measureFrom);
        }
        if (!this.measureTo.isEmpty()) {
            details.append('-').append(this.measureTo);
        }
        if (details.length() == 0) {
            return displayTitle;
        }
        return displayTitle + " [" + details + "]";
    }

    private static String normalizeMeasure(String measure) {
        String value = (measure == null) ? "" : measure.trim();
        return value.matches("-?[0-9]+|\\?") ? value : "";
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
        for (AnnotationLink existing : this.links) {
            if ((existing.getType() == link.getType()) && java.util.Objects.equals(existing.getTarget(), link.getTarget())) {
                return;
            }
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
                String placementPage = placement.getSource();
                String sought = (pagePath == null) ? "" : pagePath;
                if (!placementPage.equals(sought)) {
                    continue;
                }
            }
            double dist;

            double width = Math.max(1.0, placement.getWidth());
            double height = Math.max(1.0, placement.getHeight());
            double dx = 0.0;
            if (x < placement.getX()) dx = placement.getX() - x;
            else if (x > (placement.getX() + width)) dx = x - (placement.getX() + width);
            double dy = 0.0;
            if (y < placement.getY()) dy = placement.getY() - y;
            else if (y > (placement.getY() + height)) dy = y - (placement.getY() + height);
            dist = (dx * dx) + (dy * dy);

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

    @Override
    public String toString() {
        return this.text.isEmpty() ? this.id : this.text;
    }
}
