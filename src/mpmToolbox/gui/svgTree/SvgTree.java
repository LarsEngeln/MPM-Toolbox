package mpmToolbox.gui.svgTree;

import com.alee.api.annotations.NotNull;
import com.alee.extended.tree.WebExTree;
import mpmToolbox.gui.ProjectPane;
import mpmToolbox.projectData.SvgData;
import nu.xom.Element;

import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 * A WebExTree that displays the XML structure of an SVG document.
 *
 * @author Lars Engeln
 */
public class SvgTree extends WebExTree<SvgTreeNode> {

    @NotNull private final SvgData svgData;

    /**
     * Constructor.
     * @param svgData    the SVG data to display
     * @param projectPane the owning ProjectPane (used for score repaint on selection)
     */
    public SvgTree(@NotNull SvgData svgData, @NotNull ProjectPane projectPane) {
        super(new SvgTreeDataProvider(svgData.getXmlRoot()));
        this.svgData = svgData;

        this.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        this.setCellRenderer(new SvgTreeCellRenderer());
        this.setToolTipProvider(new SvgTreeTooltipProvider());

        // Tree → Score: when a node is selected in this tree, highlight the
        // corresponding element in the score display and repaint it.
        this.addTreeSelectionListener(event -> {
            TreePath path = event.getNewLeadSelectionPath();
            if (path == null) {
                svgData.setHighlightedElement(null);
            } else {
                SvgTreeNode node = this.getNodeForPath(path);
                if (node != null && node.getUserObject() instanceof Element) {
                    svgData.setHighlightedElement((Element) node.getUserObject());
                } else {
                    svgData.setHighlightedElement(null);
                }
            }
            projectPane.repaintScoreDisplay();
        });
    }

    /**
     * Access the SVG data this tree is based on.
     * @return SvgData
     */
    public SvgData getSvgData() {
        return this.svgData;
    }

    /**
     * Select the tree node that corresponds to the given XOM element.
     * Scrolls the node into view. Does nothing if not found.
     */
    public void selectNodeForElement(@NotNull Element element) {
        SvgTreeNode root = this.getRootNode();
        SvgTreeNode target = findNodeForElement(root, element);
        if (target != null) {
            this.setSelectedNode(target);
            this.scrollPathToVisible(target.getTreePath());
        }
    }

    // -------------------------------------------------------------------------

    private static SvgTreeNode findNodeForElement(SvgTreeNode node, Element element) {
        if (node.getUserObject() == element)
            return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            SvgTreeNode found = findNodeForElement((SvgTreeNode) node.getChildAt(i), element);
            if (found != null)
                return found;
        }
        return null;
    }
}
