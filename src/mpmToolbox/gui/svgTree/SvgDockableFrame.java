package mpmToolbox.gui.svgTree;

import com.alee.api.data.CompassDirection;
import com.alee.extended.dock.WebDockableFrame;
import com.alee.laf.label.WebLabel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.managers.icon.Icons;
import com.alee.managers.style.StyleId;
import mpmToolbox.gui.ProjectPane;
import mpmToolbox.projectData.SvgData;

import java.awt.*;

/**
 * Dockable frame on the east side that shows one SVG tree per loaded SVG file.
 * Multiple SVGs are displayed as tabs inside this frame.
 *
 * @author Lars Engeln
 */
public class SvgDockableFrame extends WebDockableFrame {

    private final ProjectPane parent;
    private final WebTabbedPane tabs = new WebTabbedPane();
    private final WebLabel placeholder;

    /**
     * Constructor.
     * @param parent the owning ProjectPane
     */
    public SvgDockableFrame(ProjectPane parent) {
        super("svgFrame", "Scalable Vector Graphics");
        this.parent = parent;

        this.setIcon(Icons.table);
        this.setClosable(false);
        this.setMaximizable(false);
        this.setPosition(CompassDirection.east);

        this.placeholder = new WebLabel("Drop an SVG file.", WebLabel.CENTER);

        if (this.parent.getProjectData().getSvgs().isEmpty()) {
            this.add(this.placeholder);
            this.minimize();
        } else {
            // add a tab for each already-loaded SVG (e.g. from project file)
            for (SvgData svg : this.parent.getProjectData().getSvgs()) {
                this.addTab(svg);
            }
            this.add(this.tabs);
        }
    }

    /**
     * Add a new SVG and show it in a new tab.
     * @param svg the SVG data to add
     */
    public synchronized void addSvg(SvgData svg) {
        if (this.parent.getProjectData().getSvgs().size() == 1) {
            // first SVG: replace placeholder with tabs
            this.remove(this.placeholder);
            this.add(this.tabs);
        }
        this.addTab(svg);
        this.restore();
        this.validate();
        this.repaint();
    }

    /** Remove the SVG tab at the given index. */
    public synchronized void removeSvg(int index) {
        if (index < 0 || index >= this.tabs.getTabCount())
            return;
        this.tabs.removeTabAt(index);
        if (this.tabs.getTabCount() == 0) {
            this.remove(this.tabs);
            this.add(this.placeholder);
        }
        this.validate();
        this.repaint();
    }

    /** Returns the currently selected SvgTree, or null if none. */
    public SvgTree getSelectedSvgTree() {
        Component c = this.tabs.getSelectedComponent();
        if (c instanceof WebScrollPane) {
            Component view = ((WebScrollPane) c).getViewport().getView();
            if (view instanceof SvgTree)
                return (SvgTree) view;
        }
        return null;
    }

    /**
     * Returns the SvgTree that displays the given SvgData, or null if not found.
     */
    public SvgTree getTreeForSvg(SvgData svg) {
        for (int i = 0; i < this.tabs.getTabCount(); i++) {
            Component c = this.tabs.getComponentAt(i);
            if (c instanceof WebScrollPane) {
                Component view = ((WebScrollPane) c).getViewport().getView();
                if (view instanceof SvgTree && ((SvgTree) view).getSvgData() == svg)
                    return (SvgTree) view;
            }
        }
        return null;
    }

    /**
     * Brings the tab for the given SvgData to the front.
     */
    public void showTabForSvg(SvgData svg) {
        for (int i = 0; i < this.tabs.getTabCount(); i++) {
            Component c = this.tabs.getComponentAt(i);
            if (c instanceof WebScrollPane) {
                Component view = ((WebScrollPane) c).getViewport().getView();
                if (view instanceof SvgTree && ((SvgTree) view).getSvgData() == svg) {
                    this.tabs.setSelectedIndex(i);
                    return;
                }
            }
        }
    }

    // ---- private helpers ----

    private void addTab(SvgData svg) {
        SvgTree tree = new SvgTree(svg, this.parent);
        WebScrollPane scroll = new WebScrollPane(tree);
        scroll.setStyleId(StyleId.scrollpaneUndecoratedButtonless);
        this.tabs.addTab(svg.getName(), scroll);
        this.tabs.setSelectedIndex(this.tabs.getTabCount() - 1);
    }
}



