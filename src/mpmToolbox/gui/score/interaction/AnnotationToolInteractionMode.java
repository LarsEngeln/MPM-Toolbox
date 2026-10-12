package mpmToolbox.gui.score.interaction;

import mpmToolbox.gui.annotations.AnnotationManager;
import mpmToolbox.gui.annotations.AnnotationOverlayPanel;
import mpmToolbox.gui.score.ScoreDisplayPanel;
import mpmToolbox.projectData.annotations.AnnotationData;
import mpmToolbox.projectData.annotations.AnnotationPlacement;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Tool mode for adding/editing/moving project annotations on score pages.
 */
public class AnnotationToolInteractionMode extends AbstractInteractionMode {
    private AnnotationData draggedAnnotation = null;
    private AnnotationPlacement draggedPlacement = null;
    private double dragOffsetX = 0.0;
    private double dragOffsetY = 0.0;

    public AnnotationToolInteractionMode(ScoreDisplayPanel panel) {
        super(panel, "Add Annotation", "add and edit annotations", Color.YELLOW.darker());
    }

    private AnnotationManager annotations() {
        return this.panel.getScoreDocumentData().getProjectPane().getAnnotationManager();
    }

    @Override
    public void mousePressed(MouseEvent mouseEvent) {
        if (mouseEvent.getButton() != MouseEvent.BUTTON1) {
            return;
        }
        if (mouseEvent.isControlDown()) {
            return;
        }
        updateMousePosition(mouseEvent);
        if (this.panel.getMousePositionInImage() == null) {
            return;
        }
        double x = this.panel.getMousePositionInImage().x;
        double y = this.panel.getMousePositionInImage().y;
        double maxDistSquared = Math.pow(Math.max(8.0, this.panel.getOverlayXWidth()), 2.0);
        this.draggedAnnotation = this.panel.findAnnotationAtScorePosition(x, y, maxDistSquared);
        this.draggedPlacement = this.panel.findNearestScorePlacement(this.draggedAnnotation, x, y, maxDistSquared);
        if (this.draggedPlacement != null) {
            this.dragOffsetX = x - this.draggedPlacement.getX();
            this.dragOffsetY = y - this.draggedPlacement.getY();
            this.annotations().setSelected(this.draggedAnnotation);
            this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        } else {
            this.annotations().setSelected(null);
        }
    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        if ((this.draggedAnnotation == null) || (this.draggedPlacement == null)) {
            handlePanDrag(mouseEvent);
            return;
        }
        updateMousePosition(mouseEvent);
        if (this.panel.getMousePositionInImage() == null) {
            return;
        }
        this.draggedPlacement.setX(this.panel.getMousePositionInImage().x - this.dragOffsetX);
        this.draggedPlacement.setY(this.panel.getMousePositionInImage().y - this.dragOffsetY);
        this.draggedAnnotation.touch();
        this.annotations().notifyChanged();
        this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        if (handlePanOrSelectRelease(mouseEvent)) {
            this.draggedAnnotation = null;
            this.draggedPlacement = null;
            this.dragOffsetX = 0.0;
            this.dragOffsetY = 0.0;
            return;
        }
        if ((mouseEvent.getButton() == MouseEvent.BUTTON1) && (this.draggedPlacement == null)) {
            updateMousePosition(mouseEvent);
            if (this.panel.getMousePositionInImage() == null) {
                return;
            }
            AnnotationData newAnnotation = this.annotations().create(AnnotationPlacement.TabType.SCORE,
                    this.panel.getMousePositionInImage().x,
                    this.panel.getMousePositionInImage().y,
                    this.panel.getScorePage().getFile().getAbsolutePath());
            this.annotations().setSelected(newAnnotation);
        }
        this.draggedAnnotation = null;
        this.draggedPlacement = null;
        this.dragOffsetX = 0.0;
        this.dragOffsetY = 0.0;
        this.panel.setCursor(Cursor.getDefaultCursor());
    }

    @Override
    public void mouseClicked(MouseEvent mouseEvent) {
        if ((mouseEvent.getButton() != MouseEvent.BUTTON1) || (mouseEvent.getClickCount() < 2)) {
            return;
        }
        updateMousePosition(mouseEvent);
        if (this.panel.getMousePositionInImage() == null) {
            return;
        }
        double x = this.panel.getMousePositionInImage().x;
        double y = this.panel.getMousePositionInImage().y;
        double maxDistSquared = Math.pow(Math.max(8.0, this.panel.getOverlayXWidth()), 2.0);
        AnnotationData annotation = this.panel.findAnnotationAtScorePosition(x, y, maxDistSquared);
        if (annotation == null) {
            return;
        }
        if (AnnotationOverlayPanel.editText(this.panel, annotation)) {
            this.annotations().notifyChanged();
            this.annotations().setSelected(annotation);
        }
    }

    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
        updateMousePosition(mouseEvent);
        if (this.panel.getMousePositionInImage() == null) {
            this.annotations().setHovered(null);
            this.panel.setCursor(Cursor.getDefaultCursor());
            return;
        }
        double x = this.panel.getMousePositionInImage().x;
        double y = this.panel.getMousePositionInImage().y;
        double maxDistSquared = Math.pow(Math.max(8.0, this.panel.getOverlayXWidth()) * 2.0, 2.0);
        AnnotationData hovered = this.panel.findAnnotationAtScorePosition(x, y, maxDistSquared);
        this.annotations().setHovered(hovered);
        this.panel.setCursor((hovered == null) ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    protected void clearStates() {
        super.clearStates();
        this.draggedAnnotation = null;
        this.draggedPlacement = null;
        this.dragOffsetX = 0.0;
        this.dragOffsetY = 0.0;
        this.annotations().setHovered(null);
    }
}
