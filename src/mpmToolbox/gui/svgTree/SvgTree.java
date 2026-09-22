package mpmToolbox.gui.svgTree;

import com.alee.api.annotations.NotNull;
import com.alee.extended.tree.WebExTree;
import mpmToolbox.gui.ProjectPane;
import mpmToolbox.projectData.SvgData;
import nu.xom.Element;

import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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
            Element selectedElement = null;
            
            if (path != null) {
                SvgTreeNode node = this.getNodeForPath(path);
                if (node != null && node.getUserObject() instanceof Element) {
                    selectedElement = (Element) node.getUserObject();
                    svgData.setHighlightedElement(selectedElement);
                } else {
                    svgData.setHighlightedElement(null);
                }
            } else {
                svgData.setHighlightedElement(null);
            }
            
            // Notify AddSvgLinkMode about selection if it exists
            onSvgElementSelected(selectedElement);
            
            projectPane.repaintScoreDisplay();
        });

        // Tree → Score: when the mouse hovers over a node, set the hovered element
        // so the score display can draw a small hotpink indicator rectangle.
        this.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                TreePath path = getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    svgData.setHoveredElement(null);
                } else {
                    SvgTreeNode node = getNodeForPath(path);
                    if (node != null && node.getUserObject() instanceof Element) {
                        svgData.setHoveredElement((Element) node.getUserObject());
                    } else {
                        svgData.setHoveredElement(null);
                    }
                }
                projectPane.repaintScoreDisplay();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                svgData.setHoveredElement(null);
                projectPane.repaintScoreDisplay();
            }
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

    /**
     * Called when an SVG element is selected in the tree.
     * This method allows AddSvgLinkInteractionMode to be notified without
     * creating circular dependencies (SvgTree doesn't access InteractionModeManager).
     * Instead, AddSvgLinkInteractionMode can register a listener or poll this state.
     *
     * @param selectedElement the selected SVG element, or null if deselected
     */
    private void onSvgElementSelected(Element selectedElement) {
        // This is a placeholder for notification logic.
        // AddSvgLinkInteractionMode should call getHighlightedElement() on SvgData
        // to detect when a <g> element is selected, rather than having SvgTree
        // directly access the mode.
        // See: SvgData.getHighlightedElement()
    }

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
