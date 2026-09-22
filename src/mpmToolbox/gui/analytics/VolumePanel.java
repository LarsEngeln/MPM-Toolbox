package mpmToolbox.gui.analytics;

import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import mpmToolbox.gui.Settings;
import mpmToolbox.gui.analytics.AnalyticsDocumentData.SourceEntry;
import mpmToolbox.gui.analytics.AnalyticsDocumentData.SourceType;
import mpmToolbox.projectData.alignment.Note;

import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;

/**
 * Draws loudness curves in dB for the active analytics sources.
 */
public class VolumePanel extends WebPanel {
    private static final double MIN_DB = -120.0;
    private static final double MAX_DB = 0.0;

    private final AnalyticsDocumentData parent;
    private final WebLabel placeholder = new WebLabel("Select at least one audio file or performance.", WebLabel.CENTER);

    // cache of precomputed dB curves so paintComponent() does not have to recompute them on every repaint
    private ArrayList<SourceEntry> cachedSources = null;
    private double cachedMaxDuration = -1.0;
    private double[][] cachedAvgDb = null;

    public VolumePanel(AnalyticsDocumentData parent) {
        super(new BorderLayout());
        this.parent = parent;
        this.placeholder.setOpaque(false);
        this.add(this.placeholder, BorderLayout.CENTER);
        this.setOpaque(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        ArrayList<SourceEntry> sources = this.parent.getVisibleSources();
        this.placeholder.setVisible(sources.isEmpty());
        if (sources.isEmpty())
            return;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            this.paintBackground(g2);

            int left = 60;
            int right = 20;
            int top = 20;
            int bottom = 35;
            int plotWidth = Math.max(1, this.getWidth() - left - right);
            int plotHeight = Math.max(1, this.getHeight() - top - bottom);
            double maxDuration = this.getMaxDuration(sources);
            if (maxDuration <= 0.0) {
                this.placeholder.setVisible(true);
                return;
            }

            this.drawGrid(g2, left, top, plotWidth, plotHeight, maxDuration);
            this.ensureCurveData(sources, maxDuration);
            this.drawCurves(g2, sources, left, top, plotWidth, plotHeight, maxDuration);
            this.drawPlaybackCursor(g2, left, top, plotWidth, plotHeight);
            //this.drawLegend(g2, sources, left, top);
        } finally {
            g2.dispose();
        }
    }

    private void paintBackground(Graphics2D g2) {
        g2.setColor(this.getBackground());
        g2.fillRect(0, 0, this.getWidth(), this.getHeight());
    }

    private double getMaxDuration(ArrayList<SourceEntry> sources) {
        double max = 0.0;
        for (SourceEntry source : sources)
            max = Math.max(max, source.getDurationMs(this.parent.getProjectPane()));
        return max;
    }

    private void drawGrid(Graphics2D g2, int left, int top, int plotWidth, int plotHeight, double maxDuration) {
        g2.setColor(new Color(180, 180, 180, 70));

        for (int db = 0; db >= -120; db -= 10) {
            int y = top + (int) Math.round((1.0 - normalizeDb(db)) * plotHeight);
            g2.drawLine(left, y, left + plotWidth, y);
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString(db + " dB", 4, y + 4);
            g2.setColor(new Color(180, 180, 180, 70));
        }

        double step = this.chooseTimeStep(maxDuration);
        g2.setColor(new Color(180, 180, 180, 70));
        for (double t = 0.0; t <= maxDuration + 0.0001; t += step) {
            int x = left + (int) Math.round((t / maxDuration) * plotWidth);
            g2.drawLine(x, top, x, top + plotHeight);
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString(formatTime(t), x + 2, top + plotHeight + 18);
            g2.setColor(new Color(180, 180, 180, 70));
        }
    }

    private static final int SAMPLE_STEPS = 500;

    /**
     * (Re-)computes the average/max dB curves for the given sources if the sources or the duration changed
     * since the last computation, so paintComponent() does not have to redo this expensive work on every repaint.
     */
    private void ensureCurveData(ArrayList<SourceEntry> sources, double maxDuration) {
        if ((this.cachedSources != null) && this.cachedSources.equals(sources) && (this.cachedMaxDuration == maxDuration))
            return;

        this.cachedAvgDb = new double[sources.size()][SAMPLE_STEPS];

        for (int s = 0; s < sources.size(); ++s) {
            SourceEntry source = sources.get(s);
            for (int i = 0; i < SAMPLE_STEPS; ++i) {
                double time = (SAMPLE_STEPS <= 1) ? 0.0 : (maxDuration * i) / (SAMPLE_STEPS - 1.0);
                this.cachedAvgDb[s][i] = this.computeDb(source, time, maxDuration);
            }
        }

        this.cachedSources = new ArrayList<>(sources);
        this.cachedMaxDuration = maxDuration;
    }

