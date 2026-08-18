package mpmToolbox.gui.score;

import com.alee.extended.button.WebSplitButton;
import com.alee.extended.window.WebPopup;
import com.alee.laf.WebLookAndFeel;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.panel.WebPanel;
import meico.mpm.elements.Performance;
import meico.supplementary.KeyValue;
import mpmToolbox.gui.mpmEditingTools.MpmEditingTools;
import mpmToolbox.gui.mpmEditingTools.PlaceAndCreateContextMenu;
import mpmToolbox.gui.mpmTree.MpmTree;
import mpmToolbox.gui.mpmTree.MpmTreeNode;
import mpmToolbox.gui.msmEditingTools.MsmEditingTools;
import mpmToolbox.gui.msmTree.MsmTree;
import mpmToolbox.gui.msmTree.MsmTreeNode;
import mpmToolbox.gui.score.interaction.*;
import mpmToolbox.gui.svgTree.SvgDockableFrame;
import mpmToolbox.gui.svgTree.SvgTree;
import mpmToolbox.projectData.SvgData;
import mpmToolbox.projectData.score.Score;
import mpmToolbox.projectData.score.ScoreNode;
import mpmToolbox.projectData.score.ScorePage;
import mpmToolbox.supplementary.orthantNeighborhoodGraph.ONGNode;
import nu.xom.Element;
import nu.xom.Node;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;

/**
 * This class displays the score pages and defines interaction with them.
 * @author Axel Berndt
 */
public class ScoreDisplayPanel extends WebPanel implements MouseWheelListener, MouseListener, MouseMotionListener, KeyListener {
    protected final ScoreDocumentData scoreDocumentData;                               // a link to the parent ScoreFrame
    protected ScorePage scorePage;                                          // the score page currently displayed
    private int pageIndex = 0;                                              // the page number (array index) currently displayed
    private final WebPopup noNoteSelected = new WebPopup<>(new WebLabel("You should select a note in the Musical Sequence Markup to be associated with a position here."));
    private final WebPopup noMpm = new WebPopup<>(new WebLabel("This project has no MPM. First create an MPM and a performance in it."));
    private final WebPopup noPerformance = new WebPopup<>(new WebLabel("This project's MPM has no Performance. First create a performance."));

    // variables for pan and zoom
    protected PanZoomHelper panZoomHelper;                                  // manages pan and zoom state

    // variables for the overlay
    private Point mousePositionInImage = null;                              // this is used to keep track of the pixel position of the mouse cursor within the image (required to draw the "annotation preview overlay")
    private double overlayElementScaleFactor = 5.0;                         // this scales the note annotation size
    private int xOffset = (int)(this.overlayElementScaleFactor * 3.0);      // horizontal offset to center the overlay symbols around its position
    private int yOffset = (int)(this.overlayElementScaleFactor * 2.0);      // vertical offset to center the overlay symbols around its position
    private int xWidth = (int)(this.overlayElementScaleFactor * 6.0);       // the width of the overlay symbols
    private int yWidth = (int)(this.overlayElementScaleFactor * 4.0);       // the height of the overlay symbols

    private Font performanceSymbolFont = WebLookAndFeel.globalWindowFont.deriveFont(Font.BOLD, (float) (72.0 * this.xWidth / Toolkit.getDefaultToolkit().getScreenResolution()));

