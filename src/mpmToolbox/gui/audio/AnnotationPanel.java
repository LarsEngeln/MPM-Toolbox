package mpmToolbox.gui.audio;

import com.alee.laf.menu.WebMenu;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import mpmToolbox.gui.Settings;
import mpmToolbox.supplementary.Tools;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;

/**
 * This panel visualizes annotation data that is associated with an audio recording.
 * Each annotation is a (timestamp in milliseconds, value) pair.
 * The data is displayed as a line graph aligned with the other audio panels (waveform, spectrogram).
 * @author Lars Engeln
 */
public class AnnotationPanel extends PianoRollPanel {
    private final ArrayList<AnnotationData> annotations = new ArrayList<>();  // all loaded annotation datasets
    private AnnotationData activeData = null;                               // the currently displayed dataset

    // cached flat view of the active dataset for rendering
    private ArrayList<AnnotationEntry> annotationEntries = new ArrayList<>();
    private double minValue = 0.0;
    private double maxValue = 1.0;
    private String label = "Annotation";

    /**
     * constructor
     * @param parent
     */
    protected AnnotationPanel(AudioDocumentData parent) {
        super(parent, "No annotation data available.");
        this.setOpaque(true);
        this.setBackground(Color.BLACK);

        // generate some test data as initial dataset
        this.addAnnotationData(this.buildTestData());
    }

