package mpmToolbox.gui.analytics;

import com.alee.api.annotations.NotNull;
import com.alee.api.data.Orientation;
import com.alee.extended.split.WebMultiSplitPane;
import com.alee.extended.tab.DocumentData;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.scroll.WebScrollPane;
import meico.mpm.elements.Performance;
import mpmToolbox.gui.ProjectPane;
import mpmToolbox.gui.Settings;
import mpmToolbox.projectData.alignment.Alignment;
import mpmToolbox.projectData.alignment.Note;
import mpmToolbox.projectData.audio.Audio;
import mpmToolbox.supplementary.Tools;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;

/**
 * Analytics tab with selectable audio and performance sources.
 */
public class AnalyticsDocumentData extends DocumentData<WebPanel> {
    public enum SourceType {
        AUDIO,
        PERFORMANCE
    }

    public static class SourceEntry {
        private final SourceType type;
        private final Audio audio;
        private final Performance performance;
        private final Color color;
        private final WebCheckBox checkBox;
        private boolean enabled = true;
        private Alignment cachedAlignment = null;
        private ArrayList<Note> cachedNotes = null;

        private SourceEntry(SourceType type, Audio audio, Performance performance, String label, Color color) {
            this.type = type;
            this.audio = audio;
            this.performance = performance;
            this.color = color;
            this.checkBox = new WebCheckBox(true);
            this.checkBox.setText(label);
            this.checkBox.setOpaque(false);
        }

        public SourceType getType() {
            return this.type;
        }

        public Audio getAudio() {
            return this.audio;
        }

        public Performance getPerformance() {
            return this.performance;
        }

        public Color getColor() {
            return this.color;
        }

        public WebCheckBox getCheckBox() {
            return this.checkBox;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
            this.checkBox.setSelected(enabled);
        }

        public String getName() {
            if (this.audio != null && this.audio.getFile() != null)
                return this.audio.getFile().getName();
            if (this.performance != null)
                return this.performance.getName();
            return this.type.name();
        }

        public double getDurationMs(ProjectPane projectPane) {
            if (this.type == SourceType.AUDIO) {
                if (this.audio == null)
                    return 0.0;
                return ((double) this.audio.getNumberOfSamples() / this.audio.getFrameRate()) * 1000.0;
            }

            Alignment alignment = this.ensureAlignment(projectPane);
            return (alignment == null) ? 0.0 : alignment.getMillisecondsLength();
        }

        public Alignment ensureAlignment(ProjectPane projectPane) {
            if (this.type != SourceType.PERFORMANCE)
                return null;
            if (this.cachedAlignment != null)
                return this.cachedAlignment;
            if (this.performance == null || projectPane.getMsm() == null)
                return null;
            this.cachedAlignment = new Alignment(this.performance.perform(projectPane.getMsm()), null);
            this.cachedNotes = null;
            return this.cachedAlignment;
        }

        public ArrayList<Note> getPerformanceNotes(ProjectPane projectPane) {
            if (this.type != SourceType.PERFORMANCE)
                return new ArrayList<>();

            Alignment alignment = this.ensureAlignment(projectPane);
            if (alignment == null)
                return new ArrayList<>();

            if (this.cachedNotes != null)
                return this.cachedNotes;

            ArrayList<Note> notes = new ArrayList<>();
            for (mpmToolbox.projectData.alignment.Part part : alignment.getParts())
                notes.addAll(part.getNoteSequence());
            Collections.sort(notes, Comparator.comparingDouble(Note::getMillisecondsDate));
            this.cachedNotes = notes;
            return this.cachedNotes;
        }
    }

    private static final Color[] COLORS = {
            new Color(0, 180, 255),
            new Color(255, 160, 0),
            new Color(80, 255, 120),
            new Color(255, 80, 180),
            new Color(200, 200, 60),
            new Color(180, 100, 255)
    };

    private final ProjectPane projectPane;
    private final WebPanel analyticsPanel = new WebPanel(new GridBagLayout());  // the panel that contains everything in this tab
    private final WebMultiSplitPane splitPane = new WebMultiSplitPane(Orientation.vertical);  // the vertical split pane contains the accordion-like sections (source selection, volume graph etc.)
    private final SourceSelectionPanel sourceSelectionPanel;
    private final VolumePanel volumePanel;
    private final ArrayList<SourceEntry> audioSources = new ArrayList<>();
    private final ArrayList<SourceEntry> performanceSources = new ArrayList<>();

