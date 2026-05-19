package mpmToolbox.gui.audio;

import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebCheckBoxMenuItem;
import com.alee.laf.menu.WebMenu;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.panel.WebPanel;
import mpmToolbox.gui.Settings;
import mpmToolbox.gui.audio.utilities.CsvImportDialog;
import mpmToolbox.supplementary.Tools;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/**
 * Visualizes one or more AnnotationData objects as line graphs, aligned with the other audio panels.
 * Renders directly from AnnotationLine values, no intermediate AnnotationEntry objects.
 * @author Lars Engeln
 */
public class AnnotationPanel extends WebPanel {

    private static final Color[] COLORS = {
        new Color(0,   180, 255),
        new Color(255, 160,   0),
        new Color(80,  255, 120),
        new Color(255,  80, 180),
        new Color(200, 200,  60),
        new Color(180, 100, 255),
    };

    protected final AudioDocumentData parent;
    protected final WebLabel annotationPlaceholder;

    /** Panel-local visibility overrides: true = visible in this panel. */
    private final IdentityHashMap<AnnotationData, Boolean> localVisibility = new IdentityHashMap<>();


    /**
     * constructor
     * @param parent the AudioDocumentData this panel belongs to
     */
    protected AnnotationPanel(AudioDocumentData parent) {
        super();
        this.parent = parent;
        this.annotationPlaceholder = new WebLabel("No annotation data available.", WebLabel.CENTER);
        this.annotationPlaceholder.setOpaque(false);
        this.add(this.annotationPlaceholder);
        this.updateAnnotationPlaceholder();
    }

    /**
     * Returns whether the given AnnotationData is visible in THIS panel.
     * @param data the AnnotationData to check
     */
    protected boolean isVisibleInPanel(AnnotationData data) {
        return localVisibility.getOrDefault(data, data.isVisible());
    }

    /**
     * Set visibility of the given AnnotationData for this panel only.
     * @param data the AnnotationData to set visibility for
     * @param visible true to show this dataset in this panel, false to hide it (overrides the dataset's own visibility setting)
     */
    protected void setVisibleInPanel(AnnotationData data, boolean visible) {
        localVisibility.put(data, visible);
    }


    /**
     * Show/hide the "no data" placeholder depending on whether there is anything to draw.
     */
    void updateAnnotationPlaceholder() {
        boolean hasData = false;
        for (AnnotationData d : this.parent.getAnnotations())
            if (isVisibleInPanel(d) && !d.isEmpty()) { hasData = true; break; }
        if (hasData) this.remove(this.annotationPlaceholder);
        else         this.add(this.annotationPlaceholder);
        this.revalidate();
        this.repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }

    /**
     * Draw all visible annotation data sets.
     * @param g2 graphics context
     */
    protected void paintAnnotations(Graphics2D g2) {
        if (this.parent.getAudio() == null)
            return;
        int w = this.getWidth();
        if (w <= 0) return;

        double fromMs, toMs;
        if (this.parent.getLeftmostMillisecond() >= 0 && this.parent.getRightmostMillisecond() >= 0) {
            fromMs = this.parent.getLeftmostMillisecond();
            toMs   = this.parent.getRightmostMillisecond();
        } else {
            double frameRate = this.parent.getAudio().getFrameRate();
            fromMs = this.parent.getLeftmostSample() / frameRate * 1000.0;
            toMs   = this.parent.getRightmostSample() / frameRate * 1000.0;
        }
        paintAnnotations(g2, fromMs, toMs, Double.NaN, Double.NaN);
    }

    /**
     * Draw all visible annotation data sets with frequency bounds only.
     * The time range is derived from the panel's leftmost/rightmost millisecond.
     *
     * @param g2     graphics context
     * @param fromHz spectrogram min frequency in Hz (NaN = auto y-axis)
     * @param toHz   spectrogram max frequency in Hz (NaN = auto y-axis)
     */
    protected void paintAnnotations(Graphics2D g2, double fromHz, double toHz) {
        if (this.parent.getAudio() == null)
            return;
        int w = this.getWidth();
        if (w <= 0) return;

        double fromMs, toMs;
        if (this.parent.getLeftmostMillisecond() >= 0 && this.parent.getRightmostMillisecond() >= 0) {
            fromMs = this.parent.getLeftmostMillisecond();
            toMs   = this.parent.getRightmostMillisecond();
        } else {
            double frameRate = this.parent.getAudio().getFrameRate();
            fromMs = this.parent.getLeftmostSample() / frameRate * 1000.0;
            toMs   = this.parent.getRightmostSample() / frameRate * 1000.0;
        }
        paintAnnotations(g2, fromMs, toMs, fromHz, toHz);
    }