    /**
     * Builds a test AnnotationData object.
     * @return the test data
     */
    private AnnotationData buildTestData() {
        ArrayList<AnnotationEntry> entries = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            double ms = i * 100.0;
            double value = 0.5 + 0.4 * Math.sin(ms / 1000.0 * Math.PI)
                    + 0.1 * Math.sin(ms / 300.0 * Math.PI);
            entries.add(new AnnotationEntry(ms, value));
        }
        return new AnnotationData("Test Annotation", entries);
    }

    /**
     * Add a new AnnotationData object to the store and display it.
     * @param data the annotation data to add
     */
    public void addAnnotationData(AnnotationData data) {
        if (data == null)
            return;
        this.annotations.add(data);
        this.setActiveData(data);
    }

    /**
     * Returns all stored annotation datasets.
     * @return the data store
     */
    public ArrayList<AnnotationData> getAnnotations() {
        return this.annotations;
    }

    /**
     * Set the currently displayed annotation dataset.
     * @param data the dataset to display
     */
    public void setActiveData(AnnotationData data) {
        this.activeData = data;
        if (data != null) {
            this.annotationEntries = data.getEntries();
            this.label = data.getName();
        } else {
            this.annotationEntries = new ArrayList<>();
            this.label = "Annotation";
        }
        this.updateValueRange();
        this.updateNoDataLabel();
        this.repaint();
    }

    /**
     * Set annotation data from external source.
     * @param annotations list of annotation entries
     * @param label the label/name for this annotation
     */
    public void setAnnotations(ArrayList<AnnotationEntry> annotations, String label) {
        // check if an AnnotationData with this label already exists → replace it
        for (AnnotationData existing : this.annotations) {
            if (existing.getName().equals(label)) {
                existing.replaceEntries(annotations);
                this.setActiveData(existing);
                return;
            }
        }
        // otherwise add a new one
        this.addAnnotationData(new AnnotationData(label, annotations));
    }

    /**
     * Show or hide the "no data" label depending on whether annotations are available.
     */
    private void updateNoDataLabel() {
        if (this.annotationEntries.isEmpty()) {
            this.add(this.noData);
        } else {
            this.remove(this.noData);
        }
    }

    /**
     * Recompute min/max value range from the current annotations.
     */
    private void updateValueRange() {
        if (this.annotationEntries.isEmpty()) {
            this.minValue = 0.0;
            this.maxValue = 1.0;
            return;
        }
        this.minValue = Double.MAX_VALUE;
        this.maxValue = -Double.MAX_VALUE;
        for (AnnotationEntry entry : this.annotationEntries) {
            if (entry.value < this.minValue) this.minValue = entry.value;
            if (entry.value > this.maxValue) this.maxValue = entry.value;
        }
        double margin = (this.maxValue - this.minValue) * 0.05;
        if (margin == 0.0) margin = 0.5;
        this.minValue -= margin;
        this.maxValue += margin;
    }

    /**
     * Get the list of active annotations.
     * @return the annotation entries
     */
    public ArrayList<AnnotationEntry> getAnnotationEntries() {
        return this.annotationEntries;
    }

    /**
     * draw the component
     * @param g
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (this.parent.getAudio() == null || this.annotationEntries.isEmpty())
            return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        this.drawPianoRoll(g2);
        this.drawAnnotationCurve(g2);
        this.drawValueAxis(g2);
        this.drawPlaybackCursor(g2);

        if (this.drawMouseCursor(g2)) {
            long sampleIndex = this.parent.getMouseCursor().getSample();
            double millisec = Tools.round(((double) sampleIndex / this.parent.getAudio().getFrameRate()) * 1000.0, 2);
            Double interpolatedValue = this.getInterpolatedValue(millisec);
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString("Milliseconds: " + millisec, 2, Settings.getDefaultFontSize());
            if (interpolatedValue != null)
                g2.drawString(this.label + ": " + Tools.round(interpolatedValue, 4), 2, Settings.getDefaultFontSize() * 2.25f);
        }
    }

    /**
     * Draw the annotation data as a line graph.
     * @param g2
     */
    private void drawAnnotationCurve(Graphics2D g2) {
        if (this.annotationEntries.size() < 2)
            return;

        int width = this.getWidth();
        int height = this.getHeight();
        double fromMs = ((double) this.parent.getLeftmostSample() / this.parent.getAudio().getFrameRate()) * 1000.0;
        double toMs = ((double) this.parent.getRightmostSample() / this.parent.getAudio().getFrameRate()) * 1000.0;
        double msRange = toMs - fromMs;

        if (msRange <= 0.0)
            return;

        double valueRange = this.maxValue - this.minValue;
        if (valueRange <= 0.0)
            valueRange = 1.0;

        Path2D.Double fillPath = new Path2D.Double();
        Path2D.Double linePath = new Path2D.Double();
        boolean started = false;

        for (AnnotationEntry entry : this.annotationEntries) {
            double x = ((entry.milliseconds - fromMs) / msRange) * width;
            double y = height - ((entry.value - this.minValue) / valueRange) * height;
            if (!started) {
                fillPath.moveTo(x, height);
                fillPath.lineTo(x, y);
                linePath.moveTo(x, y);
                started = true;
            } else {
                fillPath.lineTo(x, y);
                linePath.lineTo(x, y);
            }
        }

        if (started) {
            double lastX = ((this.annotationEntries.get(this.annotationEntries.size() - 1).milliseconds - fromMs) / msRange) * width;
            fillPath.lineTo(lastX, height);
            fillPath.closePath();

            g2.setColor(new Color(0, 180, 255, 40));
            g2.fill(fillPath);

            Stroke defaultStroke = g2.getStroke();
            g2.setStroke(new BasicStroke(2.0f));
            g2.setColor(new Color(0, 180, 255, 200));
            g2.draw(linePath);
            g2.setStroke(defaultStroke);

            g2.setColor(new Color(0, 200, 255, 255));
            for (AnnotationEntry entry : this.annotationEntries) {
                double x = ((entry.milliseconds - fromMs) / msRange) * width;
                double y = height - ((entry.value - this.minValue) / valueRange) * height;
                if (x >= -3 && x <= width + 3)
                    g2.fillOval((int) x - 2, (int) y - 2, 4, 4);
            }
        }
    }

    /**
     * Draw the value axis labels on the left side.
     * @param g2
     */
    private void drawValueAxis(Graphics2D g2) {
        int height = this.getHeight();
        double valueRange = this.maxValue - this.minValue;
        if (valueRange <= 0.0)
            return;

        Font smallFont = g2.getFont().deriveFont((float) Settings.getDefaultFontSize() * 0.85f);
        g2.setFont(smallFont);

        int numTicks = 5;
        for (int i = 0; i <= numTicks; i++) {
            double fraction = (double) i / numTicks;
            double value = this.minValue + fraction * valueRange;
            int y = height - (int) (fraction * height);

            g2.setColor(new Color(100, 100, 100, 80));
            g2.drawLine(0, y, this.getWidth(), y);

            g2.setColor(new Color(180, 180, 180, 150));
            g2.drawString(String.valueOf(Tools.round(value, 2)), 4, y - 2);
        }

        g2.setColor(new Color(0, 180, 255, 180));
        g2.setFont(g2.getFont().deriveFont(Font.BOLD));
        g2.drawString(this.label, 4, Settings.getDefaultFontSize());
    }

    /**
     * Get the linearly interpolated value at a given millisecond position.
     * @param milliseconds the position in milliseconds
     * @return the interpolated value, or null if outside range
     */
    private Double getInterpolatedValue(double milliseconds) {
        if (this.annotationEntries.isEmpty())
            return null;

        if (milliseconds <= this.annotationEntries.get(0).milliseconds)
            return this.annotationEntries.get(0).value;

        if (milliseconds >= this.annotationEntries.get(this.annotationEntries.size() - 1).milliseconds)
            return this.annotationEntries.get(this.annotationEntries.size() - 1).value;

        for (int i = 0; i < this.annotationEntries.size() - 1; i++) {
            AnnotationEntry a = this.annotationEntries.get(i);
            AnnotationEntry b = this.annotationEntries.get(i + 1);
            if (milliseconds >= a.milliseconds && milliseconds <= b.milliseconds) {
                double t = (milliseconds - a.milliseconds) / (b.milliseconds - a.milliseconds);
                return a.value + t * (b.value - a.value);
            }
        }

        return null;
    }

    /**
     * Build a context menu with the standard entries plus an "Annotation" submenu
     * for selecting which dataset to display.
     * @param e
     * @return the context menu
     */
    @Override
    protected WebPopupMenu getContextMenu(MouseEvent e) {
        WebPopupMenu menu = super.getContextMenu(e);

        if (!this.annotations.isEmpty()) {
            WebMenu annotationMenu = new WebMenu("Annotation");
            for (AnnotationData data : this.annotations) {
                WebMenuItem item = new WebMenuItem(data.getName());
                boolean isActive = data == this.activeData;
                item.setEnabled(!isActive);                             // grey out the currently active one
                if (isActive)
                    item.setText("\u2022 " + data.getName());           // bullet for active dataset
                item.addActionListener(actionEvent -> this.setActiveData(data));
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
    @Override
    public void mouseEntered(MouseEvent e) {
        if (this.parent.getAudio() == null)
            return;
        super.mouseEntered(e);
    }

    /**
     * on mouse exit event
     * @param e
     */
    @Override
    public void mouseExited(MouseEvent e) {
        if (this.parent.getAudio() == null)
            return;
        super.mouseExited(e);
    }

    /**
     * on mouse move event
     * @param e
     */
    @Override
    public void mouseMoved(MouseEvent e) {
        if (this.parent.getAudio() == null)
            return;
        super.mouseMoved(e);
    }

    /**
     * on mouse click event
     * @param e
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        if (this.parent.getAudio() == null)
            return;

        switch (e.getButton()) {
            case MouseEvent.BUTTON1:
                super.mouseClicked(e);
                break;
            case MouseEvent.BUTTON3:
                WebPopupMenu menu = this.getContextMenu(e);
                menu.show(this, e.getX() - 25, e.getY());
                break;
        }
    }

    /**
     * on mouse drag event
     * @param e
     */
    @Override
    public void mouseDragged(MouseEvent e) {
        if (this.parent.getAudio() != null)
            super.mouseDragged(e);
    }

    /**
     * A single annotation data point: a timestamp in milliseconds and a value.
     * @author Axel Berndt
     */
    public static class AnnotationEntry {
        public final double milliseconds;
        public final double value;

        /**
         * constructor
         * @param milliseconds the timestamp in milliseconds
         * @param value the annotation value
         */
        public AnnotationEntry(double milliseconds, double value) {
            this.milliseconds = milliseconds;
            this.value = value;
        }
    }
}
