package mpmToolbox.gui.score.interaction;

import mpmToolbox.gui.msmTree.MsmTree;
import mpmToolbox.gui.msmTree.MsmTreeNode;
import mpmToolbox.gui.Settings;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import com.alee.laf.menu.WebPopupMenu;
import mpmToolbox.gui.msmEditingTools.MsmEditingTools;
import mpmToolbox.gui.score.ScoreDisplayPanel;
import nu.xom.Element;

/**
 * Mark-notes mode places MSM notes onto the score and exposes their context menus.
 */
public final class MarkNotesInteractionMode extends AbstractInteractionMode {
    /**
     * Creates the mark-notes interaction handler for the score panel.
     * @param panel the owning score panel
     */
    public MarkNotesInteractionMode(ScoreDisplayPanel panel) {
        super(panel, "Mark Notes", "<html><center>Place notes from Musical Sequence Markup on the score.<br>Be sure to select the respective note in the Musical Sequence Markup.<br>Left click places a note, right click deletes a note from the score page.</center></html>", Color.GREEN);
    }

    /**
     * Switches to the mark-notes cursor and refreshes the preview.
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
        this.panel.repaint();
    }

    /**
     * Clears temporary mark-notes state when the pointer leaves the panel.
     * @param mouseEvent the exit event
     */
    @Override
    public void mouseExited(MouseEvent mouseEvent) {
        if (!mouseEvent.isControlDown()) {
            clearTransientState();
        }
    }

    /**
     * Reuses the shared pan behavior for drag gestures.
     * @param mouseEvent the drag event
     */
    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        handlePanDrag(mouseEvent);
    }

    /**
     * Keeps the preview in sync with the current pointer position.
     * @param mouseEvent the move event
     */
    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
        if (mouseEvent.isControlDown()) {
            this.panel.setCursor((this.panel.getOverlayElementAt(mouseEvent) == null) ? Cursor.getDefaultCursor() : new Cursor(Cursor.HAND_CURSOR));
            return;
        }
        updateMousePosition(mouseEvent);
        this.panel.repaint();
    }

    /**
     * Places notes or opens the corresponding context menu on release.
     * @param mouseEvent the release event
     */
    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        super.mouseReleased(mouseEvent);
        
        if (handlePanOrSelectRelease(mouseEvent)) {
            return;
        }

        this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        updateMousePosition(mouseEvent);

        switch (mouseEvent.getButton()) {
            case MouseEvent.BUTTON1:
                this.panel.makeNoteAssociation(mouseEvent);
                break;
            case MouseEvent.BUTTON3:
                Element selectedElement = this.panel.getOverlayElementAt(mouseEvent);
                MsmTree msmTree = this.panel.getScoreDocumentData().getProjectPane().getMsmTree();
                MsmTreeNode msmTreeNode = msmTree.findNode(selectedElement, true);
                if (msmTreeNode != null) {
                    msmTree.setSelectedNode(msmTreeNode);
                    msmTree.scrollPathToVisible(msmTreeNode.getTreePath());
                    msmTreeNode.play(this.panel.getScoreDocumentData().getProjectPane().getParentMpmToolbox().getMidiPlayerForSingleNotes());   // the node might be a node and should play its note via MIDI when selected
                    WebPopupMenu menu = MsmEditingTools.makeScoreContextMenu(msmTreeNode, msmTree, this.panel.getScorePage());
                    menu.show(this.panel, mouseEvent.getX() - 25, mouseEvent.getY());
                }
                break;
            default:
                break;
        }
    }

    /**
     * Performs setup when mark-notes mode is activated: ensures a note is selected in the MSM tree.
     */
    @Override
    public void performSetup() {
        MsmTree msmTree = this.panel.getScoreDocumentData().getProjectPane().getMsmTree();
        if (msmTree != null) {
            MsmTreeNode node = msmTree.getSelectedNode();
            if ((node == null) || (node.getType() != MsmTreeNode.XmlNodeType.note)) {
                msmTree.gotoFirstNoteNode();
            }
        }
    }

    /**
     * Draws a preview note annotation at the mouse position.
     * @param g2 the Graphics2D context for drawing
     */
    @Override
    public void drawModeSpecificOverlay(Graphics2D g2) {
        if (this.panel.getMousePositionInImage() == null) {
            return;
        }
        g2.setColor(Settings.scoreNoteColor);
        g2.fillOval(this.panel.getMousePositionInImage().x - this.panel.getOverlayXOffset(),
                    this.panel.getMousePositionInImage().y - this.panel.getOverlayYOffset(),
                    this.panel.getOverlayXWidth(), this.panel.getOverlayYWidth());
    }

    /**
     * Restores the mark-notes cursor when Control is released.
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