    /**
     * Draw all visible annotation data sets with explicit time-axis parameters and optional frequency bounds.
     *
     * @param g2     graphics context
     * @param fromMs the time (ms) at panel pixel x = 0
     * @param toMs   the time (ms) at panel pixel x = width-1
     * @param fromHz spectrogram min frequency in Hz (NaN = auto y-axis)
     * @param toHz   spectrogram max frequency in Hz (NaN = auto y-axis)
     */
    protected void paintAnnotations(Graphics2D g2,
                                    double fromMs, double toMs,
                                    double fromHz, double toHz) {
        if (this.parent.getAudio() == null)
            return;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int colorIdx = 0;
        for (AnnotationData data : this.parent.getAnnotations()) {
            if (!isVisibleInPanel(data) || data.isEmpty()) { colorIdx++; continue; }
            this.drawDataset(g2, data, COLORS[colorIdx % COLORS.length],
                             fromMs, toMs, fromHz, toHz);
            colorIdx++;
        }
    }

    /**
     * Draw the textual overlay that shows the values at the current mouse cursor position.
     * @param g2 graphics context
     * @param millisec the time (ms) corresponding to the current mouse x position
     */
    protected void paintAnnotationMouseInfo(Graphics2D g2, double millisec) {
        int labelRow = 2;
        int lineStep = (int) (Settings.getDefaultFontSize() * 1.4f);
        int colorIdx = 0;
        for (AnnotationData data : this.parent.getAnnotations()) {
            if (!isVisibleInPanel(data) || data.isEmpty()) { colorIdx++; continue; }
            Color color = COLORS[colorIdx % COLORS.length];
            int vIdx = data.getFirstLineIndexOfType(AnnotationLine.Type.CURVE);
            if (vIdx < 0) vIdx = data.getFirstLineIndexOfType(AnnotationLine.Type.MARKS);
            if (vIdx >= 0) {
                Double val = interpolate(data, vIdx, millisec);
                if (val != null) {
                    g2.setColor(color);
                    g2.drawString(data.getName() + ": " + Tools.round(val, 4), 2,
                            Settings.getDefaultFontSize() + lineStep * (labelRow++));
                }
            }
            colorIdx++;
        }
    }

    /**
     * Draw one AnnotationData by dispatching each value line to the appropriate draw method.
     *
     * @param g2     graphics context
     * @param data   the dataset to draw
     * @param color  base color for this dataset
     * @param fromMs the time (ms) at panel pixel x = 0
     * @param toMs   the time (ms) at panel pixel x = width-1
     * @param fromHz spectrogram min frequency in Hz (NaN = auto y-axis)
     * @param toHz   spectrogram max frequency in Hz (NaN = auto y-axis)
     */
    private void drawDataset(Graphics2D g2, AnnotationData data, Color color,
                             double fromMs, double toMs,
                             double fromHz, double toHz) {
        double msRange = toMs - fromMs;
        if (msRange <= 0.0) return;

        boolean useFreqAxis = !Double.isNaN(fromHz) && !Double.isNaN(toHz) && toHz > fromHz;

        int timeIdx = data.getFirstLineIndexOfType(AnnotationLine.Type.TIME);
        AnnotationLine timeLine = (timeIdx >= 0) ? data.getLine(timeIdx) : null;

        int colorIdx = 0;

        // --- CURVE lines ---
        for (int lineIdx : data.getLineIndicesOfType(AnnotationLine.Type.CURVE)) {
            AnnotationLine valueLine = data.getLine(lineIdx);
            int alpha = (colorIdx == 0) ? 200 : Math.max(80, 200 - colorIdx * 40);
            Color c = withAlpha(color, alpha);
            if (useFreqAxis && valueLine.getUnit() == AnnotationLine.Unit.HZ)
                drawFrequencies(g2, data, valueLine, timeLine, c, fromMs, toMs, fromHz, toHz);
            else
                drawValues(g2, data, valueLine, timeLine, c, fromMs, toMs);
            colorIdx++;
        }

        // --- MARKS lines ---
        for (int lineIdx : data.getLineIndicesOfType(AnnotationLine.Type.MARKS)) {
            AnnotationLine marksLine = data.getLine(lineIdx);
            drawMarks(g2, marksLine, withAlpha(color, 200), fromMs, toMs);
        }

        // --- TEXT lines ---
        for (int lineIdx : data.getLineIndicesOfType(AnnotationLine.Type.TEXT)) {
            AnnotationLine textLine = data.getLine(lineIdx);
            drawTexts(g2, data, textLine, timeLine, withAlpha(color, 200), fromMs, toMs);
        }

        // dataset label (top-left)
        g2.setColor(withAlpha(color, 200));
        g2.setFont(g2.getFont().deriveFont(Font.BOLD).deriveFont((float) Settings.getDefaultFontSize()));
        g2.drawString(data.getName(), 4, Settings.getDefaultFontSize());
    }

