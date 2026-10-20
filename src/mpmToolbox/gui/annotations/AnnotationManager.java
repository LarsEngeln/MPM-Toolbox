package mpmToolbox.gui.annotations;

import meico.mei.Helper;
import mpmToolbox.gui.ProjectPane;
import mpmToolbox.gui.Settings;
import mpmToolbox.projectData.SvgData;
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
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
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
    private static final double DEFAULT_SCORE_WIDTH = 0.3;      // relative to the page image size
    private static final double DEFAULT_SCORE_HEIGHT = 0.15;
    private static final double DEFAULT_RELATIVE_WIDTH = 0.3;
    private static final double DEFAULT_RELATIVE_HEIGHT = 0.2;

    private final ProjectPane projectPane;
    private final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> activeListeners = new CopyOnWriteArrayList<>();
    private final IdentityHashMap<AnnotationOverlayLayer, OverlayLayerState> overlayLayers = new IdentityHashMap<>();
    private AnnotationData hoveredAnnotation = null;
    private AnnotationData selectedAnnotation = null;
    private AnnotationData linkingAnnotation = null;
    private AnnotationLink hoveredLink = null;
    private boolean overlayZoomEnabled = true;

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

    /**
     * @return true if overlays that live in zoomable views (the score) are scaled together with the zoom; otherwise they keep their screen size
     */
    public boolean isOverlayZoomEnabled() {
        return this.overlayZoomEnabled;
    }

    public void setOverlayZoomEnabled(boolean enabled) {
        this.overlayZoomEnabled = enabled;
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

        double contentScale = state.projection.contentScale();
        FontMetrics metrics = layer.getOwner().getFontMetrics(AnnotationOverlayPanel.deriveOverlayFont(layer.getOwner().getFont(), contentScale));
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
                overlayPanel.setContentScale(contentScale);
                Point anchor = projection.anchor(placement);
                Rectangle bounds = AnnotationOverlayPanel.computeOverlayBounds(
                        annotation, placement, metrics, anchor.x, anchor.y,
                        projection.xUnitToPixel(), projection.yUnitToPixel(), contentScale);
                if (overlayPanel.setOverlayBounds(bounds)) {
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
            this.hoveredLink = null;
            this.notifyActiveChanged();
        }
    }

    /**
     * @return the link that is hovered in an annotation's overlay panel, or null
     */
    public AnnotationLink getHoveredLink() {
        return this.hoveredLink;
    }

    public void setHoveredLink(AnnotationLink link) {
        if (this.hoveredLink != link) {
            this.hoveredLink = link;
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

    /**
     * Draw the lines from the hovered annotation's boxes to its linked score nodes.
     * @param g2 the graphics context in image coordinates
     * @param page the score page
     * @param viewTransform the image-to-screen transform of the score view; the overlay boxes live in screen coordinates
     */
    public void drawAnnotationLinkLines(Graphics2D g2, ScorePage page, AffineTransform viewTransform) {
        AnnotationData activeAnnotation = this.getHovered();
        if (activeAnnotation == null) {
            return;
        }

        AnnotationOverlayLayer scoreLayer = this.projectPane.getScoreFrame().getScoreDisplay().getAnnotationOverlayLayer();
        for (AnnotationPlacement placement : activeAnnotation.getPlacements()) {
            if (placement.getTabType() != AnnotationPlacement.TabType.SCORE) {
                continue;
            }
            AnnotationOverlayPanel overlayPanel = scoreLayer.getOverlayPanel(placement);
            if ((overlayPanel == null) || !overlayPanel.isShowing()) {
                continue;
            }
            Rectangle2D box;
            try {
                box = viewTransform.createInverse().createTransformedShape(overlayPanel.getBounds()).getBounds2D();
            } catch (NoninvertibleTransformException e) {
                continue;
            }
            this.drawAnnotationLinkLines(g2, page, activeAnnotation, box);
        }
    }

    /**
     * @param box the annotation box in image coordinates
     */
    public void drawAnnotationLinkLines(Graphics2D g2, ScorePage page, AnnotationData annotation, Rectangle2D box) {
        Stroke oldStroke = g2.getStroke();
        Object oldAntialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (AnnotationLink link : annotation.getLinks()) {
            Element linkedScoreElement = this.resolveLinkedScoreElement(link, page);
            if (linkedScoreElement == null) {
                continue;
            }
            ScoreNode linkedNode = page.getNode(linkedScoreElement);
            if (linkedNode == null) {
                continue;
            }

            boolean hovered = (link == this.hoveredLink);
            Color base = (link.getType() == AnnotationLink.LinkType.MSM) ? Settings.scoreNoteColor : Settings.scorePerformanceColor;
            g2.setColor(hovered ? Settings.scoreSvgColorHighlighted : new Color(base.getRed(), base.getGreen(), base.getBlue(), 220));
            g2.setStroke(new BasicStroke(hovered ? 9f : 5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            Point2D from = new Point2D.Double(box.getX(), box.getY());
            g2.draw(new Line2D.Double(from, new Point2D.Double(linkedNode.getX(), linkedNode.getY())));
        }

        g2.setStroke(oldStroke);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, (oldAntialiasing != null) ? oldAntialiasing : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    /**
     * @return the text to display for a link: for SVG links the title, else the first desc of the SVG element, else the id
     */
    public String getLinkText(AnnotationLink link) {
        String text = link.getType() + ": ";
        if (link.getType() == AnnotationLink.LinkType.SVG) {
            for (ScorePage page : this.projectPane.getScore().getAllPages()) {
                for (SvgData svg : page.getSvgs()) {
                    if (svg.hasElement(link.getTarget())) {
                        return text + svg.getDisplayText(link.getTarget());
                    }
                }
            }
        }
        return text + link.getTarget();
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
