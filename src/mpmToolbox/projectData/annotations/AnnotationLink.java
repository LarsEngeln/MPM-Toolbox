package mpmToolbox.projectData.annotations;


/**
 * One typed ID-based link attached to an annotation.
 */
public class AnnotationLink {
    /**
     * The type of a link. How a link is resolved and displayed is up to the tabs.
     */
    public enum LinkType {
        /** an MSM element, target holds its id */
        MSM("msm"),
        /** an MPM element, target holds its id */
        MPM("mpm"),
        /** an SVG element, target holds the URI "file.svg#id" */
        SVG("svg"),
        /** a point in time, target holds the timestamp in milliseconds */
        TIMESTAMP("timestamp"),
        /** a time range, target holds "begin,end" in milliseconds */
        TIME_RANGE("timeRange"),
        /** a rectangle on a score page, target holds the relative "x,y,width,height,pagePath" */
        SCORE_AREA("scoreArea"),
        /** a rectangle in the spectrogram, target holds the relative "x,y,width,height" */
        SPECTROGRAM_AREA("spectrogramArea");

        private final String xmlValue;

        LinkType(String xmlValue) {
            this.xmlValue = xmlValue;
        }

        public String getXmlValue() {
            return this.xmlValue;
        }

        public static LinkType fromXmlValue(String value) {
            for (LinkType type : values()) {
                if (type.xmlValue.equals(value)) {
                    return type;
                }
            }
            return TIMESTAMP;
        }
    }
    private LinkType type;
    private String target;
    private String source = "";
    private boolean resolved;

    /** the audio file a TIMESTAMP or TIME_RANGE refers to, empty if unknown */
    public String getSource() {
        return this.source;
    }

    public void setSource(String source) {
        this.source = (source == null) ? "" : source;
    }

    public AnnotationLink(LinkType type, String target) {
        this(type, target, true);
    }

    public AnnotationLink(LinkType type, String target, boolean resolved) {
        this.type = (type == null) ? LinkType.TIMESTAMP : type;
        this.target = (target == null) ? "" : target;
        this.resolved = resolved;
    }

    public LinkType getType() {
        return this.type;
    }

    public void setType(LinkType type) {
        if (type != null) {
            this.type = type;
        }
    }

    public String getTarget() {
        return this.target;
    }

    public void setTarget(String target) {
        this.target = (target == null) ? "" : target;
    }

    public boolean isResolved() {
        return this.resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }
}

