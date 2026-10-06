package mpmToolbox.gui.score.interaction;

import com.alee.extended.button.WebSplitButton;
import com.alee.laf.menu.PopupMenuWay;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import mpmToolbox.gui.Settings;
import mpmToolbox.gui.score.ScoreDisplayPanel;
import mpmToolbox.gui.score.ScoreDocumentData;
import mpmToolbox.gui.mpmTree.MpmTreeNode;
import mpmToolbox.projectData.score.ScoreNode;
import mpmToolbox.projectData.score.ScorePage;
import mpmToolbox.supplementary.Tools;
import mpmToolbox.supplementary.orthantNeighborhoodGraph.ONGNode;
import nu.xom.Element;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;

/**
 * * Manages all interaction modes for the score display and generates their UI.
 * Holds a LinkedList of instantiated InteractionMode objects and provides methods
 * to switch between modes and generate menu items dynamically.
 * Acts as a central dispatcher for mouse and keyboard input events to the current mode.
 */
public class InteractionModeManager implements MouseInput, KeyInput {

    private final LinkedList<AbstractInteractionMode> modes = new LinkedList<>();
    private AbstractInteractionMode currentMode;
    private final ScoreDisplayPanel panel;
    private WebSplitButton modeButton;
    private final AnchorNodeHelper anchorNodeHelper;
    private final ScoreNoteMultiselectHelper noteMultiselect;

    /**
     * Creates the InteractionModeManager and instantiates all four interaction modes.
     *
     * @param panel        the score display panel
     * @param scoreDocData the score document data holder
     */
    public InteractionModeManager(ScoreDisplayPanel panel, ScoreDocumentData scoreDocData) {
        this.panel = panel;
        this.anchorNodeHelper = new AnchorNodeHelper(panel);
        this.noteMultiselect = new ScoreNoteMultiselectHelper(scoreDocData.getProjectPane().getMsmTree());
        initializeModes();
        this.currentMode = modes.getFirst();
    }

    /**
     * Initializes all interaction modes and adds them to the LinkedList.
     */
    private void initializeModes() {
        modes.add(new PanAndZoomInteractionMode(panel));
        modes.add(new MarkNotesInteractionMode(panel));
        modes.add(new EditPerformanceInteractionMode(panel));
        modes.add(new AddSvgLinkInteractionMode(panel));
        modes.add(new SelectEditInteractionMode(panel));
    }

    /**
     * Sets the current interaction mode by index in the modes list.
     *
     * @param index the index of the mode to activate
     */
    public void setCurrentMode(int index) {
        if (index >= 0 && index < modes.size()) {
            setCurrentMode(modes.get(index));
        }
    }

    /**
     * Sets the current interaction mode by mode object reference.
     *
     * @param mode the AbstractInteractionMode to activate
     */
    public void setCurrentMode(AbstractInteractionMode mode) {
        if (modes.contains(mode)) {
            if (this.currentMode instanceof AddSvgLinkInteractionMode) {
                ((AddSvgLinkInteractionMode) this.currentMode).clearHoveredSvgElements();
            }
            this.currentMode = mode;
            panel.onInteractionModeChange();
            getAnchorNodeHelper().reset();
            updateModeButtonUI(mode);
            mode.performSetup();
        }
    }

    /**
     * returns the current mode
     * @return the current mode
     */
    public AbstractInteractionMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Generates the popup menu from all modes' captions.
     * This creates WebMenuItems dynamically from the LinkedList of modes.
     *
     * @return the populated WebPopupMenu
     */
    public WebPopupMenu generatePopupMenu() {
        WebPopupMenu popupMenu = new WebPopupMenu();

        int index = 0;
        for (AbstractInteractionMode mode : modes) {
            WebMenuItem menuItem = new WebMenuItem(mode.getCaption());
            final int modeIndex = index;

            menuItem.addActionListener(actionEvent -> {
                setCurrentMode(modeIndex);
            });

            menuItem.setToolTipText(mode.getToolTip());
            popupMenu.add(menuItem);
            index++;
        }

        return popupMenu;
    }

    /**
     * Updates the visual state of the mode button (label and foreground color).
     *
     * @param mode the AbstractInteractionMode to reflect in the UI
     */
    private void updateModeButtonUI(AbstractInteractionMode mode) {
        if (modeButton == null) return;

        modeButton.setText(mode.getCaption());
        modeButton.setForeground(mode.getColor());
    }

