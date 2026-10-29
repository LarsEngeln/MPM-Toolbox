package mpmToolbox.gui;

import com.alee.api.data.CompassDirection;
import com.alee.extended.dock.SidebarButtonVisibility;
import com.alee.extended.dock.WebDockableFrame;
import com.alee.extended.dock.WebDockablePane;
import com.alee.extended.tab.DocumentData;
import com.alee.extended.tab.WebDocumentPane;
import javax.swing.SwingConstants;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.panel.WebPanel;
import com.alee.managers.style.StyleId;
import meico.mei.Mei;
import meico.midi.Midi;
import meico.mpm.Mpm;
import meico.msm.Msm;
import mpmToolbox.projectData.audio.Audio;
import mpmToolbox.projectData.ProjectData;
import mpmToolbox.gui.audio.MeasurementData;
import mpmToolbox.gui.audio.AudioDocumentData;
import mpmToolbox.gui.analytics.AnalyticsDocumentData;
import mpmToolbox.gui.annotations.AnnotationDockableFrame;
import mpmToolbox.gui.annotations.AnnotationManager;
import mpmToolbox.gui.audio.utilities.CsvImportDialog;
import mpmToolbox.gui.mpmTree.MpmDockableFrame;
import mpmToolbox.gui.mpmTree.MpmTree;
import mpmToolbox.gui.mpmTree.MpmTreeNode;
import mpmToolbox.gui.msmTree.MsmTree;
import mpmToolbox.gui.msmTree.MsmTreeNode;
import mpmToolbox.gui.svgTree.SvgDockableFrame;
import mpmToolbox.projectData.SvgData;
import mpmToolbox.projectData.score.Score;
import mpmToolbox.gui.score.ScoreDisplayPanel;
import mpmToolbox.gui.score.ScoreDocumentData;
import mpmToolbox.projectData.score.ScorePage;
import mpmToolbox.gui.syncPlayer.SyncPlayer;
import com.alee.extended.dock.data.DockableContainer;
import com.alee.extended.tab.DocumentPaneState;
import com.alee.utils.XmlUtils;
import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.ParsingException;
import nu.xom.Serializer;
import org.xml.sax.SAXException;

import javax.sound.midi.MidiUnavailableException;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * A GUI pane wrapper class around ProjectData.
 * @author Axel Berndt
 */
public class ProjectPane extends WebDockablePane {
    private final MpmToolbox parent;

    private ProjectData data;                                                         // the actual project data

    private final WebDocumentPane<DocumentData<WebPanel>> tabs = new WebDocumentPane<>();   // this contains the components displayed in the center under certain tabs

    private final MsmTree msmTree;
    private final MpmDockableFrame mpmDockableFrame;
    private final SvgDockableFrame svgDockableFrame;
    private final AnnotationManager annotationManager = new AnnotationManager(this);   // manages the annotations; must exist before any view that listens to it is created
    private final AnnotationDockableFrame annotationDockableFrame;
    private final WebDockableFrame playerFrame = new WebDockableFrame("playerFrame", "Sync Player");
    private SyncPlayer syncPlayer = null;
    private ScoreDocumentData scoreFrame = null;
    private AudioDocumentData audioFrame = null;
    private AnalyticsDocumentData analyticsFrame = null;

    /**
     * constructor
     * @param msm
     * @param parent
     */
    public ProjectPane(Msm msm, MpmToolbox parent) {
        super();
        this.parent = parent;
        this.data = new ProjectData(msm);
        this.msmTree = new MsmTree(this);
        this.mpmDockableFrame = new MpmDockableFrame(this);
        this.svgDockableFrame = new SvgDockableFrame(this);
        this.annotationDockableFrame = new AnnotationDockableFrame(this);
        this.makeGUI();
    }

    /**
     * constructor, the MIDI input is converted to MSM
     * @param midi
     * @param parent
     */
    public ProjectPane(Midi midi, MpmToolbox parent) {
        super();
        this.parent = parent;
        this.data = new ProjectData(midi.exportMsm());
        this.msmTree = new MsmTree(this);
        this.mpmDockableFrame = new MpmDockableFrame(this);
        this.svgDockableFrame = new SvgDockableFrame(this);
        this.annotationDockableFrame = new AnnotationDockableFrame(this);
        this.makeGUI();
    }