    private InteractionModeManager interactionModeManager;                  // manages all interaction modes and their UI
    /**
     * constructor
     *
     * @param scoreDocumentData is the parent of ScoreDisplayPanel
     */
    public ScoreDisplayPanel(ScoreDocumentData scoreDocumentData) {
        super();

        this.scoreDocumentData = scoreDocumentData;                                                       // store the link to the parent project
        this.scorePage = this.scoreDocumentData.projectPane.getScore().getPage(this.pageIndex);     // load the score page to be displayed
        this.panZoomHelper = new PanZoomHelper(this);                               // initialize the pan/zoom helper

        this.updateOverlayElementsScaleFactor();                                    // compute the initial size of overlay elements

        // prepare the popup message that shows up in mark notes mode when no note is selected in the MSM tree
        this.noNoteSelected.setPadding(3);
//        this.noNoteSelected.onMouseClick(e -> this.noNoteSelected.hidePopup());   // disappear when clicked
//        this.noNoteSelected.setResizable(false);
//        this.noNoteSelected.setDraggable(false);
        this.noMpm.setPadding(3);
        this.noPerformance.setPadding(3);

        this.interactionModeManager = new InteractionModeManager(this, scoreDocumentData);

        // initialize the input listeners needed for interaction
        this.addMouseWheelListener(this);
        this.addMouseMotionListener(this);
        this.addMouseListener(this);
        this.addKeyListener(this);
//        this.addKeyboardInput();

        this.updateMpmTreeSelectionListener();
        if (this.scoreDocumentData.getProjectPane().getMpmDockableFrame() != null) {
            this.scoreDocumentData.getProjectPane().getMpmDockableFrame().addContainerListener(new ContainerListener() {    // if the MPM tree is completely removed or newly added, this listener updates the corresponding tree listener
                @Override
                public void componentAdded(ContainerEvent e) {
                    updateMpmTreeSelectionListener();
                }

                @Override
                public void componentRemoved(ContainerEvent e) {
                    updateMpmTreeSelectionListener();
                }
            });
        }

        this.scoreDocumentData.getProjectPane().getMsmTree().addTreeSelectionListener(treeSelectionEvent -> {
            this.repaint();
        });
    }

    /**
     * invoke this method if the MPM tree is deleted or newly created, so the TempoMapPanel can react on it
     */
    public void updateMpmTreeSelectionListener() {
        if (this.scoreDocumentData.getProjectPane().getMpmTree() != null) {
            this.scoreDocumentData.getProjectPane().getMpmTree().addTreeSelectionListener(treeSelectionEvent -> this.repaint());    // repaint when tree selection in MPM tree changed, so the highlighting gets updated
        }
    }

    /**
     * Set the current interaction mode.
     */
    public void onInteractionModeChange() {
        this.setMousePositionInImage(null);
        this.setCursor(Cursor.getDefaultCursor());
        this.repaint();
    }

    /**
     * Get access to the ScoreDocumentData object that instantiated this.
     * @return
     */
    public ScoreDocumentData getParentScoreDocumentData() {
        return this.scoreDocumentData;
    }

    /**
     * Retrieve the current anchor node.
     * @return
     */
    public ScoreNode getAnchorNode() {
        return (this.interactionModeManager != null) ? this.interactionModeManager.getAnchorNodeHelper().getAnchorNode() : null;
    }

    /**
     * Gets the pan/zoom helper.
     * @return the pan/zoom helper
     */
    public PanZoomHelper getPanZoomHelper() {
        return this.panZoomHelper;
    }

    /**
     * Indicates whether overlay rendering should be hidden.
     * @return true when overlays are hidden
     */
    public boolean isOverlayHidden() {
        return this.scoreDocumentData.hideOverlay;
    }

    /**
     * Gets the horizontal offset used for overlay symbols.
     * @return the overlay x offset
     */
    public int getOverlayXOffset() {
        return this.xOffset;
    }

    /**
     * Gets the vertical offset used for overlay symbols.
     * @return the overlay y offset
     */
    public int getOverlayYOffset() {
        return this.yOffset;
    }

    /**
     * Gets the overlay symbol width.
     * @return the overlay width
     */
    public int getOverlayXWidth() {
        return this.xWidth;
    }

    /**
     * Gets the overlay symbol height.
     * @return the overlay height
     */
    public int getOverlayYWidth() {
        return this.yWidth;
    }

    /**
     * Gets the font used for performance symbols.
     * @return the performance symbol font
     */
    public Font getPerformanceSymbolFont() {
        return this.performanceSymbolFont;
    }

