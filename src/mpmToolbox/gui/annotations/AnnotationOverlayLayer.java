package mpmToolbox.gui.annotations;

import com.alee.laf.panel.WebPanel;
import mpmToolbox.projectData.annotations.AnnotationLink;
import mpmToolbox.projectData.annotations.AnnotationPlacement;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Hosts the annotation overlays for one tab. The manager owns the overlay panels and presentation state;
 * this layer only forwards configuration and input events.
 */
public class AnnotationOverlayLayer extends WebPanel {
    /**
     * Maps a placement to the coordinate system of the hosting tab.
     */
    public interface Projection {
        Point anchor(AnnotationPlacement placement);

        void moveTo(AnnotationPlacement placement, Point anchor);

        default double xUnitToPixel() {
            return 1.0;
        }

        default double yUnitToPixel() {
            return 1.0;
        }

        /**
         * @return the factor by which the content (font) of the overlays is scaled
         */
        default double contentScale() {
            return 1.0;
        }
    }

    private final Component parent;
    private final AnnotationManager manager;

    public AnnotationOverlayLayer(Component parent, AnnotationManager manager, AnnotationPlacement.TabType tabType) {
        super();
        this.parent = parent;
        this.manager = manager;
        this.setOpaque(false);
        this.setLayout(null);
        this.setAlignmentX(0.5f);
        this.setAlignmentY(0.5f);
        this.manager.registerOverlayLayer(this, tabType);
        this.installListeners();
        manager.addChangeListener(this::refresh);
        manager.addActiveListener(this::refresh);
    }

    public void setFilter(Predicate<AnnotationPlacement> filter) {
        this.manager.setOverlayFilter(this, filter);
    }

    public void setVisibleWhen(BooleanSupplier visibleWhen) {
        this.manager.setOverlayVisibleWhen(this, visibleWhen);
    }

    public void setContentArea(Supplier<Rectangle> contentArea) {
        this.manager.setOverlayContentArea(this, contentArea);
    }

    public void setProjection(Projection projection) {
        this.manager.setOverlayProjection(this, projection);
    }

    public void setLinkResolver(Function<MouseEvent, AnnotationLink> linkResolver) {
        this.manager.setOverlayLinkResolver(this, linkResolver);
    }

    public AnnotationManager getManager() {
        return this.manager;
    }

    public Component getOwner() {
        return this.parent;
    }

    private void refresh() {
        this.setCursor(this.manager.isLinking() ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : null);
        this.sync();
        this.repaint();
        this.parent.repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        this.sync();
        super.paintComponent(g);
    }

    public void sync() {
        this.manager.syncOverlayLayer(this);
    }

    public AnnotationOverlayPanel getOverlayPanel(AnnotationPlacement placement) {
        return this.manager.getOverlayPanel(this, placement);
    }

    private boolean consumedByLinking(MouseEvent e, boolean isClick) {
        if (!this.manager.isLinking()) {
            return false;
        }
        if (isClick) {
            if (SwingUtilities.isRightMouseButton(e)) {
                this.manager.cancelLinking();
            } else if (SwingUtilities.isLeftMouseButton(e)) {
                this.manager.finishLinking(this.manager.resolveOverlayLink(this, SwingUtilities.convertMouseEvent(this, e, this.parent)));
            }
        }
        return true;
    }

    void moveTo(AnnotationPlacement placement, Point anchor) {
        this.manager.moveOverlayPlacement(this, placement, anchor);
    }

    void forwardToOwner(MouseEvent mouseEvent, Component source) {
        this.parent.dispatchEvent(SwingUtilities.convertMouseEvent(source, mouseEvent, this.parent));
    }

    private void installListeners() {
        MouseAdapter passThrough = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (consumedByLinking(e, true)) {
                    return;
                }
                forwardToOwner(e, AnnotationOverlayLayer.this);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (consumedByLinking(e, false)) {
                    return;
                }
                forwardToOwner(e, AnnotationOverlayLayer.this);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (consumedByLinking(e, false)) {
                    return;
                }
                forwardToOwner(e, AnnotationOverlayLayer.this);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                forwardToOwner(e, AnnotationOverlayLayer.this);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (consumedByLinking(e, false)) {
                    return;
                }
                forwardToOwner(e, AnnotationOverlayLayer.this);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                forwardToOwner(e, AnnotationOverlayLayer.this);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                forwardToOwner(e, AnnotationOverlayLayer.this);
            }
        };

        this.addMouseListener(passThrough);
        this.addMouseMotionListener(passThrough);
        this.addMouseWheelListener(mouseWheelEvent -> {
            Point p = SwingUtilities.convertPoint(AnnotationOverlayLayer.this, mouseWheelEvent.getPoint(), this.parent);
            MouseWheelEvent converted = new MouseWheelEvent(
                    this.parent,
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
                    mouseWheelEvent.getPreciseWheelRotation());
            this.parent.dispatchEvent(converted);
        });
    }
}