    /**
     * constructor, the MEI input is converted to MSM and MPM;
     * if the MEI has more than one mdiv, only the first is converted to MSM and MPM;
     * use mei.exportMsmMpm() directly and choose the desired MSM from the output to instantiate a ProgramData object.
     * @param mei
     * @param parent
     */
    public ProjectPane(Mei mei, MpmToolbox parent) {
        super();
        this.parent = parent;
        this.data = new ProjectData(mei);
        this.msmTree = new MsmTree(this);
        this.mpmDockableFrame = new MpmDockableFrame(this);
        this.svgDockableFrame = new SvgDockableFrame(this);
        this.annotationDockableFrame = new AnnotationDockableFrame(this);
        this.makeGUI();
    }

    /**
     * constructor, instantiate a project from a project file
     * @param file
     * @param parent
     * @throws SAXException
     * @throws ParsingException
     * @throws ParserConfigurationException
     * @throws IOException
     */
    public ProjectPane(File file, MpmToolbox parent) throws SAXException, ParsingException, ParserConfigurationException, IOException {
        super();
        this.parent = parent;

        byte[] emergencyMemory = new byte[10 * 1024 * 1024];
        try {
            Runtime rt = Runtime.getRuntime();

            long max = rt.maxMemory();
            long total = rt.totalMemory();
            long free = rt.freeMemory();

            System.out.printf("Max Heap: %.2f MB%n", max / 1024.0 / 1024.0);
            System.out.printf("Allocated Heap: %.2f MB%n", total / 1024.0 / 1024.0);
            System.out.printf("Free Heap: %.2f MB%n", free / 1024.0 / 1024.0);

            this.data = new ProjectData(file);
        } catch (OutOfMemoryError e) {
            emergencyMemory = null;
            System.gc();

            // Show error message
            WebOptionPane.showMessageDialog(
                    this,
                    "The project requires more memory than is available. "
                            + "Please increase the JVM memory settings or load a smaller project.",
                    "Out of Memory",
                    WebOptionPane.ERROR_MESSAGE
            );

            e.printStackTrace();
        }
        this.msmTree = new MsmTree(this);
        this.mpmDockableFrame = new MpmDockableFrame(this);
        this.svgDockableFrame = new SvgDockableFrame(this);
        this.annotationDockableFrame = new AnnotationDockableFrame(this);
        this.makeGUI();
    }

    /**
     * a getter for the parent MPM Toolbox
     * @return
     */
    public MpmToolbox getParentMpmToolbox() {
        return this.parent;
    }

    /**
     * a helper method for the constructors to generate the GUI
     */
    private void makeGUI() {
//        this.registerSettings(new Configuration("MyDockablePane", "state"));    // save (MyDockablePane.xml in user home directory if not set different) and reproduce the state on restart (https://github.com/mgarin/weblaf/wiki/How-to-use-WebDockablePane#saverestore-state)
        this.setStyleId(StyleId.dockablepaneCompact);                       // a more compact styling of the dockable pane elements
        this.setSidebarButtonVisibility(SidebarButtonVisibility.always);

        this.makePlayerFrame();                                             // the Sync Player in a dockable pane

        this.addFrame(this.msmTree.getDockableFrame());                     // add the MSM tree to the UI
        this.addFrame(this.annotationDockableFrame);                        // project annotations panel
        this.addFrame(this.mpmDockableFrame);                               // the MPM tree is displayed in a dockable pane
        this.addFrame(this.svgDockableFrame);                               // the SVG tree is displayed in a dockable pane

        // fill the content pane in the center
//        this.tabs.openDocument(new DocumentData<>("TestTab", "Test Tab", new WebButton("Test")));
        this.tabs.setSplitPaneCustomizer(splitPane -> {     // make the dividers of split tab frames adjustable
            splitPane.setDividerSize(6);
            splitPane.setContinuousLayout(true);
            splitPane.setResizeWeight(0.5);
            splitPane.setMinimumSize(new java.awt.Dimension(0, 0));
        });
        this.tabs.setTabbedPaneCustomizer(tabbedPane -> tabbedPane.setMinimumSize(new java.awt.Dimension(0, 0)));  // otherwise the content's minimum size blocks the divider
        this.tabs.setSplitEnabled(true);                    // tabs can be dragged to the edge of a frame to arrange them side by side or above each other
        this.tabs.setDragBetweenPanesEnabled(true);         // and moved between frames
        this.tabs.openDocument(this.makeScoreFrame());
        this.tabs.openDocument(this.makeAudioFrame());
        this.tabs.openDocument(this.makeAnalyticsFrame());
        this.tabs.setSelected(this.scoreFrame);

        this.setContent(this.tabs);     // this will fill the free space of the docking pane that is not occupied by a WebDockableFrame, this can be anything JComponent-based
    }