    public AnalyticsDocumentData(@NotNull ProjectPane projectPane) {
        super("Analytics", "Analytics", null);
        this.projectPane = projectPane;
        this.volumePanel = new VolumePanel(this);
        this.sourceSelectionPanel = new SourceSelectionPanel(this);

        this.setComponent(this.analyticsPanel);
        this.setClosable(false);
        this.draw();
        this.updateAudioList();
        this.updatePerformanceList();
        this.makeListeners();
    }

    /**
     * a listener to keep the playback cursor in the VolumePanel in sync with the SyncPlayer
     */
    private void makeListeners() {
        if (this.projectPane.getSyncPlayer() == null)
            return;

        this.projectPane.getSyncPlayer().getPlaybackSlider().addChangeListener(changeEvent -> this.volumePanel.repaint());
    }

    public ProjectPane getProjectPane() {
        return this.projectPane;
    }

    /**
     * a helper method to compute the relative playback position for the analytics visualizations
     * @return relative position in [0.0, 1.0] or null if no SyncPlayer is available
     */
    public Double getRelativePlaybackPosition() {
        if (this.projectPane.getSyncPlayer() == null)
            return null;
        return this.projectPane.getSyncPlayer().getRelativePlaybackSliderPosition();
    }

    public VolumePanel getVolumePanel() {
        return this.volumePanel;
    }

    public ArrayList<SourceEntry> getAudioSources() {
        return this.audioSources;
    }

    public ArrayList<SourceEntry> getPerformanceSources() {
        return this.performanceSources;
    }

    public ArrayList<SourceEntry> getVisibleSources() {
        ArrayList<SourceEntry> out = new ArrayList<>();
        for (SourceEntry source : this.audioSources)
            if (source.isEnabled())
                out.add(source);
        for (SourceEntry source : this.performanceSources)
            if (source.isEnabled())
                out.add(source);
        return out;
    }

    public void updateAudioList() {
        IdentityHashMap<Audio, Boolean> state = new IdentityHashMap<>();
        for (SourceEntry source : this.audioSources)
            state.put(source.getAudio(), source.isEnabled());

        this.audioSources.clear();
        if (this.projectPane.getAudio() != null) {
            int index = 0;
            for (Audio audio : this.projectPane.getAudio()) {
                boolean enabled = !state.containsKey(audio) || state.get(audio);
                String label = (audio.getFile() == null) ? "Audio " + (index + 1) : audio.getFile().getName();
                this.audioSources.add(new SourceEntry(SourceType.AUDIO, audio, null,
                        label, COLORS[index % COLORS.length]));
                this.audioSources.get(this.audioSources.size() - 1).setEnabled(enabled);
                ++index;
            }
        }

        this.sourceSelectionPanel.rebuildTiles();
    }

    public void updatePerformanceList() {
        IdentityHashMap<Performance, Boolean> state = new IdentityHashMap<>();
        for (SourceEntry source : this.performanceSources)
            state.put(source.getPerformance(), source.isEnabled());

        this.performanceSources.clear();
        if (this.projectPane.getMpm() != null) {
            int index = 0;
            for (Performance performance : this.projectPane.getMpm().getAllPerformances()) {
                boolean enabled = !state.containsKey(performance) || state.get(performance);
                SourceEntry entry = new SourceEntry(SourceType.PERFORMANCE, null, performance,
                        performance.getName(), COLORS[(index + 2) % COLORS.length]);
                entry.setEnabled(enabled);
                this.performanceSources.add(entry);
                ++index;
            }
        }

        this.sourceSelectionPanel.rebuildTiles();
    }

    public void updateSources() {
        this.updateAudioList();
        this.updatePerformanceList();
    }

    private void draw() {
        this.splitPane.setOneTouchExpandable(true);   // dividers have buttons for maximizing/minimizing a section (the accordion behavior)
        this.splitPane.setContinuousLayout(true);      // when the divider is moved the content is continuously redrawn
        this.splitPane.add(this.sourceSelectionPanel);
        this.splitPane.add(this.volumePanel);

        GridBagLayout gridBagLayout = (GridBagLayout) this.analyticsPanel.getLayout();
        Tools.addComponentToGridBagLayout(this.analyticsPanel, gridBagLayout, this.splitPane, 0, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.CENTER);
    }
}
