package mpmToolbox.gui.score.interaction;

import java.awt.event.KeyEvent;

public interface KeyInput {
    void keyTyped(KeyEvent keyEvent);
    void keyPressed(KeyEvent keyEvent);
    void keyReleased(KeyEvent keyEvent);
}
