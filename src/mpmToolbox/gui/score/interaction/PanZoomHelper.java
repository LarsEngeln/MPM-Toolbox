package mpmToolbox.gui.score.interaction;

import mpmToolbox.gui.score.ScoreDisplayPanel;

import java.awt.Point;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;

/**
 * Encapsulates all pan and zoom state and operations for score display.
 * This helper manages the affine transformation, zoom factor, and drag gestures
 * used by interaction modes to pan and zoom the score image.
 */
public class PanZoomHelper {
    private final ScoreDisplayPanel panel;
    private final AffineTransform affineTransform = new AffineTransform();
    private AffineTransform inverseAffineTransform = new AffineTransform();
    private Double zoomFactor = null;
    private Point dragStartPoint = null;
    private final Point diff = new Point(0, 0);
    private final Point2D.Double offset = new Point2D.Double(0.0, 0.0);

    /**
     * Creates a pan/zoom helper for the given score display panel.
     * @param panel the score display panel
     */
    public PanZoomHelper(ScoreDisplayPanel panel) {
        this.panel = panel;
    }

    /**
     * Resets the image zoom to match the panel size and translate to initial position.
     */
    public void reset() {
        this.affineTransform.setToIdentity();
        this.zoomFactor = ((double) this.panel.getHeight()) / this.panel.getScorePage().getImage().getHeight();
        this.affineTransform.scale(this.zoomFactor, this.zoomFactor);

        try {
            this.inverseAffineTransform = this.affineTransform.createInverse();
        } catch (NoninvertibleTransformException e) {
            e.printStackTrace();
        }

        this.panel.repaint();
    }

    /**
     * Begins or continues a pan drag gesture.
     * @param mousePosition the current mouse position on screen
     */
    public void dragImage(Point mousePosition) {
        if (this.dragStartPoint == null) {
            this.dragStartPoint = mousePosition;
            return;
        }

        this.diff.setLocation(mousePosition.x - this.dragStartPoint.x, mousePosition.y - this.dragStartPoint.y);
        this.affineTransform.setToIdentity();
        this.affineTransform.translate(this.offset.getX() + this.diff.getX(), this.offset.getY() + this.diff.getY());

        if (this.zoomFactor == null)
            this.affineTransform.scale(1.0, 1.0);
        else
            this.affineTransform.scale(this.zoomFactor, this.zoomFactor);

        try {
            this.inverseAffineTransform = this.affineTransform.createInverse();
        } catch (NoninvertibleTransformException e) {
            e.printStackTrace();
        }

        this.panel.repaint();
    }

    /**
     * Handles mouse wheel zoom events.
     * @param mouseWheelEvent the wheel event
     */
    public void mouseWheelMoved(MouseWheelEvent mouseWheelEvent) {
        if (this.zoomFactor == null) {
            this.zoomFactor = 1.0;
        }

        double prevZoomFactor = this.zoomFactor;
        if (mouseWheelEvent.getWheelRotation() < 0) {
            this.zoomFactor *= 1.1;
        } else if (mouseWheelEvent.getWheelRotation() > 0) {
            this.zoomFactor /= 1.1;
        } else {
            return;
        }

        double zoomDiv = this.zoomFactor / prevZoomFactor;
        this.offset.setLocation((zoomDiv) * this.offset.getX() + (1.0 - zoomDiv) * mouseWheelEvent.getX(), (zoomDiv) * this.offset.getY() + (1.0 - zoomDiv) * mouseWheelEvent.getY());
        this.affineTransform.setToIdentity();
        this.affineTransform.translate(this.offset.getX(), this.offset.getY());
        this.affineTransform.scale(this.zoomFactor, this.zoomFactor);

        try {
            this.inverseAffineTransform = this.affineTransform.createInverse();
        } catch (NoninvertibleTransformException e) {
            e.printStackTrace();
        }

        this.panel.repaint();
    }

    /**
     * Clears the drag start point when a drag gesture ends.
     */
    public void clearDragStartPoint() {
        this.dragStartPoint = null;
    }

    /**
     * Commits the current pan offset when a drag gesture completes.
     */
    public void commitDragOffset() {
        if (this.dragStartPoint != null) {
            this.offset.setLocation(this.offset.getX() + this.diff.getX(), this.offset.getY() + this.diff.getY());
            this.dragStartPoint = null;
            this.diff.setLocation(0, 0);
        }
    }

    /**
     * Gets the affine transformation matrix.
     * @return the affine transform
     */
    public AffineTransform getAffineTransform() {
        return affineTransform;
    }

    /**
     * Gets the inverse affine transformation matrix.
     * @return the inverse affine transform
     */
    public AffineTransform getInverseAffineTransform() {
        return inverseAffineTransform;
    }

    /**
     * Gets the current zoom factor.
     * @return the zoom factor, or null if not yet initialized
     */
    public Double getZoomFactor() {
        return zoomFactor;
    }

    /**
     * Sets the zoom factor.
     * @param zoomFactor the zoom factor to set
     */
    public void setZoomFactor(Double zoomFactor) {
        this.zoomFactor = zoomFactor;
    }

    /**
     * Gets the pan offset.
     * @return the offset as Point2D.Double
     */
    public Point2D.Double getOffset() {
        return offset;
    }

    /**
     * Gets the current drag start point.
     * @return the drag start point, or null if not dragging
     */
    public Point getDragStartPoint() {
        return dragStartPoint;
    }

    /**
     * this implements the end of an image drag action
     */
    public void dragEnded() {
        if ((this.diff.getX() != 0) && (this.diff.getY() != 0)) {               // if we had a drag interaction, we keep the image offset so it does not jump back to its initial position
            this.offset.setLocation(this.offset.getX() + this.diff.getX(), this.offset.getY() + this.diff.getY());
        }
        this.dragStartPoint = null;
    }

    /**
     * converts a screenspace point position to the transformed pixel position on the image
     * @param point
     * @return
     */
    public Point getPixelPosition(Point point) {
        double x = point.getX() * this.inverseAffineTransform.getScaleX() + this.inverseAffineTransform.getTranslateX();
        double y = point.getY() * this.inverseAffineTransform.getScaleY() + this.inverseAffineTransform.getTranslateY();
        return new Point((int)x, (int)y);
    }
}
