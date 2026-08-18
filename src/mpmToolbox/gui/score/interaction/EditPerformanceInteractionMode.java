package mpmToolbox.gui.score.interaction;

import com.alee.laf.menu.WebPopupMenu;
import mpmToolbox.gui.mpmTree.MpmTree;
import mpmToolbox.gui.mpmTree.MpmTreeNode;
import mpmToolbox.gui.mpmEditingTools.MpmEditingTools;
import mpmToolbox.gui.Settings;
import mpmToolbox.gui.score.ScoreDisplayPanel;
import mpmToolbox.gui.score.AnchorNodeHelper;
import nu.xom.Element;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

/**
 * Edit-performance mode places expressive performance data and selects performance nodes.
 */
public final class EditPerformanceInteractionMode extends AbstractInteractionMode {
    private final AnchorNodeHelper anchorNodeHelper;

    /**
     * Creates the edit-performance interaction handler for the score panel.
     * @param panel the owning score panel
     */
    public EditPerformanceInteractionMode(ScoreDisplayPanel panel) {
        super(panel, "Add/Place Performance", "add or place performance data", Color.CYAN);
        this.anchorNodeHelper = new AnchorNodeHelper(panel);
    }

    /**
     * Switches to performance-edit cursors and refreshes the anchor preview.
     * @param mouseEvent the enter event
     */
    @Override
    public void mouseEntered(MouseEvent mouseEvent) {
        if (mouseEvent.isControlDown()) {
            this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            return;
        }
        this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        updateMousePosition(mouseEvent);
        if (this.panel.getScorePage().isEmpty()) {
            return;
        }
        this.anchorNodeHelper.updateAnchor(this.panel.getMousePositionInImage());
        this.panel.repaint();
    }

    /**
     * Clears transient edit-performance state when leaving the panel.
     * @param mouseEvent the exit event
     */
    @Override
    public void mouseExited(MouseEvent mouseEvent) {
        if (!mouseEvent.isControlDown()) {
            clearTransientState();
        }
    }

    /**
     * Reuses the shared pan behavior while dragging.
     * @param mouseEvent the drag event
     */
    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        handlePanDrag(mouseEvent);
    }

    /**
     * Keeps the performance anchor preview under the pointer.
     * @param mouseEvent the move event
     */
    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
        if (mouseEvent.isControlDown()) {
            this.panel.setCursor((this.panel.getOverlayElementAt(mouseEvent) == null) ? Cursor.getDefaultCursor() : new Cursor(Cursor.HAND_CURSOR));
            return;
        }
        updateMousePosition(mouseEvent);
        if (!this.panel.getScorePage().isEmpty()) {
            this.anchorNodeHelper.updateAnchor(this.panel.getMousePositionInImage());
        }
        this.panel.repaint();
    }

    /**
     * Opens creation or context menus for performance annotations.
     * @param mouseEvent the release event
     */
    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        if (handlePanOrSelectRelease(mouseEvent)) {
            return;
        }

        this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        updateMousePosition(mouseEvent);

        switch (mouseEvent.getButton()) {
            case MouseEvent.BUTTON1:
                if (this.panel.getParentScoreDocumentData().getProjectPane().getMpm() == null) {
                    this.panel.showHasNoMpmPopUp(mouseEvent);
                    return;
                }
                if (this.panel.getParentScoreDocumentData().getProjectPane().getMpm().size() == 0) {
                    this.panel.showNoPerformancePopUp(mouseEvent);
                    return;
                }
                this.panel.makePlaceAndCreateContextMenu().show(this.panel, mouseEvent.getX() - 25, mouseEvent.getY());
                break;
            case MouseEvent.BUTTON3:
                Element selectedElement = this.panel.getOverlayElementAt(mouseEvent);
                MpmTree mpmTree = this.panel.getParentScoreDocumentData().getProjectPane().getMpmTree();
                MpmTreeNode mpmTreeNode = mpmTree.findNode(selectedElement, true);
                if (mpmTreeNode != null) {
                    mpmTree.setSelectedNode(mpmTreeNode);
                    mpmTree.scrollPathToVisible(mpmTreeNode.getTreePath());
                    WebPopupMenu editMenu = MpmEditingTools.makeScoreContextMenu(mpmTreeNode, mpmTree, this.panel.getScorePage());
                    editMenu.show(this.panel, mouseEvent.getX() - 25, mouseEvent.getY());
                }
                break;
            default:
                break;
        }
    }

    /**
     * Performs setup when edit-performance mode is activated: ensures a map entry is selected in the MPM tree.
     */
    @Override
    public void performSetup() {
        MpmTree mpmTree = this.panel.getParentScoreDocumentData().getProjectPane().getMpmTree();
        if (mpmTree != null) {
            MpmTreeNode node = mpmTree.getSelectedNode();
            if ((node == null) || !node.isMapEntryType()) {
                mpmTree.gotoFirstMapEntryNode();
            }
        }
    }

    /**
     * Draws a line from the anchor node to the mouse position and a performance symbol preview.
     * @param g2 the Graphics2D context for drawing
     */
    @Override
    public void drawModeSpecificOverlay(Graphics2D g2) {
        if (this.panel.getMousePositionInImage() == null) {
            return;
        }

        // draw the line between mouse pointer and anchor node
        if (this.anchorNodeHelper.getAnchorNode() != null) {
            g2.setColor(Settings.scorePerformanceColorHighlighted);
            g2.drawLine((int) this.anchorNodeHelper.getAnchorNode().getX(), (int) this.anchorNodeHelper.getAnchorNode().getY(), 
                        this.panel.getMousePositionInImage().x, this.panel.getMousePositionInImage().y);
        }

        // draw the performance annotation symbol at the mouse position (kind of preview)
        g2.setColor(Settings.scorePerformanceColor);
        g2.fillRect(this.panel.getMousePositionInImage().x - this.panel.getOverlayXOffset(),
                    this.panel.getMousePositionInImage().y - this.panel.getOverlayXOffset(),
                    this.panel.getOverlayXWidth(), this.panel.getOverlayXWidth());
    }

    /**
     * Restores the performance-edit cursor when Control is released.
     * @param keyEvent the released key event
     */
    @Override
    public void keyReleased(java.awt.event.KeyEvent keyEvent) {
        super.keyReleased(keyEvent);
        if (keyEvent.getKeyCode() == java.awt.event.KeyEvent.VK_CONTROL) {
            this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }
    }
}
