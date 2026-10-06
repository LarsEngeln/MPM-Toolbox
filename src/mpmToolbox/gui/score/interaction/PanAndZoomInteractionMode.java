package mpmToolbox.gui.score.interaction;

import mpmToolbox.gui.score.ScoreDisplayPanel;
import mpmToolbox.gui.Settings;
import mpmToolbox.projectData.score.ScoreNode;
import nu.xom.Element;

import java.awt.Cursor;
import java.awt.event.MouseEvent;

/**
 * Pan/zoom mode keeps selection passive and only changes hover feedback.
 *
 * @author Lars Engeln
 */
public final class PanAndZoomInteractionMode extends AbstractInteractionMode {
    private final AnchorNodeHelper anchorNodeHelper;
    /**
     * Creates the pan/zoom interaction handler for the score panel.
     * @param panel the owning score panel
     */
    public PanAndZoomInteractionMode(ScoreDisplayPanel panel) {
        super(panel, "Pan & Zoom", "pan and zoom interaction mode", Settings.foregroundColor);
        this.anchorNodeHelper = new AnchorNodeHelper(panel);
        this.anchorNodeHelper.setTreeSelectionEnabled(false);
    }

    /**
     * Keeps the default cursor when entering the panel in pan mode.
     * @param mouseEvent the enter event
     */
    @Override
    public void mouseEntered(MouseEvent mouseEvent) {
        if (mouseEvent.isControlDown()) {
            this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            return;
        }
        this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        updateMousePosition(mouseEvent);
        if (this.panel.getScorePage() != null && !this.panel.getScorePage().isEmpty()) {
            this.anchorNodeHelper.updateAnchor(this.panel.getMousePositionInImage());
        }
        this.panel.repaint();
    }

    /**
     * Clears state when leaving the panel.
     * @param mouseEvent the exit event
     */
    @Override
    public void mouseExited(MouseEvent mouseEvent) {
        clearStates();
        this.anchorNodeHelper.reset();
    }

    /**
     * Delegates release handling to the shared pan/click logic.
     * @param mouseEvent the release event
     */
    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        if (handlePanOrSelectRelease(mouseEvent)) {
            return;
        }

        Element selectedElement = this.panel.getOverlayElementAt(mouseEvent);
        Element selectedSvgElement = null;
        if (selectedElement == null) {
            selectedSvgElement = this.panel.handleSvgSelection(mouseEvent);
            if (selectedSvgElement == null) {
                this.panel.clearSvgSelection();
                this.panel.getScoreDocumentData().getProjectPane().getMsmTree().clearSelection();
                this.panel.getScoreDocumentData().getProjectPane().getMpmTree().clearSelection();

                return;
            }
        }

        updateMousePosition(mouseEvent);
        ScoreNode anchorNode = this.anchorNodeHelper.getAnchorNode();
    }

    /**
     * Drags the score image while the mouse is moving.
     * @param mouseEvent the drag event
     */
    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        handlePanDrag(mouseEvent);
    }

    /**
     * Updates the hover cursor based on overlay hit-testing.
     * @param mouseEvent the move event
     */
    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
        this.panel.setCursor((this.panel.getOverlayElementAt(mouseEvent) == null) ? Cursor.getDefaultCursor() : new Cursor(Cursor.HAND_CURSOR));

        this.panel.updateHoveredSvgElement(mouseEvent);

        updateMousePosition(mouseEvent);
        if (this.panel.getScorePage() != null && !this.panel.getScorePage().isEmpty()) {
            this.anchorNodeHelper.updateAnchor(this.panel.getMousePositionInImage());
        }
        this.panel.repaint();
    }
}
