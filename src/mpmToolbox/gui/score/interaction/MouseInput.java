package mpmToolbox.gui.score.interaction;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public interface MouseInput {
    void mouseClicked(MouseEvent mouseEvent);
    void mousePressed(MouseEvent mouseEvent);
    void mouseReleased(MouseEvent mouseEvent);
    void mouseEntered(MouseEvent mouseEvent);
    void mouseExited(MouseEvent mouseEvent);
    void mouseDragged(MouseEvent mouseEvent);
    void mouseMoved(MouseEvent mouseEvent);
    void mouseWheelMoved(MouseWheelEvent mouseWheelEvent);
}