    /**
     * Arrange the Score, Audio and Analytics tabs.
     * @param direction null for all tabs in one frame, SwingConstants.RIGHT for side by side, SwingConstants.BOTTOM for above each other
     */
    public void arrangeTabs(Integer direction) {
        this.tabs.setStructureRoot(null);                   // new empty root pane
        this.tabs.openDocument(this.scoreFrame);
        this.tabs.openDocument(this.audioFrame);
        this.tabs.openDocument(this.analyticsFrame);
        if (direction != null) {
            this.tabs.split(this.analyticsFrame, direction);
            // splitting in the opposite direction creates a nested split; the same direction would just move the tab into the neighbouring frame
            this.tabs.split(this.scoreFrame, (direction == SwingConstants.RIGHT) ? SwingConstants.LEFT : SwingConstants.TOP);
        }
        this.tabs.setSelected(this.scoreFrame);
        this.tabs.setSelected(this.audioFrame);
        this.tabs.setSelected(this.analyticsFrame);
        this.tabs.revalidate();
        this.tabs.repaint();
        javax.swing.SwingUtilities.invokeLater(() -> this.centerDividers(this.tabs));
    }

    /**
     * Sets all split dividers so that every tab frame gets the same share of the space.
     */
    private void centerDividers(java.awt.Component component) {
        if (!(component instanceof java.awt.Container))
            return;
        if (component instanceof javax.swing.JSplitPane) {
            javax.swing.JSplitPane split = (javax.swing.JSplitPane) component;
            int left = this.countTabFrames(split.getLeftComponent());
            int right = this.countTabFrames(split.getRightComponent());
            if ((left > 0) && (right > 0)) {
                double proportion = (double) left / (left + right);
                split.setResizeWeight(proportion);
                split.setDividerLocation(proportion);
            }
        }
        for (java.awt.Component child : ((java.awt.Container) component).getComponents())
            this.centerDividers(child);
    }

    private int countTabFrames(java.awt.Component component) {
        if (component instanceof javax.swing.JTabbedPane)
            return 1;
        int count = 0;
        if (component instanceof java.awt.Container)
            for (java.awt.Component child : ((java.awt.Container) component).getComponents())
                count += this.countTabFrames(child);
        return count;
    }

    /**
     * the Sync Player frame
     */
    private void makePlayerFrame() {
//        this.playerFrame.setIcon(Icons.table);
        this.playerFrame.setClosable(false);                                   // when closed the frame disappears and cannot be reopened by the user, thus, this is set false
        this.playerFrame.setMaximizable(false);                                // it is also set to not maximizable
        this.playerFrame.setPosition(CompassDirection.south);

        try {
            this.syncPlayer = new SyncPlayer(this);
        } catch (MidiUnavailableException e) {
            e.printStackTrace();
            return;
        }

        this.playerFrame.add(this.syncPlayer);
        this.addFrame(this.playerFrame);
    }

    /**
     * this method sets up the scoreDockFrame
     * @return
     */
    private ScoreDocumentData makeScoreFrame() {
        this.scoreFrame = new ScoreDocumentData(this);
        return this.scoreFrame;
    }

    /**
     * this method sets up the audioDockFrame
     * @return
     */
    private AudioDocumentData makeAudioFrame() {
        this.audioFrame = new AudioDocumentData(this);
        return this.audioFrame;
    }

    /**
     * this method sets up the analytics dock frame
     * @return
     */
    private AnalyticsDocumentData makeAnalyticsFrame() {
        this.analyticsFrame = new AnalyticsDocumentData(this);
        return this.analyticsFrame;
    }

    /**
     * provides access to the audio analysis frame
     * @return
     */
    public AudioDocumentData getAudioFrame() {
        return this.audioFrame;
    }

    /**
     * provides access to the analytics frame
     * @return
     */
    public AnalyticsDocumentData getAnalyticsFrame() {
        return this.analyticsFrame;
    }

    /**
     * access the project data
     * @return
     */
    public ProjectData getProjectData() {
        return this.data;
    }

    /**
     * get the project file
     * @return
     */
    public File getFile() {
        return this.data.getFile();
    }

