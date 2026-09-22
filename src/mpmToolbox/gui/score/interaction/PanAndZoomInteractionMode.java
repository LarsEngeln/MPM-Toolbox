package mpmToolbox.gui.score.interaction;

import mpmToolbox.gui.score.ScoreDisplayPanel;
import mpmToolbox.gui.Settings;

import java.awt.Cursor;
import java.awt.event.MouseEvent;

/**
 * Pan/zoom mode keeps selection passive and only changes hover feedback.
 */
public final class PanAndZoomInteractionMode extends AbstractInteractionMode {
    /**
     * Creates the pan/zoom interaction handler for the score panel.
     * @param panel the owning score panel
     */
    public PanAndZoomInteractionMode(ScoreDisplayPanel panel) {
        super(panel, "Pan & Zoom", "pan and zoom interaction mode", Settings.foregroundColor);
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
    }

    /**
     * Delegates release handling to the shared pan/click logic.
     * @param mouseEvent the release event
     */
    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        this.panel.dragOrSelectGesture(mouseEvent);
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
    }
}
