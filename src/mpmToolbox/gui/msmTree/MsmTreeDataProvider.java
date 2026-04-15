package mpmToolbox.gui.msmTree;

import com.alee.api.annotations.NotNull;
import com.alee.extended.tree.AbstractExTreeDataProvider;
import mpmToolbox.gui.MeasureNumberLookup;
import mpmToolbox.gui.Settings;
import mpmToolbox.projectData.ProjectData;
import nu.xom.Element;
import nu.xom.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * TreeDataProvider for MSM tree
 * @author Axel Berndt
 */
public class MsmTreeDataProvider extends AbstractExTreeDataProvider<MsmTreeNode> {
    @NotNull protected Element msmRoot;
    @NotNull private final ProjectData project;

    /**
     * constructor
     * @param msmRoot
     * @param project
     */
    public MsmTreeDataProvider(@NotNull final Element msmRoot, @NotNull ProjectData project) {
        this.msmRoot = msmRoot;
        this.project = project;
    }

    /**
     * get the root node of the tree
     * @return
     */
    @Override
    public MsmTreeNode getRoot() {
        return new MsmTreeNode(this.msmRoot, this.project);
    }

    /**
     * this method is used to buffer the tree nodes
     * @param parent
     * @return
     */
    @Override
    public List<MsmTreeNode> getChildren(MsmTreeNode parent) {
        ArrayList<MsmTreeNode> childNodes = new ArrayList<>();          // fill this list with child nodes of the specified parent

        // MsmMeasureElement nodes: their children are the grouped score elements
        if (parent.getType() == MsmTreeNode.XmlNodeType.measure) {
            MsmMeasureElement measure = (MsmMeasureElement) parent.getUserObject();
            for (Element e : measure.scoreElements)
                childNodes.add(new MsmTreeNode(e, this.project));
            return childNodes;
        }

        if (parent.getType() == MsmTreeNode.XmlNodeType.attribute)    // attributes have no children
            return childNodes;

        Element p = (Element) parent.getUserObject();

        // In MEASURE_NODE mode group score children into synthetic measure nodes
        if (parent.getType() == MsmTreeNode.XmlNodeType.score
                && Settings.measureDisplayMode == Settings.MeasureDisplayMode.MEASURE_NODE
                && this.project.getMsm() != null) {

            Element tsMap = MeasureNumberLookup.getTimeSignatureMap(this.project.getMsm());
            int ppq = this.project.getMsm().getPPQ();
            TreeMap<Integer, MsmMeasureElement> groups = new TreeMap<>();

            Elements scoreChildren = p.getChildElements();
            for (int i = 0; i < scoreChildren.size(); i++) {
                Element child = scoreChildren.get(i);
                String dateStr = child.getAttributeValue("date");
                double ticks = (dateStr != null && !dateStr.isEmpty()) ? Double.parseDouble(dateStr) : 0.0;
                int mNum = MeasureNumberLookup.getMeasureNumber(ticks, tsMap, ppq);
                groups.computeIfAbsent(mNum, MsmMeasureElement::new).scoreElements.add(child);
            }

            for (MsmMeasureElement measure : groups.values())
                childNodes.add(new MsmTreeNode(measure, this.project));

            return childNodes;
        }

        // default: make attributes to nodes
        for (int i = 0; i < p.getAttributeCount(); ++i)
            childNodes.add(new MsmTreeNode(p.getAttribute(i), this.project));

        // make child elements to nodes
        Elements children = p.getChildElements();
        for (int i = 0; i < children.size(); ++i)
            childNodes.add(new MsmTreeNode(children.get(i), this.project));

        return childNodes;
    }
}