    /**
     * access the Msm object
     * @return
     */
    public Msm getMsm() {
        return this.data.getMsm();
    }

    /**
     * access the MSM tree
     * @return
     */
    public MsmTree getMsmTree() {
        return this.msmTree;
    }

    /**
     * add an MPM to the Project
     * @param mpm
     */
    public void setMpm(Mpm mpm) {
        this.getProjectData().setMpm(mpm);
        this.mpmDockableFrame.setMpm(mpm);

        this.syncPlayer.updatePerformanceList();
        if (this.analyticsFrame != null)
            this.analyticsFrame.updatePerformanceList();
    }

    /**
     * delete the MPM from this project
     */
    public void removeMpm() {
        if (this.getMpm() == null)
            return;
        this.mpmDockableFrame.removeMpm();
        this.data.removeMpm();
        this.syncPlayer.updatePerformanceList();
        if (this.analyticsFrame != null)
            this.analyticsFrame.updatePerformanceList();
        this.repaintScoreDisplay();
    }

    /**
     * access the Mpm object
     * @return
     */
    public Mpm getMpm() {
        return this.data.getMpm();
    }

    /**
     * a getter for the MPM tree
     * @return
     */
    public MpmTree getMpmTree() {
        return this.mpmDockableFrame.getMpmTree();
    }

    /**
     * a getter for the MPM tree container
     */
    public WebDockableFrame getMpmDockableFrame() {
        return this.mpmDockableFrame;
    }

    /**
     * access the list of score pages
     * @return
     */
    public Score getScore() {
        return this.data.getScore();
    }

    /**
     * access the score frame
     * @return
     */
    public ScoreDocumentData getScoreFrame() {
        return scoreFrame;
    }

    /**
     * access the sync player
     * @return
     */
    public SyncPlayer getSyncPlayer() {
        return this.syncPlayer;
    }

    /**
     * repaint the score display
     */
    public void repaintScoreDisplay() {
        if (this.getScoreFrame().getScoreDisplay() != null)
            this.getScoreFrame().getScoreDisplay().repaint();
    }

    /**
     * add a file to the list of score pages
     * @param file
     */
    public void addScorePage(File file) {
        if (this.data.addScorePage(file) != null) {
            this.scoreFrame.addScorePage(file);
            this.annotationDockableFrame.refreshTree();
        }
    }

    /**
     * add the contents of a PDF file to the score pages; the PDF's pages will be extracted and stored as PNGs
     * @param pdf
     */
    public void addScorePdf(File pdf) {
        for (ScorePage scorePage : this.data.addScorePdf(pdf))
            this.scoreFrame.addScorePage(scorePage.getFile());
        this.annotationDockableFrame.refreshTree();
        // switch to the Score tab
        this.tabs.setSelected(this.scoreFrame);
    }

    /**
     * remove a score file from the project
     * @param index
     */
    public void removeScorePage(int index) {
        if (this.getScore().isEmpty())
            return;

        ScorePage page = this.getScore().getPage(index);
        if (page != null) {
            this.removeSvg(page.getSvg());
        }

        this.data.removeScorePage(index);                       // delete the page from the project data structure
        this.scoreFrame.removeScorePage(index);
        this.svgDockableFrame.refreshForCurrentPage();
        this.annotationDockableFrame.refreshTree();
    }

    /**
     * access the list of Audio objects
     * @return
     */
    public ArrayList<Audio> getAudio() {
        return this.data.getAudio();
    }

    /**
     * add an Audio object to the list of audios
     * @param audio
     * @return success
     */
    public boolean addAudio(Audio audio) {
        if (this.data.addAudio(audio)) {
            this.syncPlayer.addAudio(audio);
            if (this.analyticsFrame != null)
                this.analyticsFrame.updateAudioList();
            // switch to the Audio tab
            this.tabs.setSelected(this.audioFrame);
            // select the newly added audio in the SyncPlayer (last entry)
            this.syncPlayer.getAudioChooser().setSelectedIndex(this.syncPlayer.getAudioChooser().getItemCount() - 1);
            return true;
        }
        return false;
    }

    /**
     * remove an audio file from the project
     * @param index
     */
    public void removeAudio(int index) {
        Audio audio = this.getAudio().get(index);
        this.syncPlayer.removeAudio(audio);
        this.data.removeAudio(index);
        if (this.analyticsFrame != null)
            this.analyticsFrame.updateAudioList();
    }

