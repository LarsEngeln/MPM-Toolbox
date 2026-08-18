package mpmToolbox.gui.score.interaction;

import mpmToolbox.gui.mpmTree.MpmTree;
import mpmToolbox.gui.mpmTree.MpmTreeNode;
import mpmToolbox.gui.msmTree.MsmTree;
import mpmToolbox.gui.msmTree.MsmTreeNode;
import mpmToolbox.gui.Settings;
import mpmToolbox.gui.score.ScoreDisplayPanel;
import mpmToolbox.gui.score.AnchorNodeHelper;
import mpmToolbox.projectData.score.ScoreNode;
import mpmToolbox.supplementary.Tools;
import nu.xom.Element;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Map;

/**
 * Select/edit mode supports shift-multi-select and dragging overlay anchors.
 */
public final class SelectEditInteractionMode extends AbstractInteractionMode {
    private Element draggedElement = null;                                  // the element whose anchor is currently being dragged
    private ArrayList<Map.Entry<Element, ScoreNode>> currentSelection = null; // currently selected ScoreNodes
    private final AnchorNodeHelper anchorNodeHelper;
    private final ScoreNoteMultiselectHelper noteMultiselect;

    /**
     * Creates the select/edit interaction handler for the score panel.
     * @param panel the owning score panel
     */
    public SelectEditInteractionMode(ScoreDisplayPanel panel) {
        super(panel, "Select / Edit", "select and drag annotation anchors to reposition them", Color.ORANGE);
        this.anchorNodeHelper = new AnchorNodeHelper(panel);
        this.noteMultiselect = new ScoreNoteMultiselectHelper(panel.getParentScoreDocumentData().getProjectPane().getMsmTree());
    }

    /**
     * Refreshes the hover anchor when the pointer enters the panel.
     * @param mouseEvent the enter event
     */
    @Override
    public void mouseEntered(MouseEvent mouseEvent) {
        if (mouseEvent.isControlDown()) {
            this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            return;
        }
        this.draggedElement = null;
        this.anchorNodeHelper.getAnchorDragOffset().setLocation(0.0, 0.0);
        updateMousePosition(mouseEvent);
        this.anchorNodeHelper.updateAnchorForSelectEdit(this.panel.getMousePositionInImage(), this.noteMultiselect);
        this.panel.repaint();
    }

    /**
     * Clears temporary select/edit state when leaving the panel.
     * @param mouseEvent the exit event
     */
    @Override
    public void mouseExited(MouseEvent mouseEvent) {
        if (!mouseEvent.isControlDown()) {
            clearTransientState();
        }
    }

    /**
     * Starts multiselect or drag pickup gestures on press.
     * @param mouseEvent the press event
     */
    @Override
    public void mousePressed(MouseEvent mouseEvent) {
        if (mouseEvent.getButton() == MouseEvent.BUTTON1
                && mouseEvent.isShiftDown()
                && !mouseEvent.isControlDown()) {
            updateMousePosition(mouseEvent);
            this.noteMultiselect.start(this.panel.getMousePositionInImage());
            this.draggedElement = null;
            this.anchorNodeHelper.getAnchorDragOffset().setLocation(0.0, 0.0);
            this.anchorNodeHelper.reset();
            this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            this.panel.repaint();
            return;
        }

        if (mouseEvent.getButton() == MouseEvent.BUTTON1
                && !mouseEvent.isControlDown()
                && !mouseEvent.isShiftDown()) {
            updateMousePosition(mouseEvent);
            nu.xom.Element elt = this.panel.getOverlayElementAt(mouseEvent);
            if (elt != null) {
                this.draggedElement = elt;
               if (this.anchorNodeHelper.getAnchorNode() != null) {
                    Point clickPos = this.panel.getMousePositionInImage();
                  this.anchorNodeHelper.getAnchorDragOffset().setLocation(
                          this.anchorNodeHelper.getAnchorNode().getX() - clickPos.x,
                          this.anchorNodeHelper.getAnchorNode().getY() - clickPos.y);
                } else {
                  this.anchorNodeHelper.getAnchorDragOffset().setLocation(0.0, 0.0);
                }
                this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                this.panel.repaint();
            }
        }
    }

    /**
     * Finishes multiselect or completes a drag-and-drop placement.
     * @param mouseEvent the release event
     */
    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        if (handlePanOrSelectRelease(mouseEvent)) {
            return;
        }

        if (this.noteMultiselect.isSelecting()
                && mouseEvent.getButton() == MouseEvent.BUTTON1) {
            updateMousePosition(mouseEvent);
            this.noteMultiselect.update(this.panel.getMousePositionInImage());
            this.currentSelection = this.noteMultiselect.finishAndSelect(this.panel.getScorePage().getAllEntries().entrySet());
            this.anchorNodeHelper.updateAnchorForSelectEdit(this.panel.getMousePositionInImage(), this.noteMultiselect);
            this.panel.repaint();
            return;
        }