    /**
     * Draw the score page and apply all transformations. This method is invoked by the paint() method.
     * @param g
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);                                    // this ensures that the background is filled with the standard background color

        if (this.panZoomHelper.getZoomFactor() == null)
            this.reset();

        Graphics2D g2 = (Graphics2D)g;                              // make g a Graphics2D object so we can use its extended drawing features
        g2.transform(this.panZoomHelper.getAffineTransform());                         // do the transform on the graphics

        // draw light gray background exactly the size of the score image
        {
            int imgW = this.scorePage.getImage().getWidth(this);
            int imgH = this.scorePage.getImage().getHeight(this);
            g2.setColor(new Color(210, 210, 210));
            g2.fillRect(0, 0, imgW, imgH);
        }

        if (!this.scoreDocumentData.hideScore)
            g2.drawImage(this.scorePage.getImage(), 0, 0, this);    // draw image

        // draw SVG overlays
        if (!this.scoreDocumentData.hideOverlay) {
            java.util.ArrayList<SvgData> svgs = this.scoreDocumentData.projectPane.getProjectData().getSvgs();
            if (!svgs.isEmpty()) {
                int imgW = this.scorePage.getImage().getWidth(this);
                int imgH = this.scorePage.getImage().getHeight(this);
                if (imgW > 0 && imgH > 0) {
                    for (SvgData svg : svgs) {
                        Composite originalComposite = g2.getComposite();
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65f));
                        svg.render(g2, imgW, imgH);
                        g2.setComposite(originalComposite);
                    }

                    // draw highlight rectangles for the selected SVG element (tree → score)
                    for (SvgData svg : svgs) {
                        nu.xom.Element highlighted = svg.getHighlightedElement();
                        if (highlighted == null)
                            continue;
                        String id = highlighted.getAttributeValue("id");
                        if (id == null || id.isEmpty())
                            continue;
                        java.awt.geom.Rectangle2D bounds = svg.getBoundsInImageSpace(id, imgW, imgH);
                        if (bounds == null || bounds.getWidth() <= 0 || bounds.getHeight() <= 0)
                            continue;
                        Composite savedComp = g2.getComposite();
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                        g2.setColor(new Color(255, 200, 0));   // amber fill
                        g2.fill(bounds);
                        g2.setComposite(savedComp);
                        g2.setColor(new Color(255, 160, 0));   // amber outline
                        g2.setStroke(new BasicStroke(2.0f));
                        g2.draw(bounds);
                    }
                }
            }
        }

        if (this.interactionModeManager != null) {
            MpmTreeNode selectedMpmNode = this.getParentScoreDocumentData().getSelectedMpmNode();
            this.interactionModeManager.draw(g2, selectedMpmNode);
        }
    }

    /**
     * the panel's paint method
     * @param g
     */
    @Override
    public void paint(Graphics g) {
        super.paint(g);
    }

    /**
     * invoke this method to reset the image zoom to match the panel size and translate the image to the initial position
     */
    private void reset() {
        this.panZoomHelper.reset();
    }

    /**
     * This method checks if two input objects have the same parent performance.
     * This is a helper method for paintComponent() to determine which ONGNodes from the MPM should be painted.
     * We want to paint only performance overlays from the performance in which the MpmTree cursor currently is. We do not want to mix elements from different performances.
     * @param mpmElement the element to be painted (or not if it is not in the same performance as e2); the element must be part of the MPM!
     * @param e2 the currently selected MpmTreeNode
     * @return true if the parent performance is the same for both, false in every other case
     */
    public static boolean samePerformance(Element mpmElement, MpmTreeNode e2) {
        if ((mpmElement == null) || (e2 == null))                       // if any of the input objects is null
            return false;                                               // the result is false

        // determine the performance of mpmElement
        Node p1;
        for (p1 = mpmElement.getParent(); p1 != null; p1 = p1.getParent()) {
            if (((Element) p1).getLocalName().equals("performance"))
                break;
        }
        if (p1 == null)
            return false;

        // determine the performance within which the currently selected MpmTreeNode is and check whether it is the same performance as p1
        MpmTreeNode p2;
        for (p2 = e2; !p2.isRoot(); p2 = p2.getParent()) {
            if (p2.getType().equals(MpmTreeNode.MpmNodeType.performance)) {     // found the parent performance
                if (p1 == ((Performance) p2.getUserObject()).getXml())          // if it is the same performance as p1
                    return true;                                                // return true
                break;                                                          // no need to check further parental nodes, there can be no performance within a performance
            }
        }

        return false;
    }

