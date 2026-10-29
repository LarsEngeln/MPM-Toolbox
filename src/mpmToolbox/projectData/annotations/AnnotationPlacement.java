package mpmToolbox.projectData.annotations;


/**
 * One visual placement of an annotation in a specific main tab.
 * @author Lars Engeln
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

        public String toXmlValue() {
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

    private TabType tabType;

    /** Generic geometry fields. */
    private double x;
    private double y;
    private double width;
    private double height;
    private String source;
    private String pane = "";       // identifies the pane within the tab (e.g. "spectrogram")

    public AnnotationPlacement(TabType tabType, double x, double y, double width, double height, String source) {
        this.tabType = (tabType == null) ? TabType.SCORE : tabType;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.source = (source == null) ? "" : source;
    }

    public TabType getTabType() {
        return this.tabType;
    }

    public void setTabType(TabType tabType) {
        if (tabType != null) {
            this.tabType = tabType;
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

    public String getSource() {
        return this.source;
    }

    public void setSource(String source) {
        this.source = (source == null) ? "" : source;
    }

    public String getPane() {
        return this.pane;
    }

    public void setPane(String pane) {
        this.pane = (pane == null) ? "" : pane;
    }
}

