package mpmToolbox.gui.svgTree;

import com.alee.laf.tree.TreeCellArea;
import com.alee.laf.tree.TreeToolTipProvider;

import javax.swing.*;

/**
 * Tooltip provider for SVG tree nodes.
 *
 * @author Lars Engeln
 */
public class SvgTreeTooltipProvider extends TreeToolTipProvider<SvgTreeNode> {

    @Override
    protected String getToolTipText(JTree component, TreeCellArea<SvgTreeNode, JTree> area) {
        return this.getValue(component, area).getTooltipText();
    }
}

