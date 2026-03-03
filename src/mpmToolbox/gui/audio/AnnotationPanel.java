package mpmToolbox.gui.audio;

import com.alee.laf.menu.WebCheckBoxMenuItem;
import com.alee.laf.menu.WebMenu;
import com.alee.laf.menu.WebPopupMenu;
import mpmToolbox.gui.Settings;
import mpmToolbox.supplementary.Tools;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;

/**
 * Visualizes one or more AnnotationData objects as line graphs, aligned with the other audio panels.
 * Renders directly from AnnotationLine values – no intermediate AnnotationEntry objects.
 * @author Lars Engeln
 */
public class AnnotationPanel extends PianoRollPanel {

    private static final Color[] COLORS = {
        new Color(0,   180, 255),
        new Color(255, 160,   0),
        new Color(80,  255, 120),
        new Color(255,  80, 180),
        new Color(200, 200,  60),
        new Color(180, 100, 255),
    };

    /**
     * constructor
     * @param parent the owning AudioDocumentData
     */
    protected AnnotationPanel(AudioDocumentData parent) {
        super(parent, "No annotation data available.");
        this.setOpaque(true);
        this.setBackground(Color.BLACK);
        this.updateNoDataLabel();
    }

    /** Show/hide the "no data" placeholder depending on whether there is anything to draw. */
    void updateNoDataLabel() {
        boolean hasData = false;
        for (AnnotationData d : this.parent.getAnnotations())
            if (d.isVisible() && !d.isEmpty()) { hasData = true; break; }
        if (hasData) this.remove(this.noData);
        else         this.add(this.noData);
        this.revalidate();
        this.repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (this.parent.getAudio() == null) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        this.drawPianoRoll(g2);

        int colorIdx = 0;
        for (AnnotationData data : this.parent.getAnnotations()) {
            if (!data.isVisible() || data.isEmpty()) { colorIdx++; continue; }
            this.drawDataset(g2, data, COLORS[colorIdx % COLORS.length]);
            colorIdx++;
        }

        this.drawPlaybackCursor(g2);

        if (this.drawMouseCursor(g2)) {
            long sampleIndex = this.parent.getMouseCursor().getSample();
            double millisec  = Tools.round(((double) sampleIndex / this.parent.getAudio().getFrameRate()) * 1000.0, 2);
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString("ms: " + millisec, 2, Settings.getDefaultFontSize());

            int labelRow = 2;
            int lineStep = (int)(Settings.getDefaultFontSize() * 1.4f);
            colorIdx = 0;
            for (AnnotationData data : this.parent.getAnnotations()) {
                if (!data.isVisible() || data.isEmpty()) { colorIdx++; continue; }
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
    }

    /**
     * Draw one AnnotationData: all CURVE and MARKS lines against the first TIME line.
     * @param g2    graphics context
     * @param data  the dataset to draw
     * @param color base color for this dataset
     */
    private void drawDataset(Graphics2D g2, AnnotationData data, Color color) {
        int width  = this.getWidth();
        int height = this.getHeight();
        double fromMs  = ((double) this.parent.getLeftmostSample()  / this.parent.getAudio().getFrameRate()) * 1000.0;
        double toMs    = ((double) this.parent.getRightmostSample() / this.parent.getAudio().getFrameRate()) * 1000.0;
        double msRange = toMs - fromMs;
        if (msRange <= 0.0) return;

        // TIME line provides the x-axis; MARKS lines use themselves as time
        int timeIdx = data.getFirstLineIndexOfType(AnnotationLine.Type.TIME);

        ArrayList<Integer> valueCols = data.getLineIndicesOfType(AnnotationLine.Type.CURVE);
        valueCols.addAll(data.getLineIndicesOfType(AnnotationLine.Type.MARKS));
        if (valueCols.isEmpty()) return;

        for (int vIdx = 0; vIdx < valueCols.size(); vIdx++) {
            int lineIdx = valueCols.get(vIdx);
            AnnotationLine valueLine = data.getLine(lineIdx);
            boolean isMarks = valueLine.getType() == AnnotationLine.Type.MARKS;

            // build parallel ms / value arrays
            AnnotationLine timeLine = (timeIdx >= 0 && !isMarks) ? data.getLine(timeIdx) : null;
            int count = (timeLine != null) ? Math.min(timeLine.size(), valueLine.size()) : valueLine.size();
            if (count < 2) continue;

            // compute value range
            double minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                double v = isMarks ? 1.0 : valueLine.getValue(i);
                if (v < minV) minV = v;
                if (v > maxV) maxV = v;
            }
            double margin = (maxV - minV) * 0.05;
            if (margin == 0.0) margin = 0.5;
            minV -= margin; maxV += margin;
            double vRange = (maxV - minV) > 0.0 ? (maxV - minV) : 1.0;

            int alpha = (vIdx == 0) ? 200 : Math.max(80, 200 - vIdx * 40);
            Color lineColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
            Color fillColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 35);
            Color dotColor  = new Color(color.getRed(), color.getGreen(), color.getBlue(), 220);

            Path2D.Double fill = new Path2D.Double();
            Path2D.Double path = new Path2D.Double();
            boolean started = false;
            double lastX = 0.0;

            for (int i = 0; i < count; i++) {
                double ms = isMarks
                        ? valueLine.getUnit().toMilliseconds(valueLine.getValue(i))
                        : timeLine.getUnit().toMilliseconds(timeLine.getValue(i));
                double v  = isMarks ? 1.0 : valueLine.getValue(i);
                double x  = ((ms - fromMs) / msRange) * width;
                double y  = height - ((v - minV) / vRange) * height;
                if (!started) {
                    fill.moveTo(x, height);
                    fill.lineTo(x, y);
                    path.moveTo(x, y);
                    started = true;
                } else {
                    fill.lineTo(x, y);
                    path.lineTo(x, y);
                }
                lastX = x;
            }

            if (started) {
                fill.lineTo(lastX, height);
                fill.closePath();
                g2.setColor(fillColor);
                g2.fill(fill);

                Stroke prev = g2.getStroke();
                g2.setStroke(new BasicStroke(2.0f));
                g2.setColor(lineColor);
                g2.draw(path);
                g2.setStroke(prev);

                // dots
                g2.setColor(dotColor);
                for (int i = 0; i < count; i++) {
                    double ms = isMarks
                            ? valueLine.getUnit().toMilliseconds(valueLine.getValue(i))
                            : timeLine.getUnit().toMilliseconds(timeLine.getValue(i));
                    double v  = isMarks ? 1.0 : valueLine.getValue(i);
                    double x  = ((ms - fromMs) / msRange) * width;
                    double y  = height - ((v - minV) / vRange) * height;
                    if (x >= -3 && x <= width + 3)
                        g2.fillOval((int) x - 2, (int) y - 2, 4, 4);
                }

                // label
                g2.setColor(lineColor);
                g2.setFont(g2.getFont().deriveFont(Font.BOLD).deriveFont((float) Settings.getDefaultFontSize()));
                String label = data.getLineCount() > 2 ? data.getName() + " / " + valueLine.getName() : data.getName();
                g2.drawString(label, 4, Settings.getDefaultFontSize() * (1 + vIdx * 1.4f));
            }
        }
    }

