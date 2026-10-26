package mpmToolbox.gui.svgTree;

import com.alee.api.annotations.NotNull;
import com.alee.api.annotations.Nullable;
import com.alee.extended.tree.WebExTree;
import com.alee.laf.menu.WebMenuItem;
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
     * Creates the items for the SVG menu in the main menu's View menu.
     * @return the menu items
     */
    @NotNull
    public java.util.List<WebMenuItem> createSvgMenuItems() {
        java.util.List<WebMenuItem> items = new java.util.ArrayList<>();

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
        items.add(pickColorItem);

        WebMenuItem useOriginalColorItem = new WebMenuItem("use original Color");
        useOriginalColorItem.addActionListener(actionEvent -> {
            this.svgData.useOriginalSVGColor();
            this.projectPane.repaintScoreDisplay();
        });
        items.add(useOriginalColorItem);

        WebMenuItem useHiddenColorItem = new WebMenuItem("use hidden Color");
        useHiddenColorItem.addActionListener(actionEvent -> {
            this.svgData.useHiddenSVGColor();
            this.projectPane.repaintScoreDisplay();
        });
        items.add(useHiddenColorItem);

        return items;
    }
}