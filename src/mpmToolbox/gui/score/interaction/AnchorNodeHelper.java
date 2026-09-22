package mpmToolbox.gui.score.interaction;

import meico.mei.Helper;
import meico.supplementary.KeyValue;
import meico.mpm.elements.Performance;
import mpmToolbox.gui.MeasureNumberLookup;
import mpmToolbox.gui.Settings;
import mpmToolbox.gui.mpmTree.MpmTree;
import mpmToolbox.gui.mpmTree.MpmTreeNode;
import mpmToolbox.gui.msmTree.MsmTree;
import mpmToolbox.gui.msmTree.MsmTreeNode;
import mpmToolbox.gui.score.ScoreDisplayPanel;
import mpmToolbox.projectData.score.ScoreNode;
import mpmToolbox.projectData.score.ScorePage;
import mpmToolbox.supplementary.orthantNeighborhoodGraph.ONGNode;
import nu.xom.Element;
import nu.xom.Node;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Map;

/**
 * Manages anchor node state for interaction modes.
 * Tracks the nearest overlay node to the mouse position and handles cursor updates.
 */
public class AnchorNodeHelper {
    private final ScoreDisplayPanel panel;
    private ScoreNode anchorNode = null;
    private final Point2D.Double anchorDragOffset = new Point2D.Double(0, 0);
    private double maxDistance = Double.MAX_VALUE;
    private boolean treeSelectionEnabled = true;

    /**
     * Creates the anchor node helper for the given score display panel.
     * @param panel the score display panel
     */
    public AnchorNodeHelper(ScoreDisplayPanel panel) {
        this.panel = panel;
    }

    /**
     * Gets the current anchor node.
     * @return the anchor node, or null if none is set
     */
    public ScoreNode getAnchorNode() {
        return this.anchorNode;
    }

    /**
     * Gets the drag offset from the click point to the anchor centre.
     * Used in selectEdit mode to preserve grab offset when dragging.
     * @return the anchor drag offset
     */
    public Point2D.Double getAnchorDragOffset() {
        return this.anchorDragOffset;
    }

    /**
     * Clears the anchor node and drag offset state.
     */
    public void reset() {
        this.anchorNode = null;
        this.anchorDragOffset.setLocation(0.0, 0.0);
    }