        if ((this.draggedElement != null)
                && mouseEvent.getButton() == MouseEvent.BUTTON1) {
            updateMousePosition(mouseEvent);
            double targetX = this.panel.getMousePositionInImage().getX() + this.anchorNodeHelper.getAnchorDragOffset().x;
            double targetY = this.panel.getMousePositionInImage().getY() + this.anchorNodeHelper.getAnchorDragOffset().y;
            this.panel.getScorePage().addEntry(targetX, targetY, this.draggedElement);

            if (this.draggedElement.getLocalName().equals("note")) {
                MsmTree msmTree = this.panel.getParentScoreDocumentData().getProjectPane().getMsmTree();
                MsmTreeNode msmTreeNode = msmTree.findNode(this.draggedElement, true);
                if (msmTreeNode != null) {
                    msmTree.updateNode(msmTreeNode);
                    msmTree.setSelectedNode(msmTreeNode);
                    msmTree.scrollPathToVisible(msmTreeNode.getTreePath());
                }
            } else {
                MpmTree mpmTree = this.panel.getParentScoreDocumentData().getProjectPane().getMpmTree();
                if (mpmTree != null) {
                    MpmTreeNode mpmTreeNode = mpmTree.findNode(this.draggedElement, true);
                    if (mpmTreeNode != null) {
                        mpmTree.setSelectedNode(mpmTreeNode);
                        mpmTree.scrollPathToVisible(mpmTreeNode.getTreePath());
                    }
                    mpmTree.clearSelection();
                }
            }

            this.currentSelection = null;
            this.draggedElement = null;
            updateMousePosition(mouseEvent);
            this.anchorNodeHelper.updateAnchorForSelectEdit(this.panel.getMousePositionInImage(), this.noteMultiselect);
            this.panel.repaint();
        }
    }

    /**
     * Updates either the selection rectangle, dragged preview, or pan gesture.
     * @param mouseEvent the drag event
     */
    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        if (this.noteMultiselect.isSelecting()) {
            updateMousePosition(mouseEvent);
            this.noteMultiselect.update(this.panel.getMousePositionInImage());
            this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            this.panel.repaint();
            return;
        }

        if (this.draggedElement != null) {
            updateMousePosition(mouseEvent);
            this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            this.panel.repaint();
            return;
        }

        handlePanDrag(mouseEvent);
    }

    /**
     * Keeps the selection anchor and hover cursor in sync with the pointer.
     * @param mouseEvent the move event
     */
    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
        if (mouseEvent.isControlDown()) {
            this.panel.setCursor((this.panel.getOverlayElementAt(mouseEvent) == null) ? Cursor.getDefaultCursor() : new Cursor(Cursor.HAND_CURSOR));
            return;
        }

        if (this.noteMultiselect.isSelecting()) {
            return;
        }

        updateMousePosition(mouseEvent);
        this.anchorNodeHelper.updateAnchorForSelectEdit(this.panel.getMousePositionInImage(), this.noteMultiselect);
        this.panel.repaint();
    }

    /**
     * Draws a selection ring around the nearest draggable anchor and a dragged element preview.
     * @param g2 the Graphics2D context for drawing
     */
    @Override
    public void drawModeSpecificOverlay(Graphics2D g2) {
        if (this.panel.getMousePositionInImage() == null) {
            return;
        }

        // draw a selection ring around the nearest draggable anchor (hover feedback)
        if (this.anchorNodeHelper.getAnchorNode() != null) {
            g2.setColor(Settings.editColorHighlighted);
            int r = this.panel.getOverlayXWidth() / 2 + 4;
            g2.drawOval((int) this.anchorNodeHelper.getAnchorNode().getX() - r, (int) this.anchorNodeHelper.getAnchorNode().getY() - r, 2 * r, 2 * r);
        }

        // if dragging, draw the element preview at the mouse position with the original grab offset preserved
        if (this.draggedElement != null) {
            g2.setColor(Settings.editColor);
            // apply the grab offset so the anchor does not snap its centre to the cursor
            int px = (int) (this.panel.getMousePositionInImage().x + this.anchorNodeHelper.getAnchorDragOffset().x);
            int py = (int) (this.panel.getMousePositionInImage().y + this.anchorNodeHelper.getAnchorDragOffset().y);
            Element element = this.draggedElement;
            if (element.getLocalName().equals("note")) {
                g2.fillOval(px - this.panel.getOverlayXOffset(), py - this.panel.getOverlayYOffset(), this.panel.getOverlayXWidth(), this.panel.getOverlayYWidth());
            } else if (element.getLocalName().equals("style")) {
                g2.fill(Tools.generateDiamondShape(px, py, this.panel.getOverlayXWidth(), this.panel.getOverlayXWidth()));
            } else {
                g2.fillRect(px - this.panel.getOverlayXOffset(), py - this.panel.getOverlayXOffset(), this.panel.getOverlayXWidth(), this.panel.getOverlayXWidth());
            }
        }

        this.noteMultiselect.paintSelectionRectangle(g2, this.panel.getOverlayYWidth());
    }

    @Override
    protected void clearTransientState() {
        super.clearTransientState();
        this.draggedElement = null;
        this.currentSelection = null;
        this.noteMultiselect.clear();
    }

    /**
     * Restores the select/edit cursor when Control is released.
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
