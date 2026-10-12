package mpmToolbox.gui.annotations;

import meico.mei.Helper;
import mpmToolbox.gui.ProjectPane;
import mpmToolbox.gui.Settings;
import mpmToolbox.projectData.annotations.AnnotationData;
import mpmToolbox.projectData.annotations.AnnotationLink;
import mpmToolbox.projectData.annotations.AnnotationPlacement;
import mpmToolbox.projectData.score.ScoreNode;
import mpmToolbox.projectData.score.ScorePage;
import nu.xom.Element;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Manages the project's annotations: the data (add, remove, create), the visibility of the overlays,
 * the hover/selection state that is shared by all tabs, and the overlay panels that render each placement.
 * Views (tabs, overlay layers, the annotation tree) only register as listeners and display what is managed here.
 */
public class AnnotationManager {
    private static final double DEFAULT_SCORE_WIDTH = 190.0;
    private static final double DEFAULT_SCORE_HEIGHT = 40.0;
    private static final double DEFAULT_RELATIVE_WIDTH = 0.22;
    private static final double DEFAULT_RELATIVE_HEIGHT = 0.12;

    private final ProjectPane projectPane;
    private final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> activeListeners = new CopyOnWriteArrayList<>();
    private final IdentityHashMap<AnnotationOverlayLayer, OverlayLayerState> overlayLayers = new IdentityHashMap<>();
    private AnnotationData hoveredAnnotation = null;
    private AnnotationData selectedAnnotation = null;
    private AnnotationData linkingAnnotation = null;

    private static class OverlayLayerState {
        private final AnnotationPlacement.TabType tabType;
        private final IdentityHashMap<AnnotationPlacement, AnnotationOverlayPanel> overlayPanels = new IdentityHashMap<>();
        private Predicate<AnnotationPlacement> filter = placement -> true;
        private BooleanSupplier visibleWhen = () -> true;
        private Supplier<Rectangle> contentArea = () -> new Rectangle();
        private AnnotationOverlayLayer.Projection projection;
        private Function<MouseEvent, AnnotationLink> linkResolver = event -> null;

        private OverlayLayerState(AnnotationPlacement.TabType tabType) {
            this.tabType = tabType;
        }
    }

    public AnnotationManager(ProjectPane projectPane) {
        this.projectPane = projectPane;
    }

    /**
     * @param listener is called whenever annotation data or the overlay visibility changed
     */
    public void addChangeListener(Runnable listener) {
        this.changeListeners.add(listener);
    }

    /**
     * @param listener is called whenever the hovered or the selected annotation changed
     */
    public void addActiveListener(Runnable listener) {
        this.activeListeners.add(listener);
    }

    public void notifyChanged() {
        for (Runnable listener : this.changeListeners) {
            listener.run();
        }
    }

    private void notifyActiveChanged() {
        for (Runnable listener : this.activeListeners) {
            listener.run();
        }
    }

    // ---- data ----

    public ArrayList<AnnotationData> getAnnotations() {
        return this.projectPane.getProjectData().getAnnotations();
    }

    public boolean add(AnnotationData annotation) {
        boolean added = this.projectPane.getProjectData().addAnnotation(annotation);
        if (added) {
            this.notifyChanged();
        }
        return added;
    }

    public boolean remove(AnnotationData annotation) {
        boolean removed = this.projectPane.getProjectData().removeAnnotation(annotation);
        if (removed) {
            if (this.hoveredAnnotation == annotation) {
                this.hoveredAnnotation = null;
            }
            if (this.selectedAnnotation == annotation) {
                this.selectedAnnotation = null;
            }
            this.notifyChanged();
            this.notifyActiveChanged();
        }
        return removed;
    }

    public boolean isOverlayVisible() {
        return this.projectPane.getProjectData().isAnnotationOverlayVisible();
    }