    /**
     * Determine whether an MPM element is in the global environment.
     * @param mpmElement
     * @return
     */
    public static boolean isGlobal(Element mpmElement) {
        if (mpmElement == null)
            return false;

        for (Node parent = mpmElement.getParent(); parent != null; parent = parent.getParent()) {
            Element p = (Element) parent;
            switch (p.getLocalName()) {
                case "part":
                    return false;
                case "global":
                    return true;
            }
        }

        return false;
    }

    /**
     * a getter for the mouse position in the image space
     * @return
     */
    public synchronized Point getMousePositionInImage() {
        return this.mousePositionInImage;
    }

    /**
     * set the mouse position in image space
     * @param point
     */
    public synchronized void setMousePositionInImage(Point point) {
        this.mousePositionInImage = point;
    }

    /**
     * get the index of the page currently displayed
     * @return
     */
    public int getPageIndex() {
        return this.pageIndex;
    }

    /**
     * get the score page currently displayed
     * @return
     */
    public ScorePage getScorePage() {
        return this.scorePage;
    }

    /**
     * display the next page in the list
     */
    public void nextPage() {
        Score score = this.scoreDocumentData.projectPane.getScore();

        if ((this.pageIndex + 1) >= score.size())
            return;

        this.pageIndex++;
        this.scorePage = score.getPage(this.pageIndex);

        this.repaint();
    }

    /**
     * display the previous page in the list
     */
    public void previousPage() {
        if (this.pageIndex == 0)
            return;

        this.pageIndex--;
        this.scorePage = this.scoreDocumentData.projectPane.getScore().getPage(this.pageIndex);

        this.repaint();
    }

    /**
     * jump to a page indicated by index
     * @param index the page index
     */
    public void showPage(int index) {
        Score score = this.scoreDocumentData.projectPane.getScore();

        if (index >= score.size())
            return;

        this.pageIndex = index;
        this.scorePage = score.getPage(this.pageIndex);

        this.repaint();
    }

    /**
     * invoke this method when the size of the overlay elements needs to be updated
     */
    protected void updateOverlayElementsScaleFactor() {
        int exp = (int)this.scoreDocumentData.annotationSizeSpinner.getValue();
        this.overlayElementScaleFactor = 5.0 * Math.pow(1.05, exp);

        this.xOffset = (int)(this.overlayElementScaleFactor * 3.0);      // horizontal offset to center the ellipsis around its position
        this.yOffset = (int)(this.overlayElementScaleFactor * 2.0);      // vertical offset to center the ellipsis around its position
        this.xWidth = (int)(this.overlayElementScaleFactor * 6.0);       // the width of the ellipsis
        this.yWidth = (int)(this.overlayElementScaleFactor * 4.0);       // the height of the ellipsis

        double fontSize = 72.0 * this.xWidth / Toolkit.getDefaultToolkit().getScreenResolution();
        this.performanceSymbolFont = WebLookAndFeel.globalWindowFont.deriveFont(Font.BOLD, (float) fontSize);

        this.repaint();
    }

    /**
     * this implements the dragging of the image
     * @param mousePosition
     */
    public void dragImage(Point mousePosition) {
        this.panZoomHelper.dragImage(mousePosition);
    }

