package mpmToolbox.gui.annotations;

import com.alee.laf.button.WebButton;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.text.WebTextArea;
import com.alee.managers.style.StyleId;
import mpmToolbox.gui.Settings;
import mpmToolbox.projectData.annotations.AnnotationData;
import mpmToolbox.projectData.annotations.AnnotationLink;
import mpmToolbox.projectData.annotations.AnnotationPlacement;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JOptionPane;
import javax.swing.JScrollBar;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Presents one placement of an annotation: a multi-line, scrollable text with a fixed, user-adjustable size.
 * The size is stored in the placement (right and bottom edge can be dragged).
 */
public class AnnotationOverlayPanel extends WebPanel {
    private static final int OVERLAY_MIN_WIDTH_PX = 80;
    private static final int OVERLAY_MAX_WIDTH_PX = 420;
    private static final int OVERLAY_MIN_HEIGHT_PX = 36;
    private static final int OVERLAY_PADDING_X_PX = 12;
    private static final int OVERLAY_PADDING_Y_PX = 8;
    private static final int RESIZE_ZONE_PX = 8;                // the grabbable area at the right and bottom edge (border + padding)
    private static final int BORDER_WIDTH_PX = 2;
    private static final Color OVERLAY_FILL = new Color(220, 215, 215, 190);
    private static final Color OVERLAY_BORDER = new Color(130, 130, 120, 230);
    private static final Color OVERLAY_BORDER_ACTIVE = new Color(240, 210, 70, 230);
    private static final Color OVERLAY_TEXT = new Color(20, 20, 20, 255);

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
    private final WebPanel linksPanel = new WebPanel();
    private final ScrollableContent content = new ScrollableContent();
    private final WebScrollPane scrollPane = new WebScrollPane(StyleId.scrollpaneTransparent, this.content);
    private String lastLinksKey = null;
    private MouseAdapter mouseHandler = null;
    private AnnotationData annotation;
    private AnnotationPlacement placement;
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
        this.setOpaque(true);
        this.setBackground(OVERLAY_FILL);

        this.textArea.setEditable(false);
        this.textArea.setFocusable(false);
        this.textArea.setLineWrap(true);
        this.textArea.setWrapStyleWord(true);
        this.textArea.setOpaque(false);
        this.textArea.setForeground(OVERLAY_TEXT);
        this.textArea.setMargin(new Insets(0, OVERLAY_PADDING_X_PX - BORDER_WIDTH_PX, 0, 0));

        this.scrollPane.setOpaque(false);
        this.scrollPane.getViewport().setOpaque(false);
        this.scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        this.content.add(this.textArea, BorderLayout.CENTER);
        this.content.add(this.linksPanel, BorderLayout.SOUTH);
        this.add(this.scrollPane, BorderLayout.CENTER);

        this.linksPanel.setLayout(new BoxLayout(this.linksPanel, BoxLayout.Y_AXIS));
        this.linksPanel.setOpaque(false);

        this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        this.textArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
            this.refreshVisuals();
        }
    }

    private void refreshVisuals() {
        this.textArea.setFont(deriveOverlayFont(this.layer.getOwner().getFont(), this.contentScale));
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

    /**
     * List the links of the annotation, each with a "-" to remove it, and a "+" at the end to add a new one by clicking its target.
     */
    private void refreshLinks() {
        StringBuilder key = new StringBuilder();
        if (this.annotation != null) {
            for (AnnotationLink link : this.annotation.getLinks()) {
                key.append(link.getType()).append(':').append(link.getTarget()).append(';');
            }
        }
        String keyString = key.toString();
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
                WebLabel label = new WebLabel(link.getType() + ": " + link.getTarget());
                label.setForeground(OVERLAY_TEXT);
                label.setFont(this.textArea.getFont().deriveFont(Math.max(this.textArea.getFont().getSize2D() - (float) (3.0 * this.contentScale), (float) (10.0 * this.contentScale))));
                row.add(label, BorderLayout.CENTER);
                final int index = i;
                WebButton remove = new WebButton(StyleId.buttonUndecorated, "-");
                remove.setFocusable(false);
                remove.setPadding(this.scaledPadding(), this.scaledPadding(), this.scaledPadding(), this.scaledPadding());
                remove.setFont(this.textArea.getFont());
                remove.setForeground(OVERLAY_TEXT);
                remove.addActionListener(e -> {
                    this.annotation.removeLink(index);
                    this.manager.notifyChanged();
                });
                row.add(remove, BorderLayout.EAST);
                this.linksPanel.add(row);
                this.makeDraggable(row);
            }
        }
        WebButton add = new WebButton(StyleId.buttonUndecorated, "add Link +");
        add.setFocusable(false);
        add.setPadding(this.scaledPadding(), this.scaledPadding(), this.scaledPadding(), this.scaledPadding());
        add.setFont(this.textArea.getFont());
        add.setForeground(OVERLAY_TEXT);
        add.addActionListener(e -> this.manager.startLinking(this.annotation));
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
                BorderFactory.createLineBorder(active ? OVERLAY_BORDER_ACTIVE : OVERLAY_BORDER, line, true),
                BorderFactory.createEmptyBorder(Math.max(0, (int) Math.round(OVERLAY_PADDING_Y_PX * this.contentScale) - line), pad, pad, pad));
    }

    @Override
    public boolean contains(int x, int y) {
        if (this.manager.isLinking()) {
            return false;                       // the click is meant for the tab below
        }
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
                Math.min(OVERLAY_MAX_WIDTH_PX, metrics.stringWidth(annotation.getDisplayText()) + (padX * 2)));
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
        int result = JOptionPane.showConfirmDialog(parent, new WebScrollPane(editor), "Edit annotation text", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return false;
        }
        annotation.setText(editor.getText());
        return true;
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
                if (resizing || dragging || contains(e.getX(), e.getY())) {      // still inside, e.g. just moved on top of the text area
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

            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    layer.forwardToOwner(e, AnnotationOverlayPanel.this);
                    return;
                }
                this.updateCursor(e);
                manager.setSelected(annotation);
                layer.getOwner().requestFocusInWindow();
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
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    layer.forwardToOwner(e, AnnotationOverlayPanel.this);
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