    /**
     * access the SVG dockable frame
     * @return
     */
    public SvgDockableFrame getSvgDockableFrame() {
        return this.svgDockableFrame;
    }

    public AnnotationDockableFrame getAnnotationDockableFrame() {
        return this.annotationDockableFrame;
    }

    public AnnotationManager getAnnotationManager() {
        return this.annotationManager;
    }

    /**
     * Add an SVG overlay to the project.
     * @param svg
     */
    public void addSvg(SvgData svg) {
        ScorePage scorePage = this.getCurrentScorePage();
        if (scorePage == null) {
            if (this.data.addSvg(svg)) {
                this.svgDockableFrame.refreshForCurrentPage();
                this.repaintScoreDisplay();
            }
            return;
        }
        if (this.data.addSvg(svg, scorePage)) {
            this.svgDockableFrame.showSvgsForPage(scorePage, svg);
            this.repaintScoreDisplay();
        }
    }

    /**
     * Remove an SVG overlay from the project.
     * @param index
     */
    public void removeSvg(int index) {
        this.data.removeSvg(index);
        this.svgDockableFrame.refreshForCurrentPage();
        this.repaintScoreDisplay();
    }

    /**
     * Remove a specific SVG overlay from the project.
     * @param svg the SVG to remove
     */
    public void removeSvg(SvgData svg) {
        this.data.removeSvg(svg);
        this.svgDockableFrame.refreshForCurrentPage();
        this.repaintScoreDisplay();
    }

    /**
     * Get the score page currently shown in the score display, or the first page if none is shown.
     * @return current score page or null
     */
    public ScorePage getCurrentScorePage() {
        if ((this.scoreFrame != null) && (this.scoreFrame.getScoreDisplay() != null)) {
            return this.scoreFrame.getScoreDisplay().getScorePage();
        }
        return this.getScore().isEmpty() ? null : this.getScore().getPage(0);
    }

    /**
     * Load annotation data from a CSV file into the AnnotationPanel.
     * Opens a dialog to let the user configure column types and units.
     * @param file the CSV file
     */
    public void loadAnnotationCsv(File file) {
        CsvImportDialog dialog = new CsvImportDialog(file, this.audioFrame.getAnnotations());
        if (!dialog.showDialog())
            return;

        MeasurementData built = dialog.buildAnnotationData();
        if (built == null) {
            System.err.println("No valid annotation data found in " + file.getAbsolutePath());
            return;
        }

        System.out.println("Loaded " + built.getRowCount() + " rows from " + file.getAbsolutePath());

        MeasurementData target = dialog.getTargetAnnotationData();
        if (target != null)
            this.audioFrame.replaceAnnotation(target, built);
        else
            this.audioFrame.addAnnotation(built);

        this.tabs.setSelected(this.audioFrame);
    }

    /**
     * Save the project under its already defined filename. If it is not defined (in this.xml) it returns false.
     * @return
     */
    public boolean saveProject() {
        return this.data.saveProject();
    }

    /**
     * This saves the project in an .mpr file, basically an xml file which stores relative paths to all other project files.
     * If the MSM or MPM file was not existent in the file system, they will be created in the directory.
     * @param file
     * @return
     */
    public boolean saveProjectAs(File file) {
        return this.data.saveProjectAs(file);
    }
    /**
     * the user config file that belongs to the given project file, i.e. "[project].mpr.user"
     * @param projectFile
     * @return
     */
    private static File getUserConfigFile(File projectFile) {
        return new File(projectFile.getAbsolutePath() + ".user");
    }

