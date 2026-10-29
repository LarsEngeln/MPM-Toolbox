package mpmToolbox.gui.annotations;

import com.alee.laf.button.WebButton;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.text.WebTextArea;
import com.alee.laf.text.WebTextField;
import com.alee.managers.style.StyleId;
import mpmToolbox.gui.Settings;
import mpmToolbox.projectData.annotations.AnnotationData;
import mpmToolbox.projectData.annotations.AnnotationLink;
import mpmToolbox.projectData.annotations.AnnotationPlacement;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Presents one placement of an annotation: a multi-line, scrollable text with a fixed, user-adjustable size.
 * The size is stored in the placement (right and bottom edge can be dragged).
 * @author Lars Engeln
 */
public class AnnotationOverlayPanel extends WebPanel {
    private static final int OVERLAY_MIN_WIDTH_PX = 80;
    private static final int OVERLAY_MAX_WIDTH_PX = 420;
    private static final int OVERLAY_MIN_HEIGHT_PX = 36;
    private static final int COLLAPSED_WIDTH_PX = 22;
    private static final int COLLAPSED_HEIGHT_PX = 18;
    private static final String COLLAPSED_TEXT = "...";
    private static final int OVERLAY_PADDING_X_PX = 4;
    private static final int OVERLAY_PADDING_Y_PX = 8;
    private static final int RESIZE_ZONE_PX = 5;                // the grabbable area at the right and bottom edge (border + padding)
    private static final int BORDER_WIDTH_PX = 2;
    private static final Color OVERLAY_FILL = new Color(220, 215, 215, 190);
    private static final int OVERLAY_HOVER_ALPHA = 245;
    private static final Color OVERLAY_BORDER = new Color(130, 130, 120, 230);
    private static final Color OVERLAY_TEXT = new Color(20, 20, 20, 255);
    private static final String OVERLAY_TITLE = "Annotation";