    /**
     * Sets the button reference for UI updates.
     * Must be called after the button is created to enable updateModeButtonUI().
     *
     * @param modeButton the WebSplitButton to use for mode switching
     */
    public void setModeButton(WebSplitButton modeButton) {
        this.modeButton = modeButton;
        if (modeButton != null) {
            // Configure the button
            modeButton.setPopupMenu(generatePopupMenu());
            modeButton.setPopupMenuWay(PopupMenuWay.aboveEnd);
            modeButton.setPadding(Settings.paddingInDialogs);
            modeButton.setToolTip("select interaction mode");

            // Set initial mode
            setCurrentMode(0);
        }
    }

    /**
     * Gets the LinkedList of all managed interaction modes.
     *
     * @return the modes LinkedList
     */
    public LinkedList<AbstractInteractionMode> getModes() {
        return modes;
    }

    /**
     * Gets the AnchorNodeHelper managed by this manager.
     *
     * @return the AnchorNodeHelper
     */
    public AnchorNodeHelper getAnchorNodeHelper() {
        return anchorNodeHelper;
    }

    /**
     * Gets the ScoreNoteMultiselectHelper managed by this manager.
     *
     * @return the ScoreNoteMultiselectHelper
     */
    public ScoreNoteMultiselectHelper getNoteMultiselect() {
        return noteMultiselect;
    }

    /**
     * Draws all overlay rendering for the current interaction mode.
     *
     * @param g2 the Graphics2D context for drawing
     */
    public void draw(Graphics2D g2) {
        if ((this.currentMode == null) || this.panel.isOverlayHidden()) {
            return;
        }

        this.currentMode.drawModeSpecificOverlay(g2);
    }

    // ======================== MouseInput Implementation ========================

    /**
     * Delegates mouse click events to the current mode.
     *
     * @param mouseEvent the click event
     */
    @Override
    public void mouseClicked(MouseEvent mouseEvent) {
        if (currentMode != null) {
            currentMode.mouseClicked(mouseEvent);
        }
    }

    /**
     * Delegates mouse press events to the current mode.
     *
     * @param mouseEvent the press event
     */
    @Override
    public void mousePressed(MouseEvent mouseEvent) {
        if (currentMode != null) {
            currentMode.mousePressed(mouseEvent);
        }
    }

    /**
     * Delegates mouse release events to the current mode.
     *
     * @param mouseEvent the release event
     */
    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        if (currentMode != null) {
            currentMode.mouseReleased(mouseEvent);
        }
    }

    /**
     * Delegates mouse enter events to the current mode.
     *
     * @param mouseEvent the enter event
     */
    @Override
    public void mouseEntered(MouseEvent mouseEvent) {
        if (currentMode != null) {
            currentMode.mouseEntered(mouseEvent);
        }
    }

    /**
     * Delegates mouse exit events to the current mode.
     *
     * @param mouseEvent the exit event
     */
    @Override
    public void mouseExited(MouseEvent mouseEvent) {
        if (currentMode != null) {
            currentMode.mouseExited(mouseEvent);
        }
    }

    /**
     * Delegates mouse drag events to the current mode.
     *
     * @param mouseEvent the drag event
     */
    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        if (currentMode != null) {
            currentMode.mouseDragged(mouseEvent);
        }
    }

    /**
     * Delegates mouse move events to the current mode.
     *
     * @param mouseEvent the move event
     */
    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
        if (currentMode != null) {
            currentMode.mouseMoved(mouseEvent);
        }
    }

    /**
     * Delegates mouse wheel events to the current mode.
     *
     * @param mouseWheelEvent the wheel event
     */
    @Override
    public void mouseWheelMoved(MouseWheelEvent mouseWheelEvent) {
        if (currentMode != null) {
            currentMode.mouseWheelMoved(mouseWheelEvent);
        }
    }

    // ======================== KeyInput Implementation ========================

    /**
     * Delegates key typed events to the current mode.
     *
     * @param keyEvent the typed key event
     */
    @Override
    public void keyTyped(KeyEvent keyEvent) {
        if (currentMode != null) {
            currentMode.keyTyped(keyEvent);
        }
    }

    /**
     * Delegates key pressed events to the current mode.
     *
     * @param keyEvent the pressed key event
     */
    @Override
    public void keyPressed(KeyEvent keyEvent) {
        if (currentMode != null) {
            currentMode.keyPressed(keyEvent);
        }
    }

    /**
     * Delegates key released events to the current mode.
     *
     * @param keyEvent the released key event
     */
    @Override
    public void keyReleased(KeyEvent keyEvent) {
        if (currentMode != null) {
            currentMode.keyReleased(keyEvent);
        }
    }
}