    /**
     * this implements the logic when a drag or select gesture is performed and the mouse button has been released
     * @param mouseEvent
     */
    public void dragOrSelectGesture(MouseEvent mouseEvent) {
        if (this.panZoomHelper.getDragStartPoint() != null) {                                      // user has actually performed a drag gesture
            this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            this.panZoomHelper.dragEnded();
            return;
        }

        // it was a click into the score (mouseClicked() also fires) and the user might have selected something
        Element selectedElement = this.getOverlayElementAt(mouseEvent);         // get the overlay element that the mouse click selects
        if (selectedElement == null) {                                          // click was over nothing
            // Score → SVG Tree: try to pick an SVG element at this position
            Point mousePoint = this.panZoomHelper.getPixelPosition(mouseEvent.getPoint());
            int imgW = this.scorePage.getImage().getWidth(this);
            int imgH = this.scorePage.getImage().getHeight(this);
            boolean svgHit = false;
            if (imgW > 0 && imgH > 0) {
                SvgDockableFrame svgFrame = this.scoreDocumentData.projectPane.getSvgDockableFrame();
                java.util.ArrayList<SvgData> svgs = this.scoreDocumentData.projectPane.getProjectData().getSvgs();
                for (SvgData svg : svgs) {
                    nu.xom.Element picked = svg.pickElementAt(mousePoint.x, mousePoint.y, imgW, imgH);
                    if (picked != null) {
                        svg.setHighlightedElement(picked);
                        // select in the SVG tree (activate the frame's tab for this SVG first)
                        SvgTree svgTree = svgFrame.getTreeForSvg(svg);
                        if (svgTree != null) {
                            svgFrame.showTabForSvg(svg);
                            svgTree.selectNodeForElement(picked);
                        }
                        svgHit = true;
                        break;
                    }
                }
            }
            if (!svgHit) {
                this.scoreDocumentData.projectPane.getMsmTree().clearSelection();               // deselect anything in the MSM tree
                this.scoreDocumentData.projectPane.getMpmTree().clearSelection();               // deselect anything in the MPM tree
            }
            this.repaint();
            return;
        }

        switch (selectedElement.getLocalName()) {
            case "note": {
                MsmTree msmTree = this.scoreDocumentData.projectPane.getMsmTree();              // a handle to the msm tree
                MsmTreeNode msmTreeNode = msmTree.findNode(selectedElement, true);    // get the msm tree's node that corresponds with the selected note
                if (msmTreeNode == null)                                        // if nothing has been selected
                    return;                                                     // done
                msmTree.setSelectedNode(msmTreeNode);                           // select the node in the msm tree
                msmTree.scrollPathToVisible(msmTreeNode.getTreePath());         // scroll the tree so the node is visible

                switch (mouseEvent.getButton()) {
                    case MouseEvent.BUTTON1:                                    // left click
                        break;
                    case MouseEvent.BUTTON3:                                    // right click = context menu
                        WebPopupMenu menu = MsmEditingTools.makeScoreContextMenu(msmTreeNode, msmTree, scorePage);
                        menu.show(this, mouseEvent.getX() - 25, mouseEvent.getY());
                        break;
                }
                break;
            }
            default: {                                                          // a performance instruction
                MpmTree mpmTree = this.scoreDocumentData.projectPane.getMpmTree();              // a handle to the mpm tree
                MpmTreeNode mpmTreeNode = mpmTree.findNode(selectedElement, true);    // get the msm tree's node that corresponds with the selected note
                if (mpmTreeNode == null)                                        // if nothing has been selected
                    return;                                                     // done
                mpmTree.setSelectedNode(mpmTreeNode);                           // select the node in the mpm tree
                mpmTree.scrollPathToVisible(mpmTreeNode.getTreePath());         // scroll the tree so the node is visible

                switch (mouseEvent.getButton()) {
                    case MouseEvent.BUTTON1:                                    // left click
                        if (mouseEvent.getClickCount() > 1)                     // if double (or more) click
                            MpmEditingTools.quickOpenEditor(mpmTreeNode, mpmTree);  // open editor dialog
                        break;
                    case MouseEvent.BUTTON3:                                    // right click = context menu
                        WebPopupMenu menu = MpmEditingTools.makeScoreContextMenu(mpmTreeNode, mpmTree, scorePage);
                        menu.show(this, mouseEvent.getX() - 25, mouseEvent.getY());
                        break;
                }
            }
        }
    }

    /**
     * A helper method to keep the mouse listener methods clear. It implements the procedure to annotate a note position on the current page
     * @param mouseEvent
     */
    public void makeNoteAssociation(MouseEvent mouseEvent) {
        MsmTreeNode currentNode = this.scoreDocumentData.projectPane.getMsmTree().getSelectedNode();            // get the currently selected node
        if ((currentNode == null) || (currentNode.getType() != MsmTreeNode.XmlNodeType.note)) { // but there is no node of type note selected in the MSM tree to be associated with the pixel position
            this.noNoteSelected.showPopup(this, mouseEvent.getPoint());                         // display the popup message at the mouse position
            return;
        }

//        Point p = this.mouse2PixelPosition(mouseEvent);                                         // transform the mouse click position to image pixel coordinates via inverting the affine transform of the image ... this has already been done and stored in this.mousePositionInImage when this method is invoked

        // update the note association data in the project data structure
        Element note = (Element) currentNode.getUserObject();
        ScoreNode noteNode = this.scorePage.addEntry(this.getMousePositionInImage().getX(), this.getMousePositionInImage().getY(), note);

        repaint();                                                                              // the overlay has been updated, so we need to repaint

        this.scoreDocumentData.projectPane.getMsmTree().updateNode(currentNode);                                // update the indication that the note is associated to a pixel position now

        // in the MSM tree find and select the next node of type note
        for (MsmTreeNode nextNode = currentNode.getNextNode(); nextNode != null; nextNode = nextNode.getNextNode()) {
            if (nextNode.getType() == MsmTreeNode.XmlNodeType.note) {                           // found the next note
                this.scoreDocumentData.projectPane.getMsmTree().setSelectedNode(nextNode);                      // select it
                this.scoreDocumentData.projectPane.getMsmTree().scrollPathToVisible(nextNode.getTreePath());    // scroll the tree so the node is visible
                return;
            }
        }
        this.scoreDocumentData.projectPane.getMsmTree().clearSelection();       // no note node was found (null), clear the selection so the next click won't overwrite the last note's coordinates
    }

    /**
     * this helper method creates a popup menu for placing and creating performance instructions in the score
     * @return
     */
    public WebPopupMenu makePlaceAndCreateContextMenu() {
        Point mousePosInImage = this.getMousePositionInImage();
        return new PlaceAndCreateContextMenu(mousePosInImage, this); // create popup menu for the creation and placement of MPM nodes
    }

    /**
     * the routine to select an element from the overlay
     * @param mouseEvent
     * @return
     */
    public Element getOverlayElementAt(MouseEvent mouseEvent) {
        if (this.scorePage.isEmpty())                               // this score page has no elements
            return null;

        // retrieve the nearest point to the mouse position
        Point mousePoint = this.mouse2PixelPosition(mouseEvent);    // convert mouse position to pixel position on the score page
//        KeyValue<ONGNode, Double> nearest = Tools.findNearestPoint(this.overlayElementsForCurrentPage, mousePoint);       // find the nearest point, the brute force method
        KeyValue<ONGNode, Double> nearest = this.scorePage.findNearestNeighborOf(mousePoint.getX(), mousePoint.getY());
        if (nearest == null)
            return null;

        // check whether the mouse is close enough to the nearest point so its element is selected
        if ((Math.sqrt(nearest.getValue()) * 2) > this.xWidth)      // if the mouse is too far to select the element
            return null;                                            // done

        // return the first Element in the list of elements
        ArrayList<Element> elements = ((ScoreNode) nearest.getKey()).getAssociatedElements();
        for (Element element : elements) {
            return element;
        }

        return null;
    }

    /**
     * this is a shortcut to get the neighbors of the position
     * @param point2D
     * @return
     */
    ONGNode getNeighboringNodes(Point2D point2D) {
        return this.scorePage.findAllNearestNeighborsOf(point2D.getX(), point2D.getY());   // get the four neighbors to the point via a new ScoreNode
    }

    /**
     * for a given point, determine its four nearest neighbors and inverse nearest neighbors
     * @param point2D
     * @return
     */
    ArrayList<ONGNode> getAllDirectAndInverseNeighbors(Point2D point2D) {
        ArrayList<ONGNode> neighbors = new ArrayList<>();      // put the neighboring nodes to the mouse position in the connectMeWithMouse list which is later used to highlight those nodes

        neighbors.add(this.scorePage.findNearestNeighborOf(point2D.getX(), point2D.getY()).getKey());    // get the nearest neighbor to the mouse position

        // get the mouse node's nearest neighbors
        ONGNode pivotNode = this.scorePage.findAllNearestNeighborsOf(point2D.getX(), point2D.getY());   // get the four neighbors to it via a new ONGNode
        for (ONGNode n : pivotNode.neighbors) {                     // for each of the neighbors
            if (n != null) {                                        // if it is not null
                neighbors.add(n);                                   // add it to the connectMeWithMouse list
            }
        }

        // get the mouse node's inverse nearest neighbors
        for (KeyValue<ONGNode, Integer> n : pivotNode.findMyInverseNeighbors()) {  // find all inverse nearest neighbors
            neighbors.add(n.getKey());
        }

        return  neighbors;
    }

