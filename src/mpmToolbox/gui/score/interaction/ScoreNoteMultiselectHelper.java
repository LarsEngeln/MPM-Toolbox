package mpmToolbox.gui.score.interaction;

import mpmToolbox.gui.msmTree.MsmTree;
import mpmToolbox.gui.msmTree.MsmTreeNode;
import mpmToolbox.projectData.score.ScoreNode;
import nu.xom.Element;

import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * Helper for Shift-drag note multi-selection in the score view.
 */
public class ScoreNoteMultiselectHelper {
    private final MsmTree msmTree;
    private Point selectionRectStart = null;
    private Rectangle selectionRect = null;

    public ScoreNoteMultiselectHelper(MsmTree msmTree) {
        this.msmTree = msmTree;
    }

    public boolean isSelecting() {
        return this.selectionRectStart != null;
    }

    public void start(Point startPoint) {
        this.selectionRectStart = startPoint;
        this.selectionRect = new Rectangle(startPoint.x, startPoint.y, 0, 0);
    }

    public void update(Point currentPoint) {
        if (this.selectionRectStart == null)
            return;
        this.selectionRect = makeRectangle(this.selectionRectStart, currentPoint);
    }

    public ArrayList<Map.Entry<Element, ScoreNode>> finishAndSelect(Set<Map.Entry<Element, ScoreNode>> scoreNodes) {
        if (this.selectionRect == null)
            return null;

        ArrayList<Map.Entry<Element, ScoreNode>> selectedNodes = new ArrayList<>();

        ArrayList<TreePath> paths = new ArrayList<>();
        for (Map.Entry<Element, ScoreNode> entry : scoreNodes) {
            Element element = entry.getKey();
            if (!"note".equals(element.getLocalName()))
                continue;

            ScoreNode node = entry.getValue();
            if (!this.selectionRect.contains(node.getX(), node.getY()))
                continue;

            selectedNodes.add(entry);

            MsmTreeNode msmTreeNode = this.msmTree.findNode(element, true);
            if ((msmTreeNode != null) && (msmTreeNode.getType() == MsmTreeNode.XmlNodeType.note))
                paths.add(msmTreeNode.getTreePath());
        }

        if (paths.isEmpty()) {
            deselect();
        } else {
            this.msmTree.setSelectionPaths(paths.toArray(new TreePath[0]));
        }

        this.clear();

        return selectedNodes;
    }

    public void deselect() {
        this.msmTree.clearSelection();
    }

    public void clear() {
        this.selectionRectStart = null;
        this.selectionRect = null;
    }

    public void paintSelectionRectangle(Graphics2D g2, int yWidth) {
        if (this.selectionRect == null)
            return;

        Composite savedComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
        g2.setColor(Color.YELLOW);
        g2.fillRect(this.selectionRect.x, this.selectionRect.y, this.selectionRect.width, this.selectionRect.height);
        g2.setComposite(savedComposite);
        g2.setColor(Color.YELLOW.darker());
        g2.setStroke(new BasicStroke(Math.max(1.0f, yWidth / 6.0f)));
        g2.drawRect(this.selectionRect.x, this.selectionRect.y, this.selectionRect.width, this.selectionRect.height);
    }

    public ArrayList<Element> getSelectedMsmNotes() {
        ArrayList<Element> selected = new ArrayList<>();
        TreePath[] selectedPaths = this.msmTree.getSelectionPaths();
        if (selectedPaths == null)
            return selected;

        for (TreePath path : selectedPaths) {
            MsmTreeNode node = this.msmTree.getNodeForPath(path);
            if ((node != null) && (node.getType() == MsmTreeNode.XmlNodeType.note))
                selected.add((Element) node.getUserObject());
        }
        return selected;
    }

    public boolean containsReference(ArrayList<Element> list, Element element) {
        for (Element candidate : list)
            if (candidate == element)
                return true;
        return false;
    }

    private static Rectangle makeRectangle(Point start, Point end) {
        int x = Math.min(start.x, end.x);
        int y = Math.min(start.y, end.y);
        int width = Math.abs(end.x - start.x);
        int height = Math.abs(end.y - start.y);
        return new Rectangle(x, y, width, height);
    }
}