    /**
     * Store the user's view of this project (selected audio, sizes and arrangement of frames and tabs) in "[project].mpr.user".
     * Nothing is written if the project has no file yet.
     */
    public void saveUserConfig() {
        File projectFile = this.getFile();
        if (projectFile == null)
            return;

        try {
            Element root = new Element("mprUser");

            Element syncElt = new Element("syncPlayer");
            syncElt.addAttribute(new Attribute("selectedAudio", String.valueOf(this.syncPlayer.getAudioChooser().getSelectedIndex())));
            String performanceName = this.syncPlayer.getSelectedPerformanceName();
            if (performanceName != null)
                syncElt.addAttribute(new Attribute("performance", performanceName));
            String midiPortName = this.syncPlayer.getSelectedMidiPortName();
            if (midiPortName != null)
                syncElt.addAttribute(new Attribute("midiOut", midiPortName));
            try {
                this.syncPlayer.getOffsetSpinner().commitEdit();            // apply a value that has been typed but not yet committed
            } catch (java.text.ParseException ignored) {
            }
            syncElt.addAttribute(new Attribute("skip", String.valueOf(((Number) this.syncPlayer.getOffsetSpinner().getValue()).longValue())));
            root.appendChild(syncElt);

            Element dock = new Element("dockableState");
            dock.appendChild(XmlUtils.toXML(this.getState()));
            root.appendChild(dock);

            Element tabsElt = new Element("tabsState");
            tabsElt.appendChild(XmlUtils.toXML(this.tabs.getDocumentPaneState()));
            root.appendChild(tabsElt);

            try {
                if (this.audioFrame != null) {
                    String s = this.audioFrame.getSplitState();
                    if (s != null) {
                        Element e = new Element("audioSplit");
                        e.addAttribute(new Attribute("fractions", s));
                        root.appendChild(e);
                    }
                }
                if (this.analyticsFrame != null) {
                    String s = this.analyticsFrame.getSplitState();
                    if (s != null) {
                        Element e = new Element("analyticsSplit");
                        e.addAttribute(new Attribute("fractions", s));
                        root.appendChild(e);
                    }
                }
                ScoreDisplayPanel sd = (this.scoreFrame == null) ? null : this.scoreFrame.getScoreDisplay();
                if ((sd != null) && (sd.getPanZoomHelper().getZoomFactor() != null)) {
                    Element e = new Element("score");
                    e.addAttribute(new Attribute("zoom", String.valueOf(sd.getPanZoomHelper().getZoomFactor())));
                    e.addAttribute(new Attribute("offsetX", String.valueOf(sd.getPanZoomHelper().getOffset().getX())));
                    e.addAttribute(new Attribute("offsetY", String.valueOf(sd.getPanZoomHelper().getOffset().getY())));
                    e.addAttribute(new Attribute("page", String.valueOf(sd.getPageIndex())));
                    root.appendChild(e);
                }
            } catch (Exception e) {
                System.err.println("Could not save split/score state: " + e);
            }

            try (FileOutputStream out = new FileOutputStream(getUserConfigFile(projectFile))) {
                Serializer serializer = new Serializer(out, "UTF-8");
                serializer.setIndent(2);
                serializer.write(new Document(root));
            }
        } catch (Exception e) {
            System.err.println("Could not save the user config: " + e.getMessage());
        }
    }