    private static class MeasureInputFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attributes) throws BadLocationException {
            this.replace(fb, offset, 0, text, attributes);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attributes) throws BadLocationException {
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            String replacement = (text == null) ? "" : text;
            String candidate = current.substring(0, offset) + replacement + current.substring(offset + length);
            if (candidate.matches("-?[0-9]+|\\?|") || candidate.equals("-")) {
                fb.replace(offset, length, replacement, attributes);
            } else {
                fb.replace(0, fb.getDocument().getLength(), "", attributes);
            }
        }
    }

    /**
     * The scrolled view: text and links together, as wide as the viewport.
     */
    private static class ScrollableContent extends WebPanel implements Scrollable {
        ScrollableContent() {
            super(new BorderLayout());
            this.setOpaque(false);
        }

        @Override
        public java.awt.Dimension getPreferredScrollableViewportSize() {
            return this.getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return (this.getParent() instanceof JViewport) && (this.getParent().getHeight() > this.getPreferredSize().height);
        }
    }

    private final AnnotationOverlayLayer layer;
    private final AnnotationManager manager;
    private final WebTextArea textArea = new WebTextArea(StyleId.textareaTransparent);
    private final WebPanel header = new WebPanel(new BorderLayout());
    private final WebLabel titleLabel = new WebLabel(OVERLAY_TITLE);
    private final WebButton collapseButton = new WebButton(StyleId.buttonUndecorated, "<");
    private final WebPanel linksPanel = new WebPanel();
    private final ScrollableContent content = new ScrollableContent();
    private final WebScrollPane scrollPane = new WebScrollPane(StyleId.scrollpaneTransparent, this.content);
    private String lastLinksKey = null;
    private MouseAdapter mouseHandler = null;
    private AnnotationData annotation;
    private AnnotationPlacement placement;
    private Rectangle expandedBounds;
    private boolean collapsed;
    private Boolean lastActive = null;
    private double xUnitToPixel = 1.0;
    private double yUnitToPixel = 1.0;
    private double contentScale = 3.0;

    public AnnotationOverlayPanel(AnnotationOverlayLayer layer, AnnotationData annotation, AnnotationPlacement placement) {
        super();
        this.layer = layer;
        this.manager = layer.getManager();
        this.initializeUi();
        this.updateAnnotation(annotation, placement);
        this.installMouseHandlers();
    }

    private void initializeUi() {
        this.setLayout(new BorderLayout());
        this.setOpaque(false);
        this.setBackground(OVERLAY_FILL);

        this.textArea.setEditable(false);
        this.textArea.setFocusable(false);
        this.textArea.setLineWrap(true);
        this.textArea.setWrapStyleWord(true);
        this.textArea.setOpaque(false);
        this.textArea.setForeground(OVERLAY_TEXT);
        this.textArea.setMargin(new Insets(0, OVERLAY_PADDING_X_PX - BORDER_WIDTH_PX, 0, 0));

        this.header.setOpaque(false);
        this.header.setPadding(0, OVERLAY_PADDING_X_PX, 0, OVERLAY_PADDING_X_PX);
        this.titleLabel.setForeground(OVERLAY_TEXT);
        this.titleLabel.setFont(this.textArea.getFont());
        this.collapseButton.setFocusable(false);
        this.collapseButton.setToolTipText("Collapse annotation");
        this.collapseButton.setForeground(OVERLAY_TEXT);
        this.collapseButton.setPadding(0, 0, 0, 0);
        this.collapseButton.setMinimumSize(new Dimension(24, 20));
        this.collapseButton.setPreferredSize(new Dimension(24, 20));
        this.collapseButton.setBoldFont();
        this.collapseButton.addActionListener(e -> this.setCollapsed(true));
        this.header.add(this.titleLabel, BorderLayout.CENTER);
        this.header.add(this.collapseButton, BorderLayout.EAST);

        this.scrollPane.setOpaque(false);
        this.scrollPane.getViewport().setOpaque(false);
        this.scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        this.content.add(this.textArea, BorderLayout.CENTER);
        this.content.add(this.linksPanel, BorderLayout.SOUTH);
        this.add(this.header, BorderLayout.NORTH);
        this.add(this.scrollPane, BorderLayout.CENTER);

        this.linksPanel.setLayout(new BoxLayout(this.linksPanel, BoxLayout.Y_AXIS));
        this.linksPanel.setOpaque(false);

        this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        this.textArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        this.setToolTipText(OVERLAY_TITLE);

        this.setCollapsed(true);
    }

    public void updateAnnotation(AnnotationData annotation, AnnotationPlacement placement) {
        this.annotation = annotation;
        this.placement = placement;
        this.refreshVisuals();
    }

    /**
     * Set how many pixels one unit of the placement's width and height are in the hosting tab.
     */
    public void setUnitToPixel(double xUnitToPixel, double yUnitToPixel) {
        if (xUnitToPixel > 0.0) {
            this.xUnitToPixel = xUnitToPixel;
        }
        if (yUnitToPixel > 0.0) {
            this.yUnitToPixel = yUnitToPixel;
        }
    }

    /**
     * Set the factor by which the font of the content is scaled.
     */
    public void setContentScale(double contentScale) {
        if ((contentScale > 0.0) && (this.contentScale != contentScale)) {
            this.contentScale = contentScale;
            this.lastLinksKey = null;           // the link labels derive their font from the text area
            this.lastActive = null;             // the border padding depends on the scale
            this.textArea.setMargin(new Insets(0, Math.max(0, (int) Math.round(OVERLAY_PADDING_X_PX * contentScale) - BORDER_WIDTH_PX), 0, 0));
            this.header.setPadding(0, this.scaledPadding(), 0, this.scaledPadding());
            this.refreshVisuals();
        }
    }

    boolean setOverlayBounds(Rectangle bounds) {
        Rectangle previousBounds = this.getBounds();
        if ((this.expandedBounds != null) && this.expandedBounds.equals(bounds)) {
            return false;
        }
        this.expandedBounds = new Rectangle(bounds);
        this.applyDisplayBounds();
        return !previousBounds.equals(this.getBounds());
    }

    private void setCollapsed(boolean collapsed) {
        if (this.collapsed == collapsed) {
            return;
        }
        this.collapsed = collapsed;
        this.header.setVisible(!collapsed);
        this.scrollPane.setVisible(!collapsed);
        this.setToolTipText(collapsed
                ? ((this.annotation == null) ? OVERLAY_TITLE : this.annotation.getDisplayTitle())
                : null);
        this.applyDisplayBounds();
        this.revalidate();
        this.repaint();
        if (collapsed && (this.manager.getHovered() == this.annotation)) {
            this.keepHoverWhilePointerInside();
        }
    }

    /**
     * Collapsing hides the child under the cursor and resizes the panel, which makes Swing report an exit.
     * If the pointer is still over the (collapsed) panel afterwards, it is hovered; otherwise the hover ends.
     */
    private void keepHoverWhilePointerInside() {
        SwingUtilities.invokeLater(() -> {
            if (this.collapsed && this.isPointerInside()) {
                this.manager.setHovered(this.annotation);
            } else if (this.manager.getHovered() == this.annotation) {
                this.manager.setHovered(null);
            }
        });
    }

    private boolean isPointerInside() {
        java.awt.PointerInfo info = java.awt.MouseInfo.getPointerInfo();
        if ((info == null) || !this.isShowing()) {
            return false;
        }
        Point p = new Point(info.getLocation());
        SwingUtilities.convertPointFromScreen(p, this);
        return this.contains(p);
    }

    /**
     * @return the point all link lines start at: the center of the collapsed panel, which stays fixed relative to the top left corner when expanded
     */
    public java.awt.geom.Point2D getLinkAnchor() {
        return new java.awt.geom.Point2D.Double(this.getX() + COLLAPSED_WIDTH_PX / 2.0, this.getY() + COLLAPSED_HEIGHT_PX / 2.0);
    }

    private void applyDisplayBounds() {
        if (this.expandedBounds == null) {
            return;
        }
        Rectangle bounds = new Rectangle(this.expandedBounds);
        if (this.collapsed) {
            bounds.width = COLLAPSED_WIDTH_PX;
            bounds.height = COLLAPSED_HEIGHT_PX;
        }
        this.setBounds(bounds);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (this.collapsed) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Settings.annotationCollapsedColor);
            g2.fill(new RoundRectangle2D.Double(0, 0, this.getWidth() - 1, this.getHeight() - 1, 10, 10));
            g2.setColor(this.isActive() ? Settings.annotationActiveBorderColor : OVERLAY_BORDER);
            g2.draw(new RoundRectangle2D.Double(0, 0, this.getWidth() - 1, this.getHeight() - 1, 10, 10));
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g2.setColor(Color.WHITE);
            FontMetrics metrics = g2.getFontMetrics();
            int textX = (this.getWidth() - metrics.stringWidth(COLLAPSED_TEXT)) / 2;
            int textY = (this.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.drawString(COLLAPSED_TEXT, textX, textY);
            g2.dispose();
            return;
        }
        // translucent fill must be painted on a non-opaque panel, otherwise Swing skips clearing what is behind it
        Graphics2D g2 = (Graphics2D) g.create();
        Color fill = this.getBackground();
        if (this.manager.getHovered() == this.annotation) {
            fill = new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), OVERLAY_HOVER_ALPHA);
        }
        g2.setColor(fill);
        g2.fillRect(0, 0, this.getWidth(), this.getHeight());
        g2.dispose();
    }

    private void refreshVisuals() {
        this.textArea.setFont(deriveOverlayFont(this.layer.getOwner().getFont(), this.contentScale));
        this.titleLabel.setFont(this.textArea.getFont().deriveFont(Font.BOLD));
        this.collapseButton.setFont(this.textArea.getFont());
        String title = (this.annotation == null) ? OVERLAY_TITLE : this.annotation.getDisplayTitle();
        this.titleLabel.setText(title);
        if (this.collapsed) {
            this.setToolTipText(title);
        }
        boolean active = this.isActive();
        if (this.lastActive == null || this.lastActive != active) {
            this.lastActive = active;
            this.setBorder(this.createBorder(active));
        }
        this.refreshLinks();
        String text = (this.annotation == null) ? "" : this.annotation.getDisplayText();
        if (!text.equals(this.textArea.getText())) {
            this.textArea.setText(text);
            this.textArea.setCaretPosition(0);
        }
    }

    private boolean isLinkingThis() {
        return (this.annotation != null) && (this.manager.getLinkingAnnotation() == this.annotation);
    }

    /**
     * List the links of the annotation, each with a "-" to remove it, and a "+" at the end to add a new one by clicking its target.
     */
    private void refreshLinks() {
        StringBuilder key = new StringBuilder();
        if (this.annotation != null) {
            for (AnnotationLink link : this.annotation.getLinks()) {
                key.append(this.manager.getLinkText(link)).append(';');
            }
        }
        String keyString = key.toString() + (this.isLinkingThis() ? "|linking" : "");
        if (keyString.equals(this.lastLinksKey)) {
            return;
        }
        this.lastLinksKey = keyString;

        this.linksPanel.removeAll();
        if (this.annotation != null) {
            for (int i = 0; i < this.annotation.getLinks().size(); ++i) {
                AnnotationLink link = this.annotation.getLinks().get(i);
                WebPanel row = new WebPanel(new BorderLayout());
                row.setOpaque(false);
                row.setPadding(0, this.scaledPadding(), 0, this.scaledPadding());
                WebLabel label = new WebLabel(this.manager.getLinkText(link));
                label.setForeground(OVERLAY_TEXT);
                label.setFont(this.textArea.getFont().deriveFont(Math.max(this.textArea.getFont().getSize2D() - (float) (3.0 * this.contentScale), (float) (10.0 * this.contentScale))));
                row.add(label, BorderLayout.CENTER);
                final int index = i;
                WebButton remove = new WebButton(StyleId.buttonUndecorated, "-");
                remove.setFocusable(false);
                remove.setPadding(0, this.scaledPadding(), 0, this.scaledPadding());
                remove.setFont(this.textArea.getFont());
                remove.setForeground(OVERLAY_TEXT);
                remove.addActionListener(e -> {
                    manager.setHoveredLink(null);
                    this.annotation.removeLink(index);
                    this.manager.notifyChanged();
                });
                row.add(remove, BorderLayout.EAST);
                MouseAdapter linkHover = new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        manager.setHoveredLink(link);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        Point inRow = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), row);
                        if (!row.contains(inRow) && (manager.getHoveredLink() == link)) {
                            manager.setHoveredLink(null);
                        }
                    }
                };
                row.addMouseListener(linkHover);
                label.addMouseListener(linkHover);
                this.linksPanel.add(row);
                this.makeDraggable(row);
            }
        }
        WebButton add = new WebButton(StyleId.buttonUndecorated, this.isLinkingThis() ? "cancel adding Link" : "add Link +");
        add.setFocusable(false);
        add.setPadding(this.scaledPadding(), this.scaledPadding(), this.scaledPadding(), this.scaledPadding());
        add.setFont(this.textArea.getFont());
        add.setForeground(OVERLAY_TEXT);
        add.addActionListener(e -> {
            if (this.isLinkingThis()) {
                this.manager.cancelLinking();
            } else {
                this.manager.startLinking(this.annotation);
            }
        });
        WebPanel addRow = new WebPanel(new BorderLayout());
        addRow.setOpaque(false);
        addRow.add(add, BorderLayout.EAST);
        this.linksPanel.add(addRow);
        this.revalidate();
    }

    private int scaledPadding() {
        return (int) Math.round(Settings.paddingInDialogs * this.contentScale);
    }

    private boolean isActive() {
        return this.manager.isActive(this.annotation);
    }

    public static Font deriveOverlayFont(Font base, double scale) {
        Font font = (base != null) ? base : UIManager.getFont("Label.font");
        if (font == null) {
            font = new Font(Font.DIALOG, Font.PLAIN, 12);
        }
        return font.deriveFont((float) (Math.max(font.getSize2D() + 2.0f, 14.0f) * scale));
    }

    private Border createBorder(boolean active) {
        int line = active ? BORDER_WIDTH_PX + 1 : BORDER_WIDTH_PX;
        int pad = RESIZE_ZONE_PX - line;
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(active ? Settings.annotationActiveBorderColor : OVERLAY_BORDER, line, true),
                BorderFactory.createEmptyBorder(Math.max(0, (int) Math.round(OVERLAY_PADDING_Y_PX * this.contentScale) - line), pad, pad, pad));
    }

    @Override
    public boolean contains(int x, int y) {
        return new RoundRectangle2D.Double(0, 0, this.getWidth(), this.getHeight(), 12, 12).contains(x, y);
    }

    /**
     * Compute the bounds of an overlay. Placements with a stored size keep this fixed size,
     * all others get a single line preferred size.
     */
    public static Rectangle computeOverlayBounds(AnnotationData annotation,
                                                 AnnotationPlacement placement,
                                                 FontMetrics metrics,
                                                 int anchorX,
                                                 int anchorY,
                                                 double xUnitToPixel,
                                                 double yUnitToPixel,
                                                 double contentScale) {
        int padX = (int) Math.round(OVERLAY_PADDING_X_PX * contentScale);
        int padY = (int) Math.round(OVERLAY_PADDING_Y_PX * contentScale);
        int preferredWidth = Math.max(
                OVERLAY_MIN_WIDTH_PX,
                Math.min(OVERLAY_MAX_WIDTH_PX, Math.max(
                        metrics.stringWidth(annotation.getDisplayText()),
                        metrics.stringWidth(annotation.getDisplayTitle())) + (padX * 2)));
        int preferredHeight = Math.max(OVERLAY_MIN_HEIGHT_PX, metrics.getHeight() + (padY * 2));

        int width = preferredWidth;
        int height = preferredHeight;

        if ((placement.getWidth() > 0.0) && (placement.getHeight() > 0.0)) {
            width = Math.max(OVERLAY_MIN_WIDTH_PX, (int) Math.round(placement.getWidth() * xUnitToPixel));
            height = Math.max(OVERLAY_MIN_HEIGHT_PX, (int) Math.round(placement.getHeight() * yUnitToPixel));
        }

        return new Rectangle(anchorX, anchorY, width, height);
    }

    /**
     * Let the user edit the (multi-line) text of an annotation.
     * @return true if the text was changed
     */
    public static boolean editText(Component parent, AnnotationData annotation) {
        WebTextArea editor = new WebTextArea(annotation.getText(), 8, 36);
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);
        WebTextField title = new WebTextField(annotation.getTitle());
        title.setPadding(Settings.paddingInDialogs);
        WebTextField part = new WebTextField(annotation.getPart());
        part.setPadding(Settings.paddingInDialogs);
        WebTextField measureFrom = new WebTextField(annotation.getMeasureFrom());
        WebTextField measureTo = new WebTextField(annotation.getMeasureTo());
        measureFrom.setPadding(Settings.paddingInDialogs);
        measureTo.setPadding(Settings.paddingInDialogs);
        measureFrom.setColumns(5);
        measureTo.setColumns(5);
        ((AbstractDocument) measureFrom.getDocument()).setDocumentFilter(new MeasureInputFilter());
        ((AbstractDocument) measureTo.getDocument()).setDocumentFilter(new MeasureInputFilter());
        title.setToolTipText("Enter the annotation title.");
        part.setToolTipText("Enter the locus or leave it empty.");
        String measureHint = "Enter an integer, ? for unknown, or leave empty.";
        measureFrom.setToolTipText(measureHint);
        measureTo.setToolTipText(measureHint);
        measureTo.setEnabled(!measureFrom.getText().isEmpty());
        measureFrom.getDocument().addDocumentListener(new DocumentListener() {
            private void updateMeasureToEnabled() {
                measureTo.setEnabled(!measureFrom.getText().isEmpty());
                if (!measureTo.isEnabled()) {
                    SwingUtilities.invokeLater(() -> measureTo.setText(""));
                }
            }

            @Override
            public void insertUpdate(DocumentEvent event) {
                this.updateMeasureToEnabled();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                this.updateMeasureToEnabled();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                this.updateMeasureToEnabled();
            }
        });
        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        titleRow.add(new JLabel("Title"), BorderLayout.WEST);
        titleRow.add(title, BorderLayout.CENTER);
        JPanel partRow = new JPanel(new BorderLayout(8, 0));
        partRow.add(new JLabel("Locus:"), BorderLayout.WEST);
        partRow.add(part, BorderLayout.CENTER);
        JPanel measureRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        measureRow.add(new JLabel("Measure:"));
        measureRow.add(measureFrom);
        measureRow.add(new JLabel("-"));
        measureRow.add(measureTo);

        fields.add(titleRow);
        fields.add(partRow);
        fields.add(measureRow);
        JPanel form = new JPanel(new BorderLayout(0, 8));
        form.add(fields, BorderLayout.NORTH);
        form.add(new WebScrollPane(editor), BorderLayout.CENTER);

        WebButton ok = new WebButton("OK");
        WebButton cancel = new WebButton("Cancel");
        ok.setPadding(Settings.paddingInDialogs);
        cancel.setPadding(Settings.paddingInDialogs);

        while (true) {
            JOptionPane pane = new JOptionPane(form, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[]{ok, cancel}, ok);
            for (java.awt.event.ActionListener listener : ok.getActionListeners()) {
                ok.removeActionListener(listener);
            }
            for (java.awt.event.ActionListener listener : cancel.getActionListeners()) {
                cancel.removeActionListener(listener);
            }
            ok.addActionListener(e -> pane.setValue(JOptionPane.OK_OPTION));
            cancel.addActionListener(e -> pane.setValue(JOptionPane.CANCEL_OPTION));
            javax.swing.JDialog dialog = pane.createDialog(parent, "Edit annotation");
            dialog.setVisible(true);
            dialog.dispose();
            if (!Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue())) {
                return false;
            }
            annotation.setTitle(title.getText());
            annotation.setPart(part.getText());
            annotation.setMeasureFrom(measureFrom.getText());
            annotation.setMeasureTo(measureTo.getText());
            annotation.setText(editor.getText());
            return true;
        }
    }

    private void resizeTo(int width, int height) {
        if (this.placement == null) {
            return;
        }
        this.placement.setWidth(Math.max(OVERLAY_MIN_WIDTH_PX, width) / this.xUnitToPixel);
        this.placement.setHeight(Math.max(OVERLAY_MIN_HEIGHT_PX, height) / this.yUnitToPixel);
        this.annotation.touch();
    }

    /**
     * Mouse events on the text area would not reach this panel, so they are handed over.
     */
    /**
     * Every part of the panel except buttons and scroll bars hands its mouse events to the panel's handler, so the whole panel can be dragged.
     */
    private void makeDraggable(Component component) {
        if ((this.mouseHandler == null) || (component instanceof AbstractButton) || (component instanceof JScrollBar)) {
            return;
        }
        this.forwardMouseEventsOf(component, this.mouseHandler);
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                this.makeDraggable(child);
            }
        }
    }

    private void forwardMouseEventsOf(Component child, MouseAdapter target) {
        MouseAdapter forwarder = new MouseAdapter() {
            private void forward(MouseEvent e) {
                MouseEvent converted = SwingUtilities.convertMouseEvent(child, e, AnnotationOverlayPanel.this);
                switch (e.getID()) {
                    case MouseEvent.MOUSE_PRESSED:
                        target.mousePressed(converted);
                        break;
                    case MouseEvent.MOUSE_RELEASED:
                        target.mouseReleased(converted);
                        break;
                    case MouseEvent.MOUSE_CLICKED:
                        target.mouseClicked(converted);
                        break;
                    case MouseEvent.MOUSE_MOVED:
                        target.mouseMoved(converted);
                        break;
                    case MouseEvent.MOUSE_DRAGGED:
                        target.mouseDragged(converted);
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                forward(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                forward(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                forward(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                forward(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                forward(e);
            }
        };
        child.addMouseListener(forwarder);
        child.addMouseMotionListener(forwarder);
    }

    private void installMouseHandlers() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            private double dragOffsetX = 0.0;
            private double dragOffsetY = 0.0;
            private boolean dragging = false;
            private boolean resizeRight = false;
            private boolean resizeBottom = false;
            private boolean resizing = false;
            private int resizeStartWidth = 0;
            private int resizeStartHeight = 0;
            private int resizeStartScreenX = 0;
            private int resizeStartScreenY = 0;

            private void updateCursor(MouseEvent e) {
                if (collapsed) {
                    this.resizeRight = false;
                    this.resizeBottom = false;
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    textArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    return;
                }
                boolean right = e.getX() >= getWidth() - RESIZE_ZONE_PX;
                boolean bottom = e.getY() >= getHeight() - RESIZE_ZONE_PX;
                this.resizeRight = right;
                this.resizeBottom = bottom;
                int cursor = Cursor.HAND_CURSOR;
                if (right && bottom) {
                    cursor = Cursor.SE_RESIZE_CURSOR;
                } else if (right) {
                    cursor = Cursor.E_RESIZE_CURSOR;
                } else if (bottom) {
                    cursor = Cursor.S_RESIZE_CURSOR;
                }
                Cursor c = Cursor.getPredefinedCursor(cursor);
                setCursor(c);
                textArea.setCursor(c);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                manager.setHovered(annotation);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (resizing || dragging || contains(e.getX(), e.getY()) || isPointerInside()) {      // still inside, e.g. just moved on top of the text area
                    return;
                }
                if (manager.getHovered() == annotation) {
                    manager.setHovered(null);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                manager.setHovered(annotation);
                if (!this.resizing) {
                    this.updateCursor(e);
                }
            }

            /**
             * While another annotation is adding a link, a click on this annotation is the link target.
             */
            private boolean consumedByLinking(MouseEvent e, boolean finish) {
                AnnotationData linking = manager.getLinkingAnnotation();
                if ((linking == null) || (linking == annotation)) {
                    return false;
                }
                if (finish && SwingUtilities.isLeftMouseButton(e)) {
                    manager.finishLinking(new AnnotationLink(AnnotationLink.LinkType.ANNOTATION, annotation.getId()));
                } else if (finish && SwingUtilities.isRightMouseButton(e)) {
                    manager.cancelLinking();
                }
                return true;
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (this.consumedByLinking(e, false)) {
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    layer.forwardToOwner(e, AnnotationOverlayPanel.this);
                    return;
                }
                this.updateCursor(e);
                manager.setSelected(annotation);
                layer.getOwner().requestFocusInWindow();
                if (collapsed) {
                    return;
                }
                if (this.resizeRight || this.resizeBottom) {
                    this.resizing = true;
                    this.resizeStartWidth = getWidth();
                    this.resizeStartHeight = getHeight();
                    this.resizeStartScreenX = e.getXOnScreen();
                    this.resizeStartScreenY = e.getYOnScreen();
                    return;
                }
                this.dragOffsetX = e.getX();
                this.dragOffsetY = e.getY();
                this.dragging = true;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (this.resizing && (placement != null)) {
                    int width = this.resizeRight ? this.resizeStartWidth + (e.getXOnScreen() - this.resizeStartScreenX) : this.resizeStartWidth;
                    int height = this.resizeBottom ? this.resizeStartHeight + (e.getYOnScreen() - this.resizeStartScreenY) : this.resizeStartHeight;
                    resizeTo(width, height);
                    manager.notifyChanged();
                    return;
                }
                if (!this.dragging || (placement == null)) {
                    return;
                }
                Point p = SwingUtilities.convertPoint(AnnotationOverlayPanel.this, e.getPoint(), layer);
                layer.moveTo(placement, new Point((int) Math.round(p.x - this.dragOffsetX), (int) Math.round(p.y - this.dragOffsetY)));
                annotation.touch();
                manager.notifyChanged();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    layer.forwardToOwner(e, AnnotationOverlayPanel.this);
                    return;
                }
                if (this.resizing || this.dragging) {
                    this.resizing = false;
                    this.dragging = false;
                    manager.notifyChanged();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (this.consumedByLinking(e, true)) {
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    layer.forwardToOwner(e, AnnotationOverlayPanel.this);
                    return;
                }
                if (collapsed) {
                    setCollapsed(false);
                    return;
                }
                if (e.getClickCount() < 2) {
                    return;
                }
                if (editText(layer.getOwner(), annotation)) {
                    manager.notifyChanged();
                }
            }
        };

        this.addMouseListener(mouseAdapter);
        this.addMouseMotionListener(mouseAdapter);
        this.mouseHandler = mouseAdapter;
        this.makeDraggable(this.scrollPane);
        this.makeDraggable(this.header);
        this.addMouseWheelListener(mouseWheelEvent -> {
            Component owner = layer.getOwner();
            Point p = SwingUtilities.convertPoint(AnnotationOverlayPanel.this, mouseWheelEvent.getPoint(), owner);
            owner.dispatchEvent(new MouseWheelEvent(
                    owner,
                    mouseWheelEvent.getID(),
                    mouseWheelEvent.getWhen(),
                    mouseWheelEvent.getModifiersEx(),
                    p.x,
                    p.y,
                    mouseWheelEvent.getXOnScreen(),
                    mouseWheelEvent.getYOnScreen(),
                    mouseWheelEvent.getClickCount(),
                    mouseWheelEvent.isPopupTrigger(),
                    mouseWheelEvent.getScrollType(),
                    mouseWheelEvent.getScrollAmount(),
                    mouseWheelEvent.getWheelRotation(),
                    mouseWheelEvent.getPreciseWheelRotation()));
        });
    }
}