    /**
     * Draws the hover links for the current anchor node.
     * Articulation and ornament nodes use direct note references; all other
     * performance nodes are shown as horizontal date markers.
     * @param g2 the graphics context
     * @param color the line color
     */
    public void drawLinkedNodes(Graphics2D g2, Color color) {
        if (this.anchorNode == null) {
            return;
        }

        ScorePage scorePage = this.panel.getScorePage();
        if (scorePage == null) {
            return;
        }

        Composite savedComposite = g2.getComposite();
        Stroke savedStroke = g2.getStroke();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f));
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (Element element : this.anchorNode.getAssociatedElements()) {
            if ("note".equals(element.getLocalName())) {
                drawPerformanceLinks(scorePage, element, g2);
            } else {
                drawNoteLinks(scorePage, element, g2);
            }
        }

        g2.setStroke(savedStroke);
        g2.setComposite(savedComposite);
    }

    /**
     * Updates the anchor node according to its current value and the nearest neighbor to the specified point.
     * Uses a hysteresis threshold to avoid flickering when hovering between nearby nodes.
     * @param point2D the current mouse position in image coordinates
     */
    public void updateAnchor(Point2D point2D) {
        ScorePage scorePage = this.panel.getScorePage();
        KeyValue<ONGNode, Double> nearest = scorePage.findNearestNeighborOf(point2D.getX(), point2D.getY());
        if(nearest.getKey().distance(point2D) > this.maxDistance) {
            this.reset();
            return;
        }
        if (this.anchorNode == null) {
            this.anchorNode = (ScoreNode) nearest.getKey();

            selectNode(this.anchorNode);

            return;
        }
        if (this.anchorNode != nearest.getKey()) {
            double anchorDistance = this.anchorNode.distanceSq(point2D);
            if ((nearest.getValue() / anchorDistance) <= Settings.anchorSwitchOvershootThreshold) {
                this.anchorNode = (ScoreNode) nearest.getKey();

                selectNode(this.anchorNode);
            }
        }
    }

    /**
     * sets the maximum distance the nearest node is selected.
     * Only if the nearest node is closer than this distance, it will be selected as anchor node.
     * @param maxDistance the maximum distance
     */
    public void setMaxDistance(double maxDistance) {
        this.maxDistance = maxDistance;
    }

    /**
     * Enables or disables automatic tree selection when hover anchor changes.
     * @param enabled true to sync hover to MSM/MPM tree selection, false to keep tree selection unchanged
     */
    public void setTreeSelectionEnabled(boolean enabled) {
        this.treeSelectionEnabled = enabled;
    }

    /**
     * selects the given node
     * @param node the node to select
     */
    private void selectNode(ScoreNode node) {
        if (!this.treeSelectionEnabled) {
            return;
        }

        ArrayList<Element> elements = node.getAssociatedElements();
        if (elements.isEmpty())
            return;

        Element element = elements.get(0);

        MsmTree msmTree = panel.getScoreDocumentData().getProjectPane().getMsmTree();
        MsmTreeNode msmTreeNode = msmTree.findNode(element, true);
        if (msmTreeNode != null) {
            msmTree.setSelectionPath(msmTreeNode.getTreePath());
            return;
        }

        MpmTree mpmTree = panel.getScoreDocumentData().getProjectPane().getMpmTree();
        MpmTreeNode mpmTreeNode = mpmTree.findNode(element, true);
        if (mpmTreeNode != null) {
            mpmTree.setSelectionPath(mpmTreeNode.getTreePath());
            return;
        }
    }

    private void drawPerformanceLinks(ScorePage scorePage, Element noteElement, Graphics2D g2) {
        String noteId = getXmlId(noteElement);
        if (noteId.isEmpty()) {
            return;
        }

        for (Map.Entry<Element, ScoreNode> entry : scorePage.getAllEntries().entrySet()) {
            Element candidate = entry.getKey();
            if ("note".equals(candidate.getLocalName())) {
                continue;
            }

            if (!isDirectLinkedPerformance(candidate)) {
                continue;
            }

            String candidateNoteId = normalizeReference(Helper.getAttributeValue("noteid", candidate));
            if (noteId.equals(candidateNoteId)) {
                drawDirectLine(entry.getValue(), g2);
            }
        }
    }

    private void drawNoteLinks(ScorePage scorePage, Element performanceElement, Graphics2D g2) {
        if (isDirectLinkedPerformance(performanceElement)) {
            String noteIdRef = normalizeReference(Helper.getAttributeValue("noteid", performanceElement));
            if (!noteIdRef.isEmpty()) {
                for (Map.Entry<Element, ScoreNode> entry : scorePage.getAllEntries().entrySet()) {
                    Element candidate = entry.getKey();
                    if (!"note".equals(candidate.getLocalName())) {
                        continue;
                    }
                    if (noteIdRef.equals(getXmlId(candidate))) {
                        drawDirectLine(entry.getValue(), g2);
                        return;
                    }
                }
            }
        }

        drawDateLine(scorePage, performanceElement, g2);
    }

    private void drawDirectLine(ScoreNode target, Graphics2D g2) {
        Composite savedComposite = g2.getComposite();
        Stroke savedStroke = g2.getStroke();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
        g2.setColor(new Color(Settings.scorePerformanceColorHighlighted.getRed(), Settings.scorePerformanceColorHighlighted.getGreen(), Settings.scorePerformanceColorHighlighted.getBlue()));
        g2.setStroke(new BasicStroke(6.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine((int) this.anchorNode.getX(), (int) this.anchorNode.getY(), (int) target.getX(), (int) target.getY());
        g2.setStroke(savedStroke);
        g2.setComposite(savedComposite);
    }

    private void drawDateLine(ScorePage scorePage, Element performanceElement, Graphics2D g2) {
        double date = MeasureNumberLookup.getMsmTickDate(performanceElement, this.panel.getScoreDocumentData().getProjectPane().getMsm(), getPerformancePpq(performanceElement));
        ArrayList<ScoreNode> noteNodes = getNoteNodesAtDate(scorePage, performanceElement, date);
        if (noteNodes.isEmpty()) {
            return;
        }

        int padding = 50;

        ArrayList<ScoreNode> dateNodes = getNodesAtDate(scorePage, performanceElement, date);
        double leftMost = noteNodes.get(0).getX();
        double topMost = dateNodes.get(0).getY();
        double bottomMost = dateNodes.get(0).getY();
        for (ScoreNode noteNode : noteNodes) {
            if (noteNode.getX() < leftMost) {
                leftMost = noteNode.getX();
            }
        }
        for (ScoreNode dateNode : dateNodes) {
            if (dateNode.getY() < topMost) {
                topMost = dateNode.getY();
            }
            if (dateNode.getY() > bottomMost) {
                bottomMost = dateNode.getY();
            }
        }

        int xStart = (int) Math.round(leftMost - Settings.scoreHoverDateLineOffset);
        int yStart = (int) Math.round(topMost - padding);
        int yEnd = (int) Math.round(bottomMost) + padding;
        Composite savedComposite = g2.getComposite();
        Stroke savedStroke = g2.getStroke();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f));
        g2.setColor(new Color(Settings.scoreNoteColorHighlighted.getRed(), Settings.scoreNoteColorHighlighted.getGreen(), Settings.scoreNoteColorHighlighted.getBlue()));
        g2.setStroke(new BasicStroke(6.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(xStart, yStart, xStart, yEnd);
        g2.setStroke(savedStroke);
        g2.setComposite(savedComposite);
    }

    private ArrayList<ScoreNode> getNoteNodesAtDate(ScorePage scorePage, Element performanceElement, double date) {
        ArrayList<ScoreNode> noteNodes = new ArrayList<>();
        for (Map.Entry<Element, ScoreNode> entry : scorePage.getAllEntries().entrySet()) {
            Element candidate = entry.getKey();
            if (!"note".equals(candidate.getLocalName())) {
                continue;
            }
            if (!sameDate(candidate, date)) {
                continue;
            }
            if (!samePerformanceScope(candidate, performanceElement)) {
                continue;
            }
            noteNodes.add(entry.getValue());
        }
        return noteNodes;
    }

    private ArrayList<ScoreNode> getNodesAtDate(ScorePage scorePage, Element performanceElement, double date) {
        ArrayList<ScoreNode> dateNodes = new ArrayList<>();
        for (Map.Entry<Element, ScoreNode> entry : scorePage.getAllEntries().entrySet()) {
            Element candidate = entry.getKey();
            if (!sameDate(candidate, date)) {
                continue;
            }
            if (!samePerformanceScope(candidate, performanceElement)) {
                continue;
            }
            dateNodes.add(entry.getValue());
        }
        return dateNodes;
    }

    private int getPerformancePpq(Element performanceElement) {
        MpmTree mpmTree = this.panel.getScoreDocumentData().getProjectPane().getMpmTree();
        if (mpmTree != null) {
            MpmTreeNode node = mpmTree.findNode(performanceElement, true);
            while (node != null) {
                if (node.getType() == MpmTreeNode.MpmNodeType.performance) {
                    return ((Performance) node.getUserObject()).getPulsesPerQuarter();
                }
                if (node.isRoot()) {
                    break;
                }
                node = node.getParent();
            }
        }

        return this.panel.getScoreDocumentData().getProjectPane().getMsm().getPPQ();
    }

    private static boolean isDirectLinkedPerformance(Element element) {
        String localName = element.getLocalName();
        return "articulation".equals(localName) || "ornament".equals(localName);
    }

    private static boolean samePerformanceScope(Element candidate, Element reference) {
        Element candidateScope = getAncestor(candidate, "global", "part");
        Element referenceScope = getAncestor(reference, "global", "part");
        if (referenceScope == null) {
            return candidateScope == null;
        }

        if ("global".equals(referenceScope.getLocalName())) {
            return true;
        }

        if ((candidateScope == null) || !"part".equals(candidateScope.getLocalName())) {
            return false;
        }

        String candidateNumber = candidateScope.getAttributeValue("number");
        String referenceNumber = referenceScope.getAttributeValue("number");
        return (candidateNumber != null) && candidateNumber.equals(referenceNumber);
    }

    private static Element getAncestor(Element element, String... localNames) {
        if (element == null) {
            return null;
        }

        for (Node parent = element.getParent(); parent != null; parent = parent.getParent()) {
            if (!(parent instanceof Element)) {
                return null;
            }
            Element candidate = (Element) parent;
            for (String localName : localNames) {
                if (localName.equals(candidate.getLocalName())) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String getXmlId(Element element) {
        String id = element.getAttributeValue("id", "http://www.w3.org/XML/1998/namespace");
        return (id == null) ? "" : id;
    }

    private static String normalizeReference(String reference) {
        if (reference == null) {
            return "";
        }
        return reference.startsWith("#") ? reference.substring(1) : reference;
    }

    private static boolean sameDate(Element element, double date) {
        String candidateDateStr = Helper.getAttributeValue("date", element);
        if (candidateDateStr.isEmpty()) {
            return false;
        }
        return Double.compare(Double.parseDouble(candidateDateStr), date) == 0;
    }
}