    /**
     * Map a millisecond timestamp to an x pixel coordinate within the panel width.
     *
     * @param ms     the timestamp in milliseconds
     * @param fromMs the time (ms) at x = 0
     * @param toMs   the time (ms) at x = width
     * @param width  the panel width in pixels
     * @return x pixel coordinate
     */
    private static double alignTime(double ms, double fromMs, double toMs, int width) {
        return (ms - fromMs) / (toMs - fromMs) * width;
    }

    /**
     * Map a frequency value to a y pixel coordinate within the panel height (linear scale).
     *
     * @param hz     the frequency in Hz
     * @param fromHz the frequency at y = height (bottom)
     * @param toHz   the frequency at y = 0 (top)
     * @param height the panel height in pixels
     * @return y pixel coordinate
     */
    private static double alignFrequency(double hz, double fromHz, double toHz, int height) {
        return alignFrequency(hz, fromHz, toHz, height, false);
    }

    /**
     * Map a frequency value to a y pixel coordinate within the panel height.
     *
     * @param hz       the frequency in Hz
     * @param fromHz   the frequency at y = height (bottom)
     * @param toHz     the frequency at y = 0 (top)
     * @param height   the panel height in pixels
     * @param logScale if true, uses a logarithmic scale (matching the spectrogram); linear otherwise
     * @return y pixel coordinate
     */
    private static double alignFrequency(double hz, double fromHz, double toHz, int height, boolean logScale) {
        if (logScale) {
            if (hz <= 0 || fromHz <= 0) return height;
            double logRange = Math.log(toHz / fromHz);
            if (logRange == 0.0) return height;
            return height - (Math.log(hz / fromHz) / logRange) * height;
        } else {
            double range = toHz - fromHz;
            if (range == 0.0) return height;
            return height - ((hz - fromHz) / range) * height;
        }
    }

    /**
     * Draw a prepared path (line + fill) and dots for each point.
     *
     * @param g2     graphics context
     * @param points list of [x, y] pairs
     * @param color  base color (alpha already set)
     * @param width  panel width (for dot clipping)
     * @param height panel height (for fill closing)
     */
    private static void drawLine(Graphics2D g2, ArrayList<double[]> points, Color color, int width, int height) {
        if (points.isEmpty()) return;

        Color fillColor = withAlpha(color, 35);
        Color dotColor  = withAlpha(color, 220);

        Path2D.Double fill = new Path2D.Double();
        Path2D.Double path = new Path2D.Double();

        double[] first = points.get(0);
        double[] last  = points.get(points.size() - 1);

        fill.moveTo(first[0], height);
        fill.lineTo(first[0], first[1]);
        path.moveTo(first[0], first[1]);

        for (int i = 1; i < points.size(); i++) {
            double x = points.get(i)[0];
            double y = points.get(i)[1];
            fill.lineTo(x, y);
            path.lineTo(x, y);
        }

        fill.lineTo(last[0], height);
        fill.closePath();

        g2.setColor(fillColor);
        g2.fill(fill);

        Stroke prev = g2.getStroke();
        g2.setStroke(new BasicStroke(2.0f));
        g2.setColor(color);
        g2.draw(path);
        g2.setStroke(prev);

        g2.setColor(dotColor);
        for (double[] pt : points) {
            int px = (int) pt[0];
            int py = (int) pt[1];
            if (px >= -3 && px <= width + 3)
                g2.fillOval(px - 2, py - 2, 4, 4);
        }
    }