    public void setOverlayVisible(boolean visible) {
        this.projectPane.getProjectData().setAnnotationOverlayVisible(visible);
        this.notifyChanged();
    }

    // ---- overlay layers ----

    public void registerOverlayLayer(AnnotationOverlayLayer layer, AnnotationPlacement.TabType tabType) {
        OverlayLayerState state = new OverlayLayerState(tabType);
        state.contentArea = () -> new Rectangle(0, 0, Math.max(1, layer.getWidth()), Math.max(1, layer.getHeight()));
        state.projection = this.createDefaultProjection(state);
        this.overlayLayers.put(layer, state);
    }

    public void setOverlayFilter(AnnotationOverlayLayer layer, Predicate<AnnotationPlacement> filter) {
        this.getOverlayLayerState(layer).filter = (filter != null) ? filter : placement -> true;
    }

    public void setOverlayVisibleWhen(AnnotationOverlayLayer layer, BooleanSupplier visibleWhen) {
        this.getOverlayLayerState(layer).visibleWhen = (visibleWhen != null) ? visibleWhen : () -> true;
    }

    public void setOverlayContentArea(AnnotationOverlayLayer layer, Supplier<Rectangle> contentArea) {
        OverlayLayerState state = this.getOverlayLayerState(layer);
        state.contentArea = (contentArea != null) ? contentArea : () -> new Rectangle(0, 0, Math.max(1, layer.getWidth()), Math.max(1, layer.getHeight()));
        if (state.projection == null) {
            state.projection = this.createDefaultProjection(state);
        }
    }

    public void setOverlayProjection(AnnotationOverlayLayer layer, AnnotationOverlayLayer.Projection projection) {
        OverlayLayerState state = this.getOverlayLayerState(layer);
        state.projection = (projection != null) ? projection : this.createDefaultProjection(state);
    }

    public void setOverlayLinkResolver(AnnotationOverlayLayer layer, Function<MouseEvent, AnnotationLink> linkResolver) {
        this.getOverlayLayerState(layer).linkResolver = (linkResolver != null) ? linkResolver : event -> null;
    }

    public AnnotationOverlayPanel getOverlayPanel(AnnotationOverlayLayer layer, AnnotationPlacement placement) {
        OverlayLayerState state = this.overlayLayers.get(layer);
        return (state != null) ? state.overlayPanels.get(placement) : null;
    }

    public void syncOverlayLayer(AnnotationOverlayLayer layer) {
        OverlayLayerState state = this.getOverlayLayerState(layer);
        if (!this.isOverlayVisible() || !state.visibleWhen.getAsBoolean()) {
            if (!state.overlayPanels.isEmpty()) {
                layer.removeAll();
                state.overlayPanels.clear();
                layer.revalidate();
                layer.repaint();
            }
            return;
        }

        FontMetrics metrics = layer.getOwner().getFontMetrics(AnnotationOverlayPanel.deriveOverlayFont(layer.getOwner().getFont()));
        ArrayList<AnnotationPlacement> shown = new ArrayList<>();

        for (AnnotationData annotation : new ArrayList<>(this.getAnnotations())) {
            for (AnnotationPlacement placement : annotation.getPlacements()) {
                if ((placement.getTabType() != state.tabType) || !state.filter.test(placement)) {
                    continue;
                }
                shown.add(placement);

                AnnotationOverlayPanel overlayPanel = state.overlayPanels.get(placement);
                if (overlayPanel == null) {
                    overlayPanel = new AnnotationOverlayPanel(layer, annotation, placement);
                    state.overlayPanels.put(placement, overlayPanel);
                    layer.add(overlayPanel);
                } else {
                    overlayPanel.updateAnnotation(annotation, placement);
                }

                AnnotationOverlayLayer.Projection projection = state.projection;
                overlayPanel.setUnitToPixel(projection.xUnitToPixel(), projection.yUnitToPixel());
                Point anchor = projection.anchor(placement);
                Rectangle bounds = AnnotationOverlayPanel.computeOverlayBounds(
                        annotation, placement, metrics, anchor.x, anchor.y,
                        projection.xUnitToPixel(), projection.yUnitToPixel());
                if (!bounds.equals(overlayPanel.getBounds())) {
                    overlayPanel.setBounds(bounds);
                    overlayPanel.validate();
                }
            }
        }

        ArrayList<AnnotationPlacement> stale = new ArrayList<>();
        for (Map.Entry<AnnotationPlacement, AnnotationOverlayPanel> entry : state.overlayPanels.entrySet()) {
            if (!shown.contains(entry.getKey())) {
                layer.remove(entry.getValue());
                stale.add(entry.getKey());
            }
        }
        for (AnnotationPlacement placement : stale) {
            state.overlayPanels.remove(placement);
        }
        layer.revalidate();
    }

