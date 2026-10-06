package mpmToolbox.gui.svgTree;

import com.alee.api.annotations.NotNull;
import com.alee.api.annotations.Nullable;
import com.alee.extended.tree.WebExTree;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import mpmToolbox.gui.ProjectPane;
import mpmToolbox.projectData.SvgData;
import nu.xom.Element;

import javax.swing.*;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A WebExTree that displays the XML structure of an SVG document.
 *
 * @author Lars Engeln
 */
public class SvgTree extends WebExTree<SvgTreeNode> {

    @NotNull private final SvgData svgData;
    @NotNull private final ProjectPane projectPane;
    @Nullable private Color scoreColor = null;

    /**
     * Constructor.
     * @param svgData the SVG data to display
     * @param projectPane the owning ProjectPane (used for score repaint on selection)
     */
    public SvgTree(@NotNull SvgData svgData, @NotNull ProjectPane projectPane) {
        super(new SvgTreeDataProvider(svgData.getXmlRoot()));
        this.svgData = svgData;
        this.projectPane = projectPane;

        this.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        this.setCellRenderer(new SvgTreeCellRenderer());
        this.setToolTipProvider(new SvgTreeTooltipProvider());

        // Tree -> Score: when a node is selected in this tree, highlight the
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
            
            this.projectPane.repaintScoreDisplay();
        });

        // Tree -> Score: when the mouse hovers over a node, set the hovered element
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
                SvgTree.this.projectPane.repaintScoreDisplay();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                svgData.setHoveredElement(null);
                SvgTree.this.projectPane.repaintScoreDisplay();
            }
        });

        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showContextMenuIfRequested(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showContextMenuIfRequested(e);
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
     * Update the given node and all its children. This is useful if the underlying XML data has changed and the tree needs to reflect those changes.
     * @param node to be updated
     */
    @Override
    public void updateNode(@Nullable final SvgTreeNode node) {
        if (node == null)
            return;
        node.update();
        super.updateNode(node);
    }

    /**
     * Select the tree node that corresponds to the given XOM element.
     * Scrolls the node into view. Does nothing if not found.
     * @param element to be selected
     */
    public void selectNodeForElement(@NotNull Element element) {
        SvgTreeNode root = this.getRootNode();
        SvgTreeNode target = findNodeForElement(root, element);
        if (target != null) {
            this.setSelectedNode(target);
            this.scrollPathToVisible(target.getTreePath());
        }
    }

    /**
     * tries to find the child node that is the element
     * @param node node where to search in
     * @param element element to find
     * @return the found node, or null if not found
     */
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

    /**
     * shows the ContextMenu at mouseEvent
     * @param mouseEvent
     */
    private void showContextMenuIfRequested(@NotNull MouseEvent mouseEvent) {
        if (!mouseEvent.isPopupTrigger())
            return;

        int row = this.getRowForLocation(mouseEvent.getX(), mouseEvent.getY());
        if (row < 0)
            return;

        TreePath clickedPath = this.getPathForRow(row);
        if ((clickedPath != null) && !this.isPathSelected(clickedPath))
            this.setSelectionPath(clickedPath);

        SvgTreeNode node = this.getNodeForRow(row);
        if (node == null || !(node.getUserObject() instanceof Element))
            return;

        Element element = (Element) node.getUserObject();
        WebPopupMenu menu = this.createContextMenu(node, element);
        if (menu.getComponentCount() == 0)
            return;
        menu.show(this, mouseEvent.getX() - 25, mouseEvent.getY());
    }

    /**
     * creates the ContextMenu
     * @param node
     * @param element
     * @return the ContextMenu as WebPopupMenu
     */
    @NotNull
    private WebPopupMenu createContextMenu(@NotNull SvgTreeNode node, @NotNull Element element) {
        WebPopupMenu menu = new WebPopupMenu();

        if (this.isSvgRootNode(node, element)) {
            WebMenuItem pickColorItem = new WebMenuItem("pick Color");
            pickColorItem.addActionListener(actionEvent -> {
                Color pickedColor = JColorChooser.showDialog(
                        this,
                        "Pick SVG Color",
                        (this.scoreColor != null) ? this.scoreColor : Color.decode("#C9B791")
                );
                if (pickedColor != null) {
                    this.scoreColor = pickedColor;
                    this.svgData.setScoreColor(pickedColor);
                    this.projectPane.repaintScoreDisplay();
                }
            });
            menu.add(pickColorItem);

            WebMenuItem useOriginalColorItem = new WebMenuItem("use original Color");
            useOriginalColorItem.addActionListener(actionEvent -> {
                //this.scoreColor = null;
                this.svgData.useOriginalSVGColor();
                this.projectPane.repaintScoreDisplay();
            });
            menu.add(useOriginalColorItem);

            WebMenuItem useHiddenColorItem = new WebMenuItem("use hidden Color");
            useHiddenColorItem.addActionListener(actionEvent -> {
                //this.scoreColor = null;
                this.svgData.useHiddenSVGColor();
                this.projectPane.repaintScoreDisplay();
            });
            menu.add(useHiddenColorItem);
        }

        return menu;
    }

    /**
     * Checks if the given node is the root SVG node (i.e., the <svg> element with no parent).
     * @param node SvgTreeNode to check
     * @param element corresponding Element
     * @return true if it is the rootNode, false otherwise
     */
    private boolean isSvgRootNode(@NotNull SvgTreeNode node, @NotNull Element element) {
        return "svg".equals(element.getLocalName()) && (node.getParent() == null);
    }
}