    /**
     * Process a CURVE value line with temporal alignment and draw it.
     * @param g2       graphics context
     * @param data     the dataset to draw
     * @param valueLine the CURVE line to draw (must have a time-aligned unit, e.g. ms or s)
     * @param timeLine the TIME line to use for temporal alignment (can be null if valueLine's unit is time-based)
      * @param color    base color for this dataset (alpha already set)
      * @param fromMs   the time (ms) at panel pixel x = 0
      * @param toMs     the time (ms) at panel pixel x = width-1
     */
    private void drawValues(Graphics2D g2, AnnotationData data, AnnotationLine valueLine,
                            AnnotationLine timeLine, Color color,
                            double fromMs, double toMs) {
        int width  = this.getWidth();
        int height = this.getHeight();

        int count = (timeLine != null) ? Math.min(timeLine.size(), valueLine.size()) : valueLine.size();
        if (count < 2) return;

        // stable y-range over full dataset
        double minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            double v = valueLine.getValue(i);
            if (v < minV) minV = v;
            if (v > maxV) maxV = v;
        }
        double margin = (maxV - minV) * 0.05;
        if (margin == 0.0) margin = 0.5;
        minV -= margin; maxV += margin;
        double vRange = (maxV - minV) > 0.0 ? (maxV - minV) : 1.0;

