package mpmToolbox.gui.score;

import meico.supplementary.KeyValue;
import mpmToolbox.gui.Settings;
import mpmToolbox.gui.score.interaction.ScoreNoteMultiselectHelper;
import mpmToolbox.projectData.score.ScoreNode;
import mpmToolbox.projectData.score.ScorePage;
import mpmToolbox.supplementary.orthantNeighborhoodGraph.ONGNode;

import java.awt.*;
import java.awt.geom.Point2D;

/**
 * Manages anchor node state for interaction modes.
 * Tracks the nearest overlay node to the mouse position and handles cursor updates.
 */
public class AnchorNodeHelper {
    private final ScoreDisplayPanel panel;
    private ScoreNode anchorNode = null;
    private final Point2D.Double anchorDragOffset = new Point2D.Double(0, 0);

    /**
     * Creates the anchor node helper for the given score display panel.
     * @param panel the score display panel
     */
    public AnchorNodeHelper(ScoreDisplayPanel panel) {
        this.panel = panel;
    }

    /**
     * Gets the current anchor node.
     * @return the anchor node, or null if none is set
     */
    public ScoreNode getAnchorNode() {
        return this.anchorNode;
    }

    /**
     * Gets the drag offset from the click point to the anchor centre.
     * Used in selectEdit mode to preserve grab offset when dragging.
     * @return the anchor drag offset
     */
    public Point2D.Double getAnchorDragOffset() {
        return this.anchorDragOffset;
    }

    /**
     * Clears the anchor node and drag offset state.
     */
    public void reset() {
        this.anchorNode = null;
        this.anchorDragOffset.setLocation(0.0, 0.0);
    }

    /**
     * Updates the anchor node according to its current value and the nearest neighbor to the specified point.
     * Uses a hysteresis threshold to avoid flickering when hovering between nearby nodes.
     * @param point2D the current mouse position in image coordinates
     */
    public void updateAnchor(Point2D point2D) {
        ScorePage scorePage = this.panel.getScorePage();
        KeyValue<ONGNode, Double> nearest = scorePage.findNearestNeighborOf(point2D.getX(), point2D.getY());
        if (this.anchorNode == null) {
            this.anchorNode = (ScoreNode) nearest.getKey();
            return;
        }
        if (this.anchorNode != nearest.getKey()) {
            double anchorDistance = this.anchorNode.distanceSq(point2D);
            if ((nearest.getValue() / anchorDistance) <= Settings.anchorSwitchOvershootThreshold) {
                this.anchorNode = (ScoreNode) nearest.getKey();
            }
        }
    }

    /**
     * In selectEdit mode: updates anchor node to the nearest overlay element only if the cursor is
     * close enough to grab it. Also updates the mouse cursor accordingly.
     * @param point2D the current mouse position in image coordinates
     * @param noteMultiselect the note multiselect helper to check selection state
     */
    public void updateAnchorForSelectEdit(Point2D point2D, ScoreNoteMultiselectHelper noteMultiselect) {
        if (noteMultiselect.isSelecting()) {
            this.anchorNode = null;
            this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            return;
        }

        ScorePage scorePage = this.panel.getScorePage();
        if (scorePage.isEmpty()) {
            this.anchorNode = null;
            this.panel.setCursor(Cursor.getDefaultCursor());
            return;
        }
        KeyValue<ONGNode, Double> nearest = scorePage.findNearestNeighborOf(point2D.getX(), point2D.getY());
        if (nearest != null && (Math.sqrt(nearest.getValue()) * 2.0) <= this.panel.getOverlayXWidth()) {
            this.anchorNode = (ScoreNode) nearest.getKey();
            this.panel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        } else {
            this.anchorNode = null;
            this.panel.setCursor(Cursor.getDefaultCursor());
        }
    }
}
