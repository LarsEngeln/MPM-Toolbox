package mpmToolbox.gui.mpmEditingTools.editDialogs.visualizers;

import com.alee.laf.panel.WebPanel;
import mpmToolbox.gui.Settings;

import java.awt.*;

/**
 * A WebPanel that paints the visualization of an MPM temporalSpread ornament definition.
 * Shows:
 * – a bar representing the principal note's duration
 * – the active frame region (frameStart … frameStart+frameLength), anchored at the
 *   start or end of the note depending on atEnd
 * – dots for a fixed number of example ornament notes whose x-positions follow the intensity curve
 * @author Lars Engeln
 */
public class TemporalSpreadVisualizer extends WebPanel {
    private static final int NOTE_COUNT = 8;

    private double frameStart  = 0.0;
    private double frameLength = 0.0;
    private double intensity   = 1.0;
    private boolean atEnd      = false;

    /**
     * constructor
     */
    public TemporalSpreadVisualizer() {
        super();

        // we have to set an initial non-zero preferred size so the panel will actually take room in the
        // gridbaglayout, even though it will be stretched later on; without this it won't show up
        int size = (this.getFontMetrics(this.getFont()).getHeight() + Settings.paddingInDialogs) * 4;
        this.setPreferredSize(size, size);

        this.setBackground(this.getBackground().brighter());
    }

    /**
     * this method paints the visualization
     * @param g
     */
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        final int w    = this.getWidth();
        final int h    = this.getHeight();
        final int pad  = Settings.paddingInDialogs;
        final int midY = h / 2;
        final int barH = Math.max(4, h / 5);

        Color noteBarFill  = new Color(20, 20, 20);
        Color frameFill    = new Color(10, 10, 10);
        Color tickColor    = new Color(220, 80, 60, 230);


        final double noteW    = 100.0;
        final double viewMin  = -noteW * 0.35;
        final double viewMax  =  noteW * 1.35;
        final double viewSpan = viewMax - viewMin;

        // pixel x for a virtual-unit position
        final int drawWidth = w - 2 * pad;

        int notePixLeft  = (int) toPixelX(0.0,    viewMin, viewSpan, pad, drawWidth);
        int notePixRight = (int) toPixelX(noteW,  viewMin, viewSpan, pad, drawWidth);
        int notePixW     = Math.max(1, notePixRight - notePixLeft);

        g2.setColor(noteBarFill);
        g2.fillRect(notePixLeft, midY - barH / 2, notePixW, 3);

        // Scale so the largest of (|fStart|+fLength, noteW) maps to noteW virtual units,
        // keeping the frame proportional to the note bar.
        double scale   = noteW / Math.max(noteW, Math.abs(this.frameStart) + this.frameLength + 1e-9);
        double vStart  = this.frameStart  * scale;
        double vLength = this.frameLength * scale;

        double anchor     = this.atEnd ? noteW : 0.0;
        double frameLeft  = this.atEnd ? anchor + vStart - vLength : anchor + vStart;
        double frameRight = frameLeft + vLength;

        int framePixLeft  = (int) toPixelX(frameLeft,  viewMin, viewSpan, pad, drawWidth);
        int framePixRight = (int) toPixelX(frameRight, viewMin, viewSpan, pad, drawWidth);
        int framePixX     = Math.min(framePixLeft, framePixRight);
        int framePixW     = Math.max(1, Math.abs(framePixRight - framePixLeft));

        g2.setColor(frameFill);
        g2.fillRect(framePixX, midY + 4, framePixW, 2);

        final int dotR   = Math.max(3, barH / 3);
        final int tickTop    = midY + 4 + 4;
        final int tickBottom = midY + 4 - 4 - 2;

        g2.setColor(tickColor);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < NOTE_COUNT; i++) {
            double t      = (double) i / NOTE_COUNT;
            double curved = Math.pow(t, Math.max(0.01, this.intensity));
            double vPos   = frameLeft + curved * vLength;
            int    px     = (int) toPixelX(vPos, viewMin, viewSpan, pad, drawWidth);
            g2.drawLine(px, tickTop, px, tickBottom);
        }

        g2.dispose();
    }

    /** Maps a virtual-unit value to a pixel x coordinate within [drawLeft, drawLeft+drawWidth]. */
    private static double toPixelX(double v, double viewMin, double viewSpan, int drawLeft, int drawWidth) {
        return drawLeft + (v - viewMin) / viewSpan * drawWidth;
    }

    /**
     * set the frame start value and repaint
     * @param frameStart
     */
    public void setFrameStart(double frameStart) {
        this.frameStart = frameStart;
        this.repaint();
    }

    /**
     * set the frame length value and repaint
     * @param frameLength must be >= 0
     */
    public void setFrameLength(double frameLength) {
        this.frameLength = Math.max(0.0, frameLength);
        this.repaint();
    }

    /**
     * set the intensity value and repaint
     * @param intensity must be >= 0
     */
    public void setIntensity(double intensity) {
        this.intensity = Math.max(0.0, intensity);
        this.repaint();
    }

    /**
     * set the atEnd flag and repaint
     * @param atEnd true if the ornament is anchored at the end of the principal note
     */
    public void setAtEnd(boolean atEnd) {
        this.atEnd = atEnd;
        this.repaint();
    }

    /**
     * update all parameters at once and repaint once
     * @param frameStart
     * @param frameLength must be >= 0
     * @param intensity   must be >= 0
     * @param atEnd
     */
    public void setAll(double frameStart, double frameLength, double intensity, boolean atEnd) {
        this.frameStart  = frameStart;
        this.frameLength = Math.max(0.0, frameLength);
        this.intensity   = Math.max(0.0, intensity);
        this.atEnd       = atEnd;
        this.repaint();
    }
}





