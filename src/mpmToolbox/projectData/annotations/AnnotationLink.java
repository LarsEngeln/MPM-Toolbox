package mpmToolbox.projectData.annotations;

import nu.xom.Attribute;
import nu.xom.Element;

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
        /** an SVG element, target holds its id */
        SVG("svg"),
        /** a point in time, target holds the timestamp */
        TIMESTAMP("timestamp"),
        /** a rectangle on a score page, target holds "x,y,width,height" */
        SCORE_AREA("scoreArea"),
        /** a rectangle in the spectrogram, target holds "x,y,width,height" */
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
    private boolean resolved;

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

    public Element toXml() {
        Element e = new Element("link");
        e.addAttribute(new Attribute("type", this.type.getXmlValue()));
        e.addAttribute(new Attribute("target", this.target));
        e.addAttribute(new Attribute("resolved", Boolean.toString(this.resolved)));
        return e;
    }

    public static AnnotationLink fromXml(Element element) {
        String typeValue = element.getAttributeValue("type");
        String target = element.getAttributeValue("target");
        String resolvedValue = element.getAttributeValue("resolved");
        boolean resolved = (resolvedValue == null) || Boolean.parseBoolean(resolvedValue);
        return new AnnotationLink(LinkType.fromXmlValue(typeValue), target, resolved);
    }
}

