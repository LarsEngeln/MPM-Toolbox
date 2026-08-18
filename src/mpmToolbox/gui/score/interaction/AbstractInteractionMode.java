package mpmToolbox.gui.score.interaction;

import mpmToolbox.gui.Settings;
import mpmToolbox.gui.mpmTree.MpmTreeNode;
import mpmToolbox.gui.score.ScoreDisplayPanel;
import mpmToolbox.projectData.score.ScoreNode;
import nu.xom.Element;
import meico.mpm.elements.Performance;
import mpmToolbox.supplementary.Tools;
import mpmToolbox.supplementary.orthantNeighborhoodGraph.ONGNode;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;

/**
 * Shared base for score interaction modes.
 */
public abstract class AbstractInteractionMode implements MouseInput, KeyInput {
    protected final ScoreDisplayPanel panel;
    protected final String caption;
    protected final String toolTip;
    protected final java.awt.Color color;

    /**
     * Creates a shared interaction-mode helper for the given panel.
     * @param panel the score panel that receives the interaction
     * @param caption the display caption for this mode
     * @param toolTip the tooltip text for this mode
     * @param color the UI foreground color for this mode
     */
    AbstractInteractionMode(ScoreDisplayPanel panel, String caption, String toolTip, java.awt.Color color) {
        this.panel = panel;
        this.caption = caption;
        this.toolTip = toolTip;
        this.color = color;
    }

    /**
     * Gets the display caption for this interaction mode.
     * @return the mode caption
     */
    public String getCaption() {
        return caption;
    }

    /**
     * Gets the tooltip text for this interaction mode.
     * @return the mode tooltip
     */
    public String getToolTip() {
        return toolTip;
    }

    /**
     * Gets the UI foreground color for this interaction mode.
     * @return the mode color
     */
    public java.awt.Color getColor() {
        return color;
    }

    /**
     * Handles a mouse click when a mode does not need special click logic.
     * @param mouseEvent the click event
     */
    @Override
    public void mouseClicked(MouseEvent mouseEvent) {
    }

    /**
     * Handles mouse press events when a mode does not need custom press logic.
     * @param mouseEvent the press event
     */
    @Override
    public void mousePressed(MouseEvent mouseEvent) {
    }

    /**
     * Handles mouse release events when a mode does not need custom release logic.
     * @param mouseEvent the release event
     */
    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
    }

    /**
     * Handles mouse enter events when a mode does not need custom enter logic.
     * @param mouseEvent the enter event
     */
    @Override
    public void mouseEntered(MouseEvent mouseEvent) {
    }

    /**
     * Handles mouse exit events when a mode does not need custom exit logic.
     * @param mouseEvent the exit event
     */
    @Override
    public void mouseExited(MouseEvent mouseEvent) {
    }

    /**
     * Handles drag events when a mode does not need custom drag logic.
     * @param mouseEvent the drag event
     */
    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
    }

    /**
     * Handles move events when a mode does not need custom move logic.
     * @param mouseEvent the move event
     */
    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
    }

    /**
     * Applies the shared zoom behavior used by every interaction mode.
     * @param mouseWheelEvent the wheel event
     */
    @Override
    public void mouseWheelMoved(MouseWheelEvent mouseWheelEvent) {
        this.panel.getPanZoomHelper().mouseWheelMoved(mouseWheelEvent);
    }

    /**
     * Indicates whether the given overlay element should be skipped while rendering.
     * @param element the overlay element
     * @return true if the element should not be drawn
     */
    public boolean shouldSkipOverlayElement(Element element) {
        return false;
    }

    /**
     * Handles typed keys when a mode does not need custom key handling.
     * @param keyEvent the typed key event
     */
    @Override
    public void keyTyped(KeyEvent keyEvent) {
    }

    /**
     * Handles pressed keys when a mode does not need custom key handling.
     * @param keyEvent the pressed key event
     */
    @Override
    public void keyPressed(KeyEvent keyEvent) {
    }

    /**
     * Handles released keys with the shared page-navigation behavior.
     * @param keyEvent the released key event
     */
    @Override
    public void keyReleased(KeyEvent keyEvent) {
        switch (keyEvent.getKeyCode()) {
            case KeyEvent.VK_LEFT:
                this.panel.previousPage();
                break;
            case KeyEvent.VK_RIGHT:
                this.panel.nextPage();
                break;
            default:
                break;
        }
    }

    /**
     * Ends a drag gesture or delegates to the shared click-selection routine.
     * @param mouseEvent the release event
     * @return true if the gesture was consumed
     */
    protected boolean handlePanOrSelectRelease(MouseEvent mouseEvent) {
        if (mouseEvent.isControlDown() || (this.panel.getPanZoomHelper().getDragStartPoint() != null)) {
            this.panel.dragOrSelectGesture(mouseEvent);
            return true;
        }
        return false;
    }

    /**
     * Begins or continues a pan gesture.
     * @param mouseEvent the drag event
     */
    protected void handlePanDrag(MouseEvent mouseEvent) {
        this.panel.setMousePositionInImage(null);
        this.panel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        this.panel.dragImage(mouseEvent.getLocationOnScreen());
    }

    /**
     * Clears state that should not survive leaving the panel.
     */
    protected void clearTransientState() {
        this.panel.setMousePositionInImage(null);
        this.panel.repaint();
    }

    /**
     * Updates the mouse position in image coordinates.
     * @param mouseEvent the source event
     */
    protected void updateMousePosition(MouseEvent mouseEvent) {
        this.panel.setMousePositionInImage(this.panel.mouse2PixelPosition(mouseEvent));
    }

    /**
     * Checks whether the pointer is currently above an overlay element.
     * @param mouseEvent the current mouse event
     * @return true if an overlay element is under the pointer
     */
    protected boolean hasOverlayElement(MouseEvent mouseEvent) {
        return this.panel.getOverlayElementAt(mouseEvent) != null;
    }

    /**
     * Sets a hover cursor with the given AWT cursor type.
     * @param cursorType the cursor type constant
     */
    protected void setHoverCursor(int cursorType) {
        this.panel.setCursor(new Cursor(cursorType));
    }

    /**
     * Performs any mode-specific setup when this mode is activated.
     * Subclasses can override this to perform setup actions like tree navigation.
     */
    public void performSetup() {
        // Default: no setup needed
    }

    /**
     * Draws mode-specific overlay preview graphics on the score.
     * Called during the rendering loop to allow modes to draw hover feedback or drag previews.
     * Subclasses can override this to provide mode-specific drawing logic.
     * @param g2 the Graphics2D context for drawing
     */
    public void drawModeSpecificOverlay(Graphics2D g2) {
        // Default: no mode-specific drawing
    }
}