    /**
     * Restore the user's view from "[project].mpr.user", if it exists. Otherwise the default view stays untouched.
     */
    public void loadUserConfig() {
        File projectFile = this.getFile();
        if (projectFile == null)
            return;
        File userFile = getUserConfigFile(projectFile);
        if (!userFile.isFile())
            return;

        try {
            Element root = new Builder().build(userFile).getRootElement();

            try {
                Element tabsElt = root.getFirstChildElement("tabsState");
                if (tabsElt != null) {
                    DocumentPaneState state = XmlUtils.fromXML(tabsElt.getValue());
                    this.tabs.setDocumentPaneState(state);
                }
            } catch (Exception e) {
                System.err.println("Could not restore the tab arrangement: " + e.getMessage());
            }

            try {
                Element dockElt = root.getFirstChildElement("dockableState");
                if (dockElt != null) {
                    DockableContainer state = XmlUtils.fromXML(dockElt.getValue());
                    this.setState(state);
                }
            } catch (Exception e) {
                System.err.println("Could not restore the frame layout: " + e.getMessage());
            }

            final Element audioSplitElt = root.getFirstChildElement("audioSplit");
            final Element analyticsSplitElt = root.getFirstChildElement("analyticsSplit");
            final Element scoreElt = root.getFirstChildElement("score");
            javax.swing.SwingUtilities.invokeLater(() -> javax.swing.SwingUtilities.invokeLater(() -> {    // after the first layout pass, so sizes exist
                try {
                    if ((audioSplitElt != null) && (this.audioFrame != null))
                        this.audioFrame.setSplitState(audioSplitElt.getAttributeValue("fractions"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    if ((analyticsSplitElt != null) && (this.analyticsFrame != null))
                        this.analyticsFrame.setSplitState(analyticsSplitElt.getAttributeValue("fractions"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    ScoreDisplayPanel sd = (this.scoreFrame == null) ? null : this.scoreFrame.getScoreDisplay();
                    if ((scoreElt != null) && (sd != null)) {
                        sd.showPage(Integer.parseInt(scoreElt.getAttributeValue("page")));
                        sd.getPanZoomHelper().setState(Double.parseDouble(scoreElt.getAttributeValue("zoom")), Double.parseDouble(scoreElt.getAttributeValue("offsetX")), Double.parseDouble(scoreElt.getAttributeValue("offsetY")));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));

            final Element syncElt = root.getFirstChildElement("syncPlayer");
            if (syncElt != null) javax.swing.SwingUtilities.invokeLater(() -> {     // applied after the project has been displayed, so that the sync player settings take the same path as user interactions
                // each step is isolated so that a failure in one (e.g. a listener throwing) cannot prevent the others
                String skip = syncElt.getAttributeValue("skip");
                if (skip != null) {
                    try {
                        this.syncPlayer.getOffsetSpinner().setValue(Double.parseDouble(skip));      // the spinner model holds Double values
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                String selectedAudio = syncElt.getAttributeValue("selectedAudio");     // index in the audio chooser (0 = no audio); must be set before the performance as it affects the performance list
                if (selectedAudio != null) {
                    try {
                        int index = Integer.parseInt(selectedAudio);
                        if ((index >= 0) && (index < this.syncPlayer.getAudioChooser().getItemCount()))
                            this.syncPlayer.getAudioChooser().setSelectedIndex(index);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                String performance = syncElt.getAttributeValue("performance");
                if (performance != null) {
                    try {
                        this.syncPlayer.selectPerformanceByName(performance);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                String midiOut = syncElt.getAttributeValue("midiOut");
                if (midiOut != null) {
                    try {
                        this.syncPlayer.selectMidiPortByName(midiOut);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("Could not load the user config: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Refresh both the MSM and MPM trees to reflect a changed measure display mode.
     * Instead of rebuilding the entire tree (which loses expansion state) or reloading
     * the root (which can double-insert nodes due to ExTreeModel event ordering), we
     * reload only the structural container nodes whose children change between modes.
     */
    public void refreshTreeDisplayMode() {
        refreshMsmTreeDisplayMode();
        MpmTree mpmTree = this.getMpmTree();
        if (mpmTree != null)
            refreshMpmTreeDisplayMode(mpmTree);
    }

    /** Walk the MSM tree and reload every score node (and update note/rest/lyrics labels). */
    private void refreshMsmTreeDisplayMode() {
        MsmTreeNode root = this.msmTree.getRootNode();
        if (root != null)
            refreshMsmSubtree(root);
    }

    private void refreshMsmSubtree(MsmTreeNode node) {
        switch (node.getType()) {
            case score:
                // Reload children: flat notes ↔ measure groups depending on mode.
                // reloadNode() preserves the expansion of 'score' itself.
                this.msmTree.reloadNode(node);
                return; // reloadNode already handles the whole subtree
            case note:
            case rest:
            case lyrics:
                // Only the label text changes (PREFIX mode) – no structural change.
                this.msmTree.updateNode(node);
                return;
            default:
                // Recurse into children (global, part, header, dated, measure, …)
                for (int i = 0; i < node.getChildCount(); i++)
                    refreshMsmSubtree((MsmTreeNode) node.getChildAt(i));
        }
    }

    /** Walk the MPM tree and reload every dated-map node (and update map-entry labels). */
    private void refreshMpmTreeDisplayMode(MpmTree mpmTree) {
        MpmTreeNode root = mpmTree.getRootNode();
        if (root != null)
            refreshMpmSubtree(mpmTree, root);
    }

    private void refreshMpmSubtree(MpmTree mpmTree, MpmTreeNode node) {
        switch (node.getType()) {
            // Dated maps: children switch between flat entries and measure groups.
            // reloadNode() replaces their children entirely, so no further recursion needed.
            case articulationMap:
            case asynchronyMap:
            case dynamicsMap:
            case genericMap:
            case imprecisionMap:
            case metricalAccentuationMap:
            case ornamentationMap:
            case rubatoMap:
            case tempoMap:
                mpmTree.reloadNode(node);
                return;
            default:
                // For all other nodes (mpm, performance, global, part, dated, header,
                // style collections, measure groups, etc.) recurse into loaded children.
                for (int i = 0; i < node.getChildCount(); i++)
                    refreshMpmSubtree(mpmTree, (MpmTreeNode) node.getChildAt(i));
        }
    }
}
