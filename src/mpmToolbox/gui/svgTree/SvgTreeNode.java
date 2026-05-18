package mpmToolbox.gui.svgTree;

import com.alee.api.annotations.NotNull;
import com.alee.api.ui.TextBridge;
import com.alee.extended.tree.WebExTree;
import com.alee.laf.tree.TreeNodeParameters;
import com.alee.laf.tree.UniqueNode;
import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Node;

import javax.swing.*;

/**
 * A tree node representing an SVG XML element or attribute.
 *
 * @author Lars Engeln
 */
public class SvgTreeNode extends UniqueNode<SvgTreeNode, Node>
        implements TextBridge<TreeNodeParameters<SvgTreeNode, WebExTree<SvgTreeNode>>> {

    @NotNull protected String name;
    @NotNull protected final SvgNodeType type;

    /**
     * Constructor for element nodes.
     * @param element the SVG XML element
     */
    public SvgTreeNode(@NotNull Element element) {
        super(element);
        this.type = SvgNodeType.fromElement(element);
        this.generateMyName();
    }

    /**
     * Constructor for attribute nodes.
     * @param attribute the SVG XML attribute
     */
    public SvgTreeNode(@NotNull Attribute attribute) {
        super(attribute);
        this.type = SvgNodeType.attribute;
        this.generateMyName();
    }

    // -------------------------------------------------------------------------
    // name generation – analogous to MpmTreeNode.generateMyName()
    // -------------------------------------------------------------------------

    private void generateMyName() {
        switch (this.type) {

            case svg: {
                Element e = (Element) this.getUserObject();
                String w = e.getAttributeValue("width");
                String h = e.getAttributeValue("height");
                this.name = "<html><font size=\"-2\" color=\"silver\">&lt;/&gt;</font></html>";
                break;
            }

            case g: {
                Element e = (Element) this.getUserObject();
                String id    = e.getAttributeValue("id");
                String label = e.getAttributeValue("label");          // inkscape:label lives in a namespace – fallback
                if (label == null) label = e.getAttributeValue("label", "http://www.inkscape.org/namespaces/inkscape");
                String display = (label != null) ? label : (id != null ? id : "");
                this.name = "<html><b>g</b>"
                        + (display.isEmpty() ? "" : "  <font color=\"silver\">" + display + "</font>")
                        + "</html>";
                break;
            }

            case path: {
                Element e = (Element) this.getUserObject();
                String id = e.getAttributeValue("id");
                this.name = "path" + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "");
                this.name = "<html>" + this.name + "</html>";
                break;
            }

            case rect: {
                Element e = (Element) this.getUserObject();
                String w = e.getAttributeValue("width");
                String h = e.getAttributeValue("height");
                String id = e.getAttributeValue("id");
                this.name = "<html>rect"
                        + ((w != null && h != null) ? " <font color=\"silver\">" + w + " × " + h + "</font>" : "")
                        + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "")
                        + "</html>";
                break;
            }

            case circle: {
                Element e = (Element) this.getUserObject();
                String r  = e.getAttributeValue("r");
                String id = e.getAttributeValue("id");
                this.name = "<html>circle"
                        + (r != null ? " <font color=\"silver\">r=" + r + "</font>" : "")
                        + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "")
                        + "</html>";
                break;
            }

            case ellipse: {
                Element e = (Element) this.getUserObject();
                String rx = e.getAttributeValue("rx");
                String ry = e.getAttributeValue("ry");
                String id = e.getAttributeValue("id");
                this.name = "<html>ellipse"
                        + ((rx != null && ry != null) ? " <font color=\"silver\">" + rx + " × " + ry + "</font>" : "")
                        + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "")
                        + "</html>";
                break;
            }

            case line: {
                Element e = (Element) this.getUserObject();
                String x1 = e.getAttributeValue("x1"), y1 = e.getAttributeValue("y1");
                String x2 = e.getAttributeValue("x2"), y2 = e.getAttributeValue("y2");
                this.name = "<html>line"
                        + ((x1 != null) ? " <font color=\"silver\">(" + x1 + "," + y1 + ")→(" + x2 + "," + y2 + ")</font>" : "")
                        + "</html>";
                break;
            }

            case polyline:
            case polygon: {
                Element e = (Element) this.getUserObject();
                String id = e.getAttributeValue("id");
                this.name = "<html>" + e.getLocalName()
                        + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "")
                        + "</html>";
                break;
            }

            case text: {
                Element e = (Element) this.getUserObject();
                String value = e.getValue().trim();
                if (value.length() > 40) value = value.substring(0, 40) + "…";
                this.name = "<html>text"
                        + (value.isEmpty() ? "" : "  <font color=\"silver\">\"" + value + "\"</font>")
                        + "</html>";
                break;
            }

            case tspan: {
                Element e = (Element) this.getUserObject();
                String value = e.getValue().trim();
                if (value.length() > 40) value = value.substring(0, 40) + "…";
                this.name = "<html>tspan"
                        + (value.isEmpty() ? "" : "  <font color=\"silver\">\"" + value + "\"</font>")
                        + "</html>";
                break;
            }

            case image: {
                Element e = (Element) this.getUserObject();
                // href / xlink:href
                String href = e.getAttributeValue("href");
                if (href == null) href = e.getAttributeValue("href", "http://www.w3.org/1999/xlink");
                String id = e.getAttributeValue("id");
                this.name = "<html>image"
                        + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "")
                        + (href != null ? "  <font color=\"silver\">" + href + "</font>" : "")
                        + "</html>";
                break;
            }

            case use: {
                Element e = (Element) this.getUserObject();
                String href = e.getAttributeValue("href");
                if (href == null) href = e.getAttributeValue("href", "http://www.w3.org/1999/xlink");
                this.name = "<html>use"
                        + (href != null ? "  <font color=\"silver\">" + href + "</font>" : "")
                        + "</html>";
                break;
            }

            case defs:
                this.name = "defs";
                break;

            case symbol: {
                Element e = (Element) this.getUserObject();
                String id = e.getAttributeValue("id");
                this.name = "symbol" + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "");
                this.name = "<html>" + this.name + "</html>";
                break;
            }

            case linearGradient:
            case radialGradient: {
                Element e = (Element) this.getUserObject();
                String id = e.getAttributeValue("id");
                this.name = "<html>" + e.getLocalName()
                        + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "")
                        + "</html>";
                break;
            }

            case stop: {
                Element e = (Element) this.getUserObject();
                String offset = e.getAttributeValue("offset");
                this.name = "stop" + (offset != null ? " " + offset : "");
                break;
            }

            case clipPath: {
                Element e = (Element) this.getUserObject();
                String id = e.getAttributeValue("id");
                this.name = "clipPath" + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "");
                this.name = "<html>" + this.name + "</html>";
                break;
            }

            case mask: {
                Element e = (Element) this.getUserObject();
                String id = e.getAttributeValue("id");
                this.name = "mask" + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "");
                this.name = "<html>" + this.name + "</html>";
                break;
            }

            case filter: {
                Element e = (Element) this.getUserObject();
                String id = e.getAttributeValue("id");
                this.name = "filter" + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "");
                this.name = "<html>" + this.name + "</html>";
                break;
            }

            case pattern: {
                Element e = (Element) this.getUserObject();
                String id = e.getAttributeValue("id");
                this.name = "pattern" + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "");
                this.name = "<html>" + this.name + "</html>";
                break;
            }

            case title: {
                Element e = (Element) this.getUserObject();
                String value = e.getValue().trim();
                this.name = "<html>title"
                        + (value.isEmpty() ? "" : "  <font color=\"silver\">\"" + value + "\"</font>")
                        + "</html>";
                break;
            }

            case desc: {
                Element e = (Element) this.getUserObject();
                String value = e.getValue().trim();
                if (value.length() > 60) value = value.substring(0, 60) + "…";
                this.name = "<html>desc"
                        + (value.isEmpty() ? "" : "  <font color=\"silver\">\"" + value + "\"</font>")
                        + "</html>";
                break;
            }

            case attribute: {
                Attribute a = (Attribute) this.getUserObject();
                String val = a.getValue();
                if (val.length() > 60) val = val.substring(0, 60) + "…";
                this.name = "<html><font color=\"silver\">@</font>  "
                        + a.getLocalName()
                        + " <font color=\"silver\">= " + val + "</font></html>";
                break;
            }

            case element:
            default: {
                // generic element: show local name + optional id
                Element e = (Element) this.getUserObject();
                String id = e.getAttributeValue("id");
                this.name = "<html>" + e.getLocalName()
                        + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "")
                        + "</html>";
                break;
            }
        }
    }

    /**
     * Re-generate the display name (call after data changes, then invoke the tree's updateNode()).
     */
    public void update() {
        this.generateMyName();
    }

    // -------------------------------------------------------------------------
    // TextBridge / UniqueNode overrides
    // -------------------------------------------------------------------------

    @Override
    public String getText(TreeNodeParameters<SvgTreeNode, WebExTree<SvgTreeNode>> parameters) {
        return this.name;
    }

    public Icon getNodeIcon(TreeNodeParameters<SvgTreeNode, WebExTree<SvgTreeNode>> parameters) {
        return null;
    }

    public SvgNodeType getType() {
        return this.type;
    }

    /**
     * Tooltip text showing the raw XML of this node.
     */
    public String getTooltipText() {
        Node obj = this.getUserObject();
        if (obj instanceof Attribute)
            return ((Attribute) obj).toXML();
        String s = ((Element) obj).toXML();
        int i = s.indexOf('>') + 1;
        if (i > 0)
            s = s.substring(0, i);
        return s;
    }

    @Override
    public String toString() {
        return this.name;
    }

    // -------------------------------------------------------------------------
    // Node-type enum
    // -------------------------------------------------------------------------

    public enum SvgNodeType {
        svg,
        g,
        path,
        rect,
        circle,
        ellipse,
        line,
        polyline,
        polygon,
        text,
        tspan,
        image,
        use,
        defs,
        symbol,
        linearGradient,
        radialGradient,
        stop,
        clipPath,
        mask,
        filter,
        pattern,
        title,
        desc,
        attribute,
        element;    // catch-all for unknown elements

        /**
         * Resolve a type from an SVG element's local name.
         */
        public static SvgNodeType fromElement(@NotNull Element e) {
            switch (e.getLocalName()) {
                case "svg":            return svg;
                case "g":              return g;
                case "path":           return path;
                case "rect":           return rect;
                case "circle":         return circle;
                case "ellipse":        return ellipse;
                case "line":           return line;
                case "polyline":       return polyline;
                case "polygon":        return polygon;
                case "text":           return text;
                case "tspan":          return tspan;
                case "image":          return image;
                case "use":            return use;
                case "defs":           return defs;
                case "symbol":         return symbol;
                case "linearGradient": return linearGradient;
                case "radialGradient": return radialGradient;
                case "stop":           return stop;
                case "clipPath":       return clipPath;
                case "mask":           return mask;
                case "filter":         return filter;
                case "pattern":        return pattern;
                case "title":          return title;
                case "desc":           return desc;
                default:               return element;
            }
        }
    }
}