        ArrayList<double[]> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double ms = timeLine != null
                    ? timeLine.getUnit().toMilliseconds(timeLine.getValue(i))
                    : valueLine.getUnit().toMilliseconds(valueLine.getValue(i));
            if (ms < fromMs || ms > toMs) continue;
            double x = alignTime(ms, fromMs, toMs, width);
            double y = height - ((valueLine.getValue(i) - minV) / vRange) * height;
            points.add(new double[]{x, y});
        }

        if (points.size() < 2) return;
        drawLine(g2, points, color, width, height);
    }

    /**
     * Process a CURVE line whose unit is HZ, aligning both time (alignTime) and
     * frequency (alignFrequency) to the spectrogram's scale.
     * @param g2     graphics context
     * @param data   the dataset to draw (used for error checking)
     * @param valueLine the CURVE line to draw (must have unit HZ)
     */
    private void drawFrequencies(Graphics2D g2, AnnotationData data, AnnotationLine valueLine,
                                 AnnotationLine timeLine, Color color,
                                 double fromMs, double toMs,
                                 double fromHz, double toHz) {
        int width  = this.getWidth();
        int height = this.getHeight();

        int count = (timeLine != null) ? Math.min(timeLine.size(), valueLine.size()) : valueLine.size();
        if (count < 2) return;

        ArrayList<double[]> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double ms = timeLine != null
                    ? timeLine.getUnit().toMilliseconds(timeLine.getValue(i))
                    : valueLine.getUnit().toMilliseconds(valueLine.getValue(i));
            if (ms < fromMs || ms > toMs) continue;
            double hz = valueLine.getValue(i);
            double x = alignTime(ms, fromMs, toMs, width);
            double y = alignFrequency(hz, fromHz, toHz, height, true);
            points.add(new double[]{x, y});
        }

        if (points.size() < 2) return;
        drawLine(g2, points, color, width, height);
    }

    /**
     * Draw vertical lines at each MARKS timestamp using alignTime.
     * @param g2       graphics context
     * @param marksLine the MARKS line to draw
      * @param color    base color for this dataset (alpha already set)
      * @param fromMs   the time (ms) at panel pixel x = 0
      * @param toMs     the time (ms) at panel pixel x = width-
     */
    private void drawMarks(Graphics2D g2, AnnotationLine marksLine, Color color,
                           double fromMs, double toMs) {
        int width  = this.getWidth();
        int height = this.getHeight();

        Stroke prev = g2.getStroke();
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(color);

        for (int i = 0; i < marksLine.size(); i++) {
            double ms = marksLine.getUnit().toMilliseconds(marksLine.getValue(i));
            if (ms < fromMs || ms > toMs) continue;
            int x = (int) alignTime(ms, fromMs, toMs, width);
            g2.drawLine(x, 0, x, height);
        }
        g2.setStroke(prev);
    }

    /**
     * Draw text labels at each TEXT entry's timestamp (from the shared timeLine) using alignTime.
     * The TEXT line's values are treated as indices into the display strings; the label shown
     * is the value itself formatted as a string.
     * @param g2       graphics context
     * @param data     the dataset to draw (used for error checking and label retrieval)
     * @param textLine the TEXT line to draw
     * @param timeLine the TIME line to use for temporal alignment
     * @param color    base color for this dataset (alpha already set)
     * @param fromMs   the time (ms) at panel pixel x = 0
     * @param toMs     the time (ms) at panel pixel x = width-1
     */
    private void drawTexts(Graphics2D g2, AnnotationData data, AnnotationLine textLine,
                           AnnotationLine timeLine, Color color,
                           double fromMs, double toMs) {
        int width  = this.getWidth();
        int height = this.getHeight();

        int count = (timeLine != null) ? Math.min(timeLine.size(), textLine.size()) : textLine.size();
        if (count == 0) return;

        g2.setColor(color);
        g2.setFont(g2.getFont().deriveFont((float) Settings.getDefaultFontSize()));

        for (int i = 0; i < count; i++) {
            double ms = timeLine != null
                    ? timeLine.getUnit().toMilliseconds(timeLine.getValue(i))
                    : textLine.getUnit().toMilliseconds(textLine.getValue(i));
            if (ms < fromMs || ms > toMs) continue;
            int x = (int) alignTime(ms, fromMs, toMs, width);
            String label = String.valueOf(textLine.getValue(i));
            g2.drawString(label, x + 2, height / 2);
        }
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    /**
     * Append an "Annotations" submenu to the given context menu, allowing to toggle visibility and edit each dataset.
     * @param menu the context menu to append to
     */
    protected void appendAnnotationMenu(WebPopupMenu menu) {
        ArrayList<AnnotationData> all = this.parent.getAnnotations();
        if (all.isEmpty())
            return;
        WebMenu annotationMenu = new WebMenu("Annotations");
        for (AnnotationData data : all) {
            WebMenu dataMenu = new WebMenu(data.getName());

            WebCheckBoxMenuItem visItem = new WebCheckBoxMenuItem("visible", isVisibleInPanel(data));
            visItem.addActionListener(ae -> {
                setVisibleInPanel(data, !isVisibleInPanel(data));
                this.updateAnnotationPlaceholder();
                this.repaint();
            });
            dataMenu.add(visItem);

            WebMenuItem editItem = new WebMenuItem("edit");
            editItem.addActionListener(ae -> {
                CsvImportDialog dialog = new CsvImportDialog(data, this.parent.getAnnotations());
                if (!dialog.showDialog()) return;
                AnnotationData built = dialog.buildAnnotationData();
                if (built == null) return;
                AnnotationData target = dialog.getTargetAnnotationData();
                if (target != null && target != data)
                    this.parent.replaceAnnotation(target, built);
                this.updateAnnotationPlaceholder();
                this.repaint();
            });
            dataMenu.add(editItem);

            annotationMenu.add(dataMenu);
        }
        menu.addSeparator();
        menu.add(annotationMenu);
    }

    /**
     * Interpolate the value of the given line at the given millisecond timestamp.
     * @param data
     * @param lineIdx
     * @param ms
     * @return the interpolated value, or null if interpolation is not possible (e.g. no TIME line, empty line, etc.)
     */
    private static Double interpolate(AnnotationData data, int lineIdx, double ms) {
        AnnotationLine valueLine = data.getLine(lineIdx);
        boolean isMarks = valueLine.getType() == AnnotationLine.Type.MARKS;
        int timeIdx = data.getFirstLineIndexOfType(AnnotationLine.Type.TIME);
        AnnotationLine timeLine = (!isMarks && timeIdx >= 0) ? data.getLine(timeIdx) : null;

        int count = (timeLine != null) ? Math.min(timeLine.size(), valueLine.size()) : valueLine.size();
        if (count == 0) return null;

        double firstMs = msAt(timeLine, valueLine, isMarks, 0);
        double lastMs  = msAt(timeLine, valueLine, isMarks, count - 1);

        if (ms <= firstMs) return isMarks ? 1.0 : valueLine.getValue(0);
        if (ms >= lastMs)  return isMarks ? 1.0 : valueLine.getValue(count - 1);

        for (int i = 0; i < count - 1; i++) {
            double msA = msAt(timeLine, valueLine, isMarks, i);
            double msB = msAt(timeLine, valueLine, isMarks, i + 1);
            if (ms >= msA && ms <= msB) {
                double t  = (ms - msA) / (msB - msA);
                double vA = isMarks ? 1.0 : valueLine.getValue(i);
                double vB = isMarks ? 1.0 : valueLine.getValue(i + 1);
                return vA + t * (vB - vA);
            }
        }
        return null;
    }

    private static double msAt(AnnotationLine timeLine, AnnotationLine valueLine, boolean isMarks, int i) {
        if (isMarks) return valueLine.getUnit().toMilliseconds(valueLine.getValue(i));
        return timeLine.getUnit().toMilliseconds(timeLine.getValue(i));
    }
}

