package mpmToolbox.gui.annotations;

import com.alee.laf.panel.WebPanel;
import mpmToolbox.projectData.annotations.AnnotationData;
import mpmToolbox.projectData.annotations.AnnotationLink;
import mpmToolbox.projectData.annotations.AnnotationPlacement;

import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Hosts the annotation overlays for one tab. The manager owns the overlay panels and presentation state;
 * this layer only forwards configuration and input events.
 * @author Lars Engeln
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
        this.putClientProperty(mpmToolbox.supplementary.FileDrop.SKIP_PROPERTY, Boolean.TRUE);      // the file drop must not replace the annotation drop target
        this.installDropHandler();
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

    /**
     * @param paneId identifies the pane within its tab; annotations dropped or created here are only shown in this pane
     */
    public void setPaneId(String paneId) {
        this.manager.setOverlayPaneId(this, paneId);
    }

    public void setPlacementInitializer(Consumer<AnnotationPlacement> initializer) {
        this.manager.setOverlayPlacementInitializer(this, initializer);
    }

    /**
     * Accept annotations that are dragged from the annotation tree and create a placement for them.
     */
    private void installDropHandler() {
        this.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDrop() && support.isDataFlavorSupported(AnnotationTransferable.FLAVOR) && manager.canDropInto(AnnotationOverlayLayer.this);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!this.canImport(support)) {
                    return false;
                }
                try {
                    AnnotationData annotation = (AnnotationData) support.getTransferable().getTransferData(AnnotationTransferable.FLAVOR);
                    return manager.dropAnnotation(AnnotationOverlayLayer.this, annotation, support.getDropLocation().getDropPoint());
                } catch (UnsupportedFlavorException | IOException | ClassCastException e) {
                    return false;
                }
            }
        });
        this.delegateOtherDropsToAncestors();
    }

    /**
     * The transfer handler's drop target covers the whole tab and would reject everything but annotations, which breaks the file drop (e.g. CSV).
     * Therefore, drags that do not carry an annotation are handed to the drop target of the closest ancestor.
     */
    private void delegateOtherDropsToAncestors() {
        final DropTarget swingTarget = this.getDropTarget();
        if (swingTarget == null) {
            return;
        }
        new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetListener() {
            private DropTarget current = null;

            private DropTarget ancestorTarget() {
                for (Component c = getParent(); c != null; c = c.getParent()) {
                    if (c.getDropTarget() != null) {
                        return c.getDropTarget();
                    }
                }
                return null;
            }

            private DropTarget choose(boolean annotation) {
                return annotation ? swingTarget : this.ancestorTarget();
            }

            @Override
            public void dragEnter(DropTargetDragEvent e) {
                this.current = this.choose(e.isDataFlavorSupported(AnnotationTransferable.FLAVOR));
                if (this.current != null) {
                    this.current.dragEnter(e);
                } else {
                    e.rejectDrag();
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent e) {
                if (this.current != null) {
                    this.current.dragOver(e);
                }
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent e) {
                if (this.current != null) {
                    this.current.dropActionChanged(e);
                }
            }

            @Override
            public void dragExit(DropTargetEvent e) {
                if (this.current != null) {
                    this.current.dragExit(e);
                }
                this.current = null;
            }

            @Override
            public void drop(DropTargetDropEvent e) {
                DropTarget target = (this.current != null) ? this.current : this.choose(e.isDataFlavorSupported(AnnotationTransferable.FLAVOR));
                this.current = null;
                if (target != null) {
                    target.drop(e);
                } else {
                    e.rejectDrop();
                }
            }
        });
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

    /**
     * While the layer is not supposed to show annotations it must not swallow mouse events,
     * so that components underneath (e.g. the spectrogram specification menu) receive them.
     */
    @Override
    public boolean contains(int x, int y) {
        return this.manager.canDropInto(this) && super.contains(x, y);
    }

    @Override
    protected void paintComponent(Graphics g) {
        this.sync();
        super.paintComponent(g);
        this.manager.drawAnnotationConnections(this, (java.awt.Graphics2D) g);
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