    /**
     * Linearly interpolate the value of a line at the given millisecond position.
     * Uses the first TIME line as the x-axis, or the line itself for MARKS.
     * @param data     the AnnotationData
     * @param lineIdx  the index of the value line
     * @param ms       the position in milliseconds
     * @return interpolated value, or null if out of range
     */
    private static Double interpolate(AnnotationData data, int lineIdx, double ms) {
        AnnotationLine valueLine = data.getLine(lineIdx);
        boolean isMarks = valueLine.getType() == AnnotationLine.Type.MARKS;
        int timeIdx = data.getFirstLineIndexOfType(AnnotationLine.Type.TIME);
        AnnotationLine timeLine = (!isMarks && timeIdx >= 0) ? data.getLine(timeIdx) : null;

        int count = (timeLine != null) ? Math.min(timeLine.size(), valueLine.size()) : valueLine.size();
        if (count == 0) return null;

        // build ms array on-the-fly
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

    /**
     * Get the millisecond timestamp for row i from the appropriate line.
     * @param timeLine the TIME line (can be null for MARKS)
     * @param valueLine
     * @param isMarks whether the value line is of type MARKS (in which case it provides the timestamps itself)
     * @param i the row index
     * @return the timestamp in milliseconds
     */
    private static double msAt(AnnotationLine timeLine, AnnotationLine valueLine, boolean isMarks, int i) {
        if (isMarks) return valueLine.getUnit().toMilliseconds(valueLine.getValue(i));
        return timeLine.getUnit().toMilliseconds(timeLine.getValue(i));
    }

    /**
     * Extend the context menu with annotation visibility toggles.
     * @param e the mouse event that triggered the context menu
     * @return
     */
    @Override
    protected WebPopupMenu getContextMenu(MouseEvent e) {
        WebPopupMenu menu = super.getContextMenu(e);
        ArrayList<AnnotationData> all = this.parent.getAnnotations();
        if (!all.isEmpty()) {
            WebMenu annotationMenu = new WebMenu("Annotations");
            for (AnnotationData data : all) {
                WebCheckBoxMenuItem item = new WebCheckBoxMenuItem(data.getName(), data.isVisible());
                item.addActionListener(ae -> {
                    data.setVisible(!data.isVisible());
                    this.updateNoDataLabel();
                    this.repaint();
                });
                annotationMenu.add(item);
            }
            menu.addSeparator();
            menu.add(annotationMenu);
        }
        return menu;
    }

    /**
     * on mouse enter event
     * @param e
     */
    @Override public void mouseEntered(MouseEvent e) { if (this.parent.getAudio() != null) super.mouseEntered(e); }
    /**
     * on mouse exit event
     * @param e
     */
    @Override public void mouseExited(MouseEvent e)  { if (this.parent.getAudio() != null) super.mouseExited(e);  }
    /**
     * on mouse move event
     * @param e
     */
    @Override public void mouseMoved(MouseEvent e)   { if (this.parent.getAudio() != null) super.mouseMoved(e);   }
    /**
     * on mouse drag event
     * @param e
     */
    @Override public void mouseDragged(MouseEvent e) { if (this.parent.getAudio() != null) super.mouseDragged(e); }

    /**
     * on mouse click event
     * @param e
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON3) {
            // context menu is always available (annotation visibility can be toggled without audio)
            this.getContextMenu(e).show(this, e.getX() - 25, e.getY());
        } else if (this.parent.getAudio() != null) {
            super.mouseClicked(e);
        }
    }
}