    /**
     * convert the mouse position to the pixel position on the image
     * @param mouseEvent
     * @return
     */
    public Point mouse2PixelPosition(MouseEvent mouseEvent) {
        return this.panZoomHelper.getPixelPosition(mouseEvent.getPoint());
    }

    /**
     * mouse clicked
     * @param mouseEvent
     */
    @Override
    public void mouseClicked(MouseEvent mouseEvent) {
        if (this.interactionModeManager != null) {
            this.interactionModeManager.mouseClicked(mouseEvent);
        }
    }

    /**
     * mouse pressed
     * @param mouseEvent
     */
    @Override
    public void mousePressed(MouseEvent mouseEvent) {
        this.requestFocusInWindow();
        if (this.interactionModeManager != null) {
            this.interactionModeManager.mousePressed(mouseEvent);
        }
    }

    /**
     * mouse released
     * @param mouseEvent
     */
    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        if (this.interactionModeManager != null) {
            this.interactionModeManager.mouseReleased(mouseEvent);
        }
    }

    /**
     * mouse enters the panel
     * @param mouseEvent
     */
    @Override
    public void mouseEntered(MouseEvent mouseEvent) {
        if (this.interactionModeManager != null) {
            this.interactionModeManager.mouseEntered(mouseEvent);
        }
    }

    /**
     * mouse leaves the panel
     * @param mouseEvent
     */
    @Override
    public void mouseExited(MouseEvent mouseEvent) {
        if (this.interactionModeManager != null) {
            this.interactionModeManager.mouseExited(mouseEvent);
        }
    }

    /**
     * drag gesture
     * @param mouseEvent
     */
    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        if (this.interactionModeManager != null) {
            this.interactionModeManager.mouseDragged(mouseEvent);
        }
    }

    /**
     * mouse moves
     * @param mouseEvent
     */
    @Override
    public void mouseMoved(MouseEvent mouseEvent) {
        if (this.interactionModeManager != null) {
            this.interactionModeManager.mouseMoved(mouseEvent);
        }
    }

    /**
     * mouse wheel interaction
     * @param mouseWheelEvent
     */
    @Override
    public void mouseWheelMoved(MouseWheelEvent mouseWheelEvent) {
        if (this.interactionModeManager != null) {
            this.interactionModeManager.mouseWheelMoved(mouseWheelEvent);
        }
    }

    /**
     * actions to be triggered when a key is typed
     * @param keyEvent
     */
    @Override
    public void keyTyped(KeyEvent keyEvent) {
        if (this.interactionModeManager != null) {
            this.interactionModeManager.keyTyped(keyEvent);
        }
    }

    /**
     * actions to be triggered when a key is pressed
     * @param keyEvent
     */
    @Override
    public void keyPressed(KeyEvent keyEvent) {
        if (this.interactionModeManager != null) {
            this.interactionModeManager.keyPressed(keyEvent);
        }
    }

    /**
     * actions to be triggered when a key is released
     * @param keyEvent
     */
    @Override
    public void keyReleased(KeyEvent keyEvent) {
        if (this.interactionModeManager != null) {
            this.interactionModeManager.keyReleased(keyEvent);
        }
    }

    /**
     * opens pop-up to inform user that no MPM is available
     * @param mouseEvent
     */
    public void showHasNoMpmPopUp(MouseEvent mouseEvent) {
        this.noMpm.showPopup(this, mouseEvent.getPoint());
    }

    /**
     * opens pop-up to inform user that no performance is available
     * @param mouseEvent
     */
    public void showNoPerformancePopUp(MouseEvent mouseEvent) {
        this.noPerformance.showPopup(this, mouseEvent.getPoint());
    }

    /**
     * sets button to be filled by InteractionManager
     * @param button
     */
    public void setModeButton(WebSplitButton button) {
        this.interactionModeManager.setModeButton(button);
    }
}