    private void drawCurves(Graphics2D g2, ArrayList<SourceEntry> sources, int left, int top, int plotWidth, int plotHeight, double maxDuration) {
        for (int s = 0; s < sources.size(); ++s) {
            SourceEntry source = sources.get(s);
            double[] avgDb = this.cachedAvgDb[s];

            Path2D avgPath = new Path2D.Double();
            boolean started = false;
            for (int i = 0; i < SAMPLE_STEPS; ++i) {
                double time = (SAMPLE_STEPS <= 1) ? 0.0 : (maxDuration * i) / (SAMPLE_STEPS - 1.0);
                double avgY = top + (1.0 - normalizeDb(avgDb[i])) * plotHeight;
                double drawX = left + (time / maxDuration) * plotWidth;
                if (!started) {
                    avgPath.moveTo(drawX, avgY);
                    started = true;
                } else {
                    avgPath.lineTo(drawX, avgY);
                }
            }

            g2.setColor(source.getColor());
            g2.setStroke(new BasicStroke(2.0f));
            g2.draw(avgPath);
        }
    }

    /**
     * draw the playback cursor line in the Graphics2D object
     * @param g2d
     */
    private void drawPlaybackCursor(Graphics2D g2d, int left, int top, int plotWidth, int plotHeight) {
        Double pos = this.parent.getRelativePlaybackPosition();
        if (pos == null)
            return;

        int x = left + (int) Math.round(plotWidth * pos);
        g2d.setColor(Color.GRAY);
        g2d.drawLine(x, top, x, top + plotHeight);
    }

    private void drawLegend(Graphics2D g2, ArrayList<SourceEntry> sources, int left, int top) {
        int x = left;
        int y = top + 12;
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, (float) Settings.getDefaultFontSize()));
        for (SourceEntry source : sources) {
            g2.setColor(source.getColor());
            g2.drawLine(x, y - 4, x + 18, y - 4);
            g2.drawString(source.getName(), x + 24, y);
            y += Settings.getDefaultFontSize() + 6;
        }
    }

    private double computeDb(SourceEntry source, double timeMs, double maxDurationMs) {
        if (!source.isEnabled())
            return MIN_DB;

        if (source.getType() == SourceType.AUDIO)
            return this.computeAudioDb(source, timeMs, maxDurationMs);
        return this.computePerformanceDb(source, timeMs, maxDurationMs);
    }

    private double computeAudioDb(SourceEntry source, double timeMs, double maxDurationMs) {
        if (source.getAudio() == null)
            return MIN_DB;

        double frameRate = source.getAudio().getFrameRate();
        int sampleCount = source.getAudio().getNumberOfSamples();
        if (sampleCount <= 0 || frameRate <= 0.0)
            return MIN_DB;

        double windowMs = Math.max(25.0, maxDurationMs / 200.0);
        double halfWindowMs = windowMs / 2.0;
        int fromSample = Math.max(0, (int) Math.floor(((timeMs - halfWindowMs) / 1000.0) * frameRate));
        int toSample = Math.min(sampleCount - 1, (int) Math.ceil(((timeMs + halfWindowMs) / 1000.0) * frameRate));
        if (toSample < fromSample)
            return MIN_DB;

        ArrayList<double[]> channels = source.getAudio().getWaveforms();
        double sum = 0.0;
        long count = 0;
        for (int chan = 0; chan < channels.size(); ++chan) {
            double[] samples = channels.get(chan);
            for (int i = fromSample; i <= toSample; ++i) {
                double sample = samples[i];
                sum += sample * sample;
                ++count;
            }
        }

        if (count == 0)
            return MIN_DB;
        double rms = Math.sqrt(sum / count);
        return toDb(rms);
    }

    private double computePerformanceDb(SourceEntry source, double timeMs, double maxDurationMs) {
        ArrayList<Note> notes = source.getPerformanceNotes(this.parent.getProjectPane());
        if (notes.isEmpty())
            return MIN_DB;

        double windowMs = Math.max(30.0, maxDurationMs / 250.0);
        double from = timeMs - (windowMs / 2.0);
        double to = timeMs + (windowMs / 2.0);
        double sum = 0.0;
        int active = 0;

        for (Note note : notes) {
            if (note.getMillisecondsDateEnd() < from)
                continue;
            if (note.getMillisecondsDate() > to)
                break;

            double overlapStart = Math.max(from, note.getMillisecondsDate());
            double overlapEnd = Math.min(to, note.getMillisecondsDateEnd());
            if (overlapEnd <= overlapStart)
                continue;

            double amplitude = Math.max(0.000001, Math.min(1.0, note.getVelocity() / 127.0));
            sum += amplitude;
            ++active;
        }

        if (active == 0)
            return MIN_DB;

        return toDb(sum / active);
    }

    private static double normalizeDb(double db) {
        double clipped = Math.max(MIN_DB, Math.min(MAX_DB, db));
        return (clipped - MIN_DB) / (MAX_DB - MIN_DB);
    }

    private static double toDb(double amplitude) {
        double clipped = Math.max(0.000001, amplitude);
        return Math.max(MIN_DB, Math.min(MAX_DB, 20.0 * Math.log10(clipped)));
    }

    private double chooseTimeStep(double maxDurationMs) {
        double rawStep = maxDurationMs / 10.0;
        double quantum = 10000.0; // 10 seconds
        return Math.ceil(rawStep / quantum) * quantum;
    }

    private static String formatTime(double ms) {
        long totalSec = Math.round(ms / 1000.0);
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format("%d:%02d", min, sec);
    }
}
