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

    /**
     * Constructor for element nodes.
     * @param element the SVG XML Element represented by this node
     */
    public SvgTreeNode(@NotNull Element element) {
        super(element);
        this.generateMyName();
    }

    /**
     * Constructor for attribute nodes.
     * @param attribute the SVG XML Attribute represented by this node
     */
    public SvgTreeNode(@NotNull Attribute attribute) {
        super(attribute);
        this.generateMyName();
    }

    // -------------------------------------------------------------------------

    /**
     * Generates this node's name to be displayed in SvgTree
     */
    private void generateMyName() {
        Node obj = this.getUserObject();

        if (obj instanceof Attribute) {
            Attribute a = (Attribute) obj;
            String val = a.getValue();
            if (val.length() > 60) val = val.substring(0, 60) + "…";
            this.name = "<html><font color=\"silver\">@</font>  "
                    + a.getLocalName()
                    + " <font color=\"silver\">= " + val + "</font></html>";
            return;
        }

        Element e = (Element) obj;
        String localName = e.getLocalName();

        switch (localName) {
            case "svg": {
                this.name = "<html><font size=\"-2\" color=\"silver\">&lt;/&gt;</font> svg</html>";
                break;
            }
            case "g": {
                String id    = e.getAttributeValue("id");
                String label = e.getAttributeValue("label");
                String numOfChildren = String.valueOf(countSvgElementChildren(e));
                if (label == null) label = e.getAttributeValue("label", "http://www.inkscape.org/namespaces/inkscape");
                String display = (label != null) ? label : (id != null ? id : "");
                this.name = "<html><b>g</b>"
                        + " <font color=\"silver\">(" + numOfChildren + ")</font>"
                        + (display.isEmpty() ? "" : "  <font color=\"silver\">" + display + "</font>")
                        + "</html>";
                break;
            }
            case "path": {
                String id = e.getAttributeValue("id");
                this.name = "<html>path"
                        + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "")
                        + "</html>";
                break;
            }
            case "rect": {
                String w = e.getAttributeValue("width");
                String h = e.getAttributeValue("height");
                String id = e.getAttributeValue("id");
                this.name = "<html>rect"
                        + ((w != null && h != null) ? " <font color=\"silver\">" + w + " × " + h + "</font>" : "")
                        + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "")
                        + "</html>";
                break;
            }
            case "circle": {
                String r  = e.getAttributeValue("r");
                String id = e.getAttributeValue("id");
                this.name = "<html>circle"
                        + (r != null ? " <font color=\"silver\">r=" + r + "</font>" : "")
                        + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "")
                        + "</html>";
                break;
            }
            case "ellipse": {
                String rx = e.getAttributeValue("rx");
                String ry = e.getAttributeValue("ry");
                String id = e.getAttributeValue("id");
                this.name = "<html>ellipse"
                        + ((rx != null && ry != null) ? " <font color=\"silver\">" + rx + " × " + ry + "</font>" : "")
                        + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "")
                        + "</html>";
                break;
            }
            case "line": {
                String x1 = e.getAttributeValue("x1"), y1 = e.getAttributeValue("y1");
                String x2 = e.getAttributeValue("x2"), y2 = e.getAttributeValue("y2");
                this.name = "<html>line"
                        + ((x1 != null) ? " <font color=\"silver\">(" + x1 + "," + y1 + ")->(" + x2 + "," + y2 + ")</font>" : "")
                        + "</html>";
                break;
            }
            case "polyline":
            case "polygon": {
                String id = e.getAttributeValue("id");
                this.name = "<html>" + localName
                        + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "")
                        + "</html>";
                break;
            }
            case "text": {
                String value = e.getValue().trim();
                if (value.length() > 40) value = value.substring(0, 40) + "…";
                this.name = "<html>text"
                        + (value.isEmpty() ? "" : "  <font color=\"silver\">\"" + value + "\"</font>")
                        + "</html>";
                break;
            }
            case "tspan": {
                String value = e.getValue().trim();
                if (value.length() > 40) value = value.substring(0, 40) + "…";
                this.name = "<html>tspan"
                        + (value.isEmpty() ? "" : "  <font color=\"silver\">\"" + value + "\"</font>")
                        + "</html>";
                break;
            }
            case "image": {
                String href = e.getAttributeValue("href");
                if (href == null) href = e.getAttributeValue("href", "http://www.w3.org/1999/xlink");
                String id = e.getAttributeValue("id");
                this.name = "<html>image"
                        + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "")
                        + (href != null ? "  <font color=\"silver\">" + href + "</font>" : "")
                        + "</html>";
                break;
            }
            case "use": {
                String href = e.getAttributeValue("href");
                if (href == null) href = e.getAttributeValue("href", "http://www.w3.org/1999/xlink");
                this.name = "<html>use"
                        + (href != null ? "  <font color=\"silver\">" + href + "</font>" : "")
                        + "</html>";
                break;
            }
            case "title": {
                String value = e.getValue().trim();
                this.name = "<html>title"
                        + (value.isEmpty() ? "" : "  <font color=\"silver\">\"" + value + "\"</font>")
                        + "</html>";
                break;
            }
            case "desc": {
                String value = e.getValue().trim();
                if (value.length() > 60) value = value.substring(0, 60) + "…";
                this.name = "<html>desc"
                        + (value.isEmpty() ? "" : "  <font color=\"silver\">\"" + value + "\"</font>")
                        + "</html>";
                break;
            }
            default: {
                // all other elements (defs, symbol, linearGradient, radialGradient,
                // stop, clipPath, mask, filter, pattern, …): show localName + optional id
                String id = e.getAttributeValue("id");
                this.name = "<html>" + localName
                        + (id != null ? "  <font color=\"silver\">#" + id + "</font>" : "")
                        + "</html>";
                break;
            }
        }

    }


    /**
     * Counts the number of child elements of the given SVG element.
     * @param element the SVG element to count children of
     * @return the number of child elements
     */
    private static int countSvgElementChildren(Element element) {
        int count = 0;
        for (int i = 0; i < element.getChildCount(); i++) {
            if (element.getChild(i) instanceof Element) {
                count++;
            }
        }
        return count;
    }

    /**
     * Re-generate the display name (call after data changes, then invoke the tree's updateNode()).
     */
    public void update() {
        this.generateMyName();
    }

    /**
     * returns name as the TreeNode's 'text'
     * @param parameters
     * @return name of this node
     */
    @Override
    public String getText(TreeNodeParameters<SvgTreeNode, WebExTree<SvgTreeNode>> parameters) {
        return this.name;
    }

    /**
     * return special icon of this node
     * @param parameters
     * @return null (as it has no special icon)
     */
    public Icon getNodeIcon(TreeNodeParameters<SvgTreeNode, WebExTree<SvgTreeNode>> parameters) {
        return null;
    }

    /**
     * Tooltip text showing the raw XML of this node.
     * @return tooltip text
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

    /**
     * Returns the string representation of this node.
     * @return the node's name
     */
    @Override
    public String toString() {
        return this.name;
    }
}
