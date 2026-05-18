package mpmToolbox.gui.svgTree;

import com.alee.api.annotations.NotNull;
import com.alee.extended.tree.AbstractExTreeDataProvider;
import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Data provider that builds the SVG tree from a nu.xom root element.
 *
 * @author Lars Engeln
 */
public class SvgTreeDataProvider extends AbstractExTreeDataProvider<SvgTreeNode> {

    @NotNull private final Element svgRoot;

    /**
     * Constructor.
     * @param svgRoot the root &lt;svg&gt; element
     */
    public SvgTreeDataProvider(@NotNull Element svgRoot) {
        this.svgRoot = svgRoot;
    }

    /**
     * Root node of the tree.
     * @return root SvgTreeNode
     */
    @Override
    public SvgTreeNode getRoot() {
        return new SvgTreeNode(this.svgRoot);
    }

    /**
     * Children of the given node.
     * @param parent
     * @return list of child nodes
     */
    @Override
    public List<SvgTreeNode> getChildren(SvgTreeNode parent) {
        ArrayList<SvgTreeNode> children = new ArrayList<>();
        Node obj = parent.getUserObject();

        if (obj instanceof Attribute)   // attributes have no children
            return children;

        Element e = (Element) obj;

        // add attributes as child nodes
        for (int i = 0; i < e.getAttributeCount(); ++i)
            children.add(new SvgTreeNode(e.getAttribute(i)));

        // add child elements as child nodes
        Elements childElements = e.getChildElements();
        for (int i = 0; i < childElements.size(); ++i)
            children.add(new SvgTreeNode(childElements.get(i)));

        return children;
    }
}

