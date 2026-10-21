package mpmToolbox.projectData.annotations;


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
}

