package mpmToolbox.projectData.annotations;

import nu.xom.Attribute;
import nu.xom.Element;

/**
 * One visual placement of an annotation in a specific main tab.
 */
public class AnnotationPlacement {
    public enum TabType {
        SCORE("score"),
        AUDIO("audio"),
        ANALYTICS("analytics");

        private final String xmlValue;

        TabType(String xmlValue) {
            this.xmlValue = xmlValue;
        }

        public String getXmlValue() {
            return this.xmlValue;
        }

        public static TabType fromXmlValue(String value) {
            if (value == null) {
                return SCORE;
            }
            for (TabType type : values()) {
                if (type.xmlValue.equals(value)) {
                    return type;
                }
            }
            return SCORE;
        }
    }

    public enum PlacementType {
        POINT("point"),
        RECT("rect"),
        RANGE("range");

        private final String xmlValue;

        PlacementType(String xmlValue) {
            this.xmlValue = xmlValue;
        }

        public String getXmlValue() {
            return this.xmlValue;
        }

        public static PlacementType fromXmlValue(String value) {
            if (value == null) {
                return POINT;
            }
            for (PlacementType kind : values()) {
                if (kind.xmlValue.equals(value)) {
                    return kind;
                }
            }
            return POINT;
        }
    }

    private TabType tabType;
    private PlacementType placementType;

    /** Generic geometry fields. */
    private double x;
    private double y;
    private double width;
    private double height;
    private double start;
    private double end;
    private String pagePath;

    public AnnotationPlacement(TabType tabType, PlacementType placementType) {
        this.tabType = (tabType == null) ? TabType.SCORE : tabType;
        this.placementType = (placementType == null) ? PlacementType.POINT : placementType;
    }

    public static AnnotationPlacement point(TabType tabType, double x, double y, String pagePath) {
        AnnotationPlacement placement = new AnnotationPlacement(tabType, PlacementType.POINT);
        placement.x = x;
        placement.y = y;
        placement.pagePath = (pagePath == null) ? "" : pagePath;
        return placement;
    }

    public static AnnotationPlacement rect(TabType tabType, double x, double y, double width, double height, String pagePath) {
        AnnotationPlacement placement = new AnnotationPlacement(tabType, PlacementType.RECT);
        placement.x = x;
        placement.y = y;
        placement.width = width;
        placement.height = height;
        placement.pagePath = (pagePath == null) ? "" : pagePath;
        return placement;
    }

    public static AnnotationPlacement range(TabType tabType, double start, double end) {
        AnnotationPlacement placement = new AnnotationPlacement(tabType, PlacementType.RANGE);
        placement.start = start;
        placement.end = end;
        placement.pagePath = "";
        return placement;
    }

    public TabType getTabType() {
        return this.tabType;
    }

    public void setTabType(TabType tabType) {
        if (tabType != null) {
            this.tabType = tabType;
        }
    }

    public PlacementType getPlacementType() {
        return this.placementType;
    }

    public void setPlacementType(PlacementType placementType) {
        if (placementType != null) {
            this.placementType = placementType;
        }
    }

    public double getX() {
        return this.x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return this.y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return this.width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return this.height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public double getStart() {
        return this.start;
    }

    public void setStart(double start) {
        this.start = start;
    }

    public double getEnd() {
        return this.end;
    }

    public void setEnd(double end) {
        this.end = end;
    }

    public String getPagePath() {
        return this.pagePath;
    }

    public void setPagePath(String pagePath) {
        this.pagePath = (pagePath == null) ? "" : pagePath;
    }

    public Element toXml() {
        Element e = new Element("placement");
        e.addAttribute(new Attribute("tab", this.tabType.getXmlValue()));
        e.addAttribute(new Attribute("kind", this.placementType.getXmlValue()));
        e.addAttribute(new Attribute("x", Double.toString(this.x)));
        e.addAttribute(new Attribute("y", Double.toString(this.y)));
        e.addAttribute(new Attribute("width", Double.toString(this.width)));
        e.addAttribute(new Attribute("height", Double.toString(this.height)));
        e.addAttribute(new Attribute("start", Double.toString(this.start)));
        e.addAttribute(new Attribute("end", Double.toString(this.end)));
        if ((this.pagePath != null) && !this.pagePath.isEmpty()) {
            e.addAttribute(new Attribute("page", this.pagePath));
        }
        return e;
    }

    public static AnnotationPlacement fromXml(Element element) {
        AnnotationPlacement placement = new AnnotationPlacement(
                TabType.fromXmlValue(element.getAttributeValue("tab")),
                PlacementType.fromXmlValue(element.getAttributeValue("kind")));
        placement.setX(parseDouble(element.getAttributeValue("x"), 0.0));
        placement.setY(parseDouble(element.getAttributeValue("y"), 0.0));
        placement.setWidth(parseDouble(element.getAttributeValue("width"), 0.0));
        placement.setHeight(parseDouble(element.getAttributeValue("height"), 0.0));
        placement.setStart(parseDouble(element.getAttributeValue("start"), 0.0));
        placement.setEnd(parseDouble(element.getAttributeValue("end"), 0.0));
        placement.setPagePath(element.getAttributeValue("page"));
        return placement;
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}