    public void moveOverlayPlacement(AnnotationOverlayLayer layer, AnnotationPlacement placement, Point anchor) {
        this.getOverlayLayerState(layer).projection.moveTo(placement, anchor);
    }

    public AnnotationLink resolveOverlayLink(AnnotationOverlayLayer layer, MouseEvent event) {
        return this.getOverlayLayerState(layer).linkResolver.apply(event);
    }

    // ---- creation ----

    public AnnotationData create(AnnotationPlacement.TabType tab, double x, double y, String pagePath) {
        AnnotationData annotation = new AnnotationData("");
        if (tab == AnnotationPlacement.TabType.SCORE) {
            annotation.addPlacement(AnnotationPlacement.rect(tab, x, y, DEFAULT_SCORE_WIDTH, DEFAULT_SCORE_HEIGHT, pagePath));
        } else {
            annotation.addPlacement(AnnotationPlacement.rect(tab, x, y, DEFAULT_RELATIVE_WIDTH, DEFAULT_RELATIVE_HEIGHT, ""));
        }
        return this.add(annotation) ? annotation : null;
    }

    public AnnotationData create(AnnotationPlacement.TabType tab, double x, double y) {
        return this.create(tab, x, y, "");
    }

    // ---- linking ----

    public void startLinking(AnnotationData annotation) {
        this.linkingAnnotation = annotation;
        this.notifyChanged();
    }

    public void cancelLinking() {
        if (this.linkingAnnotation != null) {
            this.linkingAnnotation = null;
            this.notifyChanged();
        }
    }

    public boolean isLinking() {
        return this.linkingAnnotation != null;
    }

    public void finishLinking(AnnotationLink link) {
        if ((this.linkingAnnotation == null) || (link == null)) {
            return;
        }
        this.linkingAnnotation.addLink(link);
        this.linkingAnnotation = null;
        this.notifyChanged();
    }

    // ---- hover and selection ----

    public AnnotationData getHovered() {
        return this.hoveredAnnotation;
    }

    public AnnotationData getSelected() {
        return this.selectedAnnotation;
    }

    public AnnotationData getActive() {
        return (this.hoveredAnnotation != null) ? this.hoveredAnnotation : this.selectedAnnotation;
    }

    public boolean isActive(AnnotationData annotation) {
        return (annotation != null) && ((annotation == this.hoveredAnnotation) || (annotation == this.selectedAnnotation));
    }

    public void setHovered(AnnotationData annotation) {
        if (this.hoveredAnnotation != annotation) {
            this.hoveredAnnotation = annotation;
            this.notifyActiveChanged();
        }
    }

    public void setSelected(AnnotationData annotation) {
        if (this.selectedAnnotation != annotation) {
            this.selectedAnnotation = annotation;
            this.notifyActiveChanged();
        }
    }

    public void clearActive() {
        if ((this.hoveredAnnotation != null) || (this.selectedAnnotation != null)) {
            this.hoveredAnnotation = null;
            this.selectedAnnotation = null;
            this.notifyActiveChanged();
        }
    }

