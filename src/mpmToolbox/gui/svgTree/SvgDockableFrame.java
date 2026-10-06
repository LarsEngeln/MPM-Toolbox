package mpmToolbox.gui.svgTree;

import com.alee.api.data.CompassDirection;
import com.alee.extended.dock.WebDockableFrame;
import com.alee.laf.label.WebLabel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.managers.icon.Icons;
import mpmToolbox.gui.ProjectPane;
import mpmToolbox.projectData.SvgData;
import mpmToolbox.projectData.score.ScorePage;

import java.awt.BorderLayout;
import javax.swing.*;

/**
 * Dockable frame on the east side that shows the SVG tree for the current score page.
 *
 * @author Lars Engeln
 */
public class SvgDockableFrame extends WebDockableFrame {

    private final ProjectPane parent;
    private final JPanel contentPanel;
    private final WebLabel placeholder;
    private SvgTree svgTree;

    /**
     * Constructor.
     * @param parent the owning ProjectPane
     */
    public SvgDockableFrame(ProjectPane parent) {
        super("svgFrame", "Scalable Vector Graphics");
        this.parent = parent;

        this.setTitle("Scalable Vector Graphics");
        this.setIcon(Icons.table);
        this.setClosable(false);
        this.setMaximizable(false);
        this.setPosition(CompassDirection.east);

        this.contentPanel = new JPanel(new BorderLayout());
        this.add(this.contentPanel);
        this.placeholder = new WebLabel("Drop an SVG file.", WebLabel.CENTER);
        this.refreshForCurrentPage();
    }

    /**
     * Refresh the SVG view for the current score page.
     */
    public synchronized void refreshForCurrentPage() {
        this.showSvgsForPage(this.parent.getCurrentScorePage(), null);
    }

    /**
     * Refresh the SVG view for the given score page.
     * @param scorePage the page to show
     */
    public synchronized void showSvgsForPage(ScorePage scorePage) {
        this.showSvgsForPage(scorePage, null);
    }

    /**
     * Refresh the SVG view for the given score page and try to select a specific SVG element.
     * @param scorePage the page to show
     * @param selectedSvg SVG to select after rebuilding, or null
     */
    public synchronized void showSvgsForPage(ScorePage scorePage, SvgData selectedSvg) {
        this.contentPanel.removeAll();

        SvgData svg = (scorePage == null) ? null : scorePage.getSvg();
        if (svg == null) {
            this.svgTree = null;
            this.contentPanel.add(this.placeholder, BorderLayout.CENTER);
            this.restore();
            this.contentPanel.revalidate();
            this.validate();
            this.repaint();
            return;
        }

        this.svgTree = new SvgTree(svg, this.parent);
        WebScrollPane scroll = this.createScrollPane(this.svgTree);
        this.contentPanel.add(scroll, BorderLayout.CENTER);

        if (selectedSvg != null && selectedSvg == svg && selectedSvg.getHighlightedElement() != null) {
            this.svgTree.selectNodeForElement(selectedSvg.getHighlightedElement());
        }
        this.restore();
        this.contentPanel.revalidate();
        this.validate();
        this.repaint();
    }

    /**
     * Returns the currently displayed SvgTree, or null if none.
     */
    public SvgTree getSvgTree() {
        return this.svgTree;
    }

    /**
     * Creates the scrollable pane where the SvgTree tree lives in
     * @param tree SvgTree to be displayed
     * @return scrollable pane
     */
    private WebScrollPane createScrollPane(SvgTree tree) {
        WebScrollPane scroll = new WebScrollPane(tree);
        scroll.setStyleId(com.alee.managers.style.StyleId.scrollpaneUndecoratedButtonless);
        return scroll;
    }
}