    // ---- rendering ----

    public void drawAnnotationLinkLines(Graphics2D g2, ScorePage page) {
        AnnotationData activeAnnotation = this.getHovered();
        if (activeAnnotation == null) {
            return;
        }

        AnnotationOverlayLayer scoreLayer = this.projectPane.getScoreFrame().getScoreDisplay().getAnnotationOverlayLayer();
        for (AnnotationData annotation : this.getAnnotations()) {
            if (annotation != activeAnnotation) {
                continue;
            }
            for (AnnotationPlacement placement : annotation.getPlacements()) {
                if (placement.getTabType() != AnnotationPlacement.TabType.SCORE) {
                    continue;
                }
                AnnotationOverlayPanel overlayPanel = scoreLayer.getOverlayPanel(placement);
                if ((overlayPanel == null) || !overlayPanel.isShowing()) {
                    continue;
                }
                this.drawAnnotationLinkLines(g2, page, annotation, overlayPanel.getBounds(), true);
            }
        }
    }

    public void drawAnnotationLinkLines(Graphics2D g2, ScorePage page, AnnotationData annotation, Rectangle box, boolean active) {
        int fromX = box.x + (box.width / 2);
        int fromY = box.y + (box.height / 2);
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(active ? 1.8f : 1.4f));
        g2.setColor(active ? Settings.scoreNoteColorHighlighted : new Color(255, 205, 80, 210));

        for (AnnotationLink link : annotation.getLinks()) {
            Element linkedScoreElement = this.resolveLinkedScoreElement(link, page);
            if (linkedScoreElement == null) {
                continue;
            }
            ScoreNode linkedNode = page.getNode(linkedScoreElement);
            if (linkedNode == null) {
                continue;
            }
            g2.drawLine(fromX, fromY, (int) linkedNode.getX(), (int) linkedNode.getY());
        }

        g2.setStroke(oldStroke);
    }

    public Element resolveLinkedScoreElement(AnnotationLink link, ScorePage page) {
        if (link == null) {
            return null;
        }
        String target = link.getTarget();
        if (((link.getType() != AnnotationLink.LinkType.MSM) && (link.getType() != AnnotationLink.LinkType.MPM)) || (target == null) || target.isEmpty()) {
            return null;
        }
        for (Map.Entry<Element, ScoreNode> entry : page.getAllEntries().entrySet()) {
            Element element = entry.getKey();
            if (target.equals(Helper.getAttributeValue("id", element))) {
                return element;
            }
        }
        return null;
    }

    private OverlayLayerState getOverlayLayerState(AnnotationOverlayLayer layer) {
        OverlayLayerState state = this.overlayLayers.get(layer);
        if (state == null) {
            throw new IllegalStateException("Annotation overlay layer was not registered.");
        }
        return state;
    }

    private AnnotationOverlayLayer.Projection createDefaultProjection(OverlayLayerState state) {
        return new AnnotationOverlayLayer.Projection() {
            @Override
            public Point anchor(AnnotationPlacement placement) {
                Rectangle area = state.contentArea.get();
                return new Point(
                        area.x + (int) Math.round(placement.getX() * area.width),
                        area.y + (int) Math.round(placement.getY() * area.height));
            }

            @Override
            public void moveTo(AnnotationPlacement placement, Point anchor) {
                Rectangle area = state.contentArea.get();
                placement.setX(Math.max(0.0, Math.min(1.0, (anchor.x - area.x) / (double) Math.max(1, area.width))));
                placement.setY(Math.max(0.0, Math.min(1.0, (anchor.y - area.y) / (double) Math.max(1, area.height))));
            }

            @Override
            public double xUnitToPixel() {
                return Math.max(1, state.contentArea.get().width);
            }

            @Override
            public double yUnitToPixel() {
                return Math.max(1, state.contentArea.get().height);
            }
        };
    }
}
