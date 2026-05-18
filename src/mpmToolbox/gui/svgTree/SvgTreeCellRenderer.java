package mpmToolbox.gui.svgTree;

import com.alee.api.annotations.NotNull;
import com.alee.api.annotations.Nullable;
import com.alee.extended.tree.WebExTree;
import com.alee.laf.tree.TreeNodeParameters;
import com.alee.laf.tree.WebTreeCellRenderer;

import javax.swing.*;

/**
 * Cell renderer for the SVG tree.
 *
 * @author Lars Engeln
 */
public class SvgTreeCellRenderer
        extends WebTreeCellRenderer<SvgTreeNode, WebExTree<SvgTreeNode>,
        TreeNodeParameters<SvgTreeNode, WebExTree<SvgTreeNode>>> {

    @Override
    @Nullable
    protected String textForValue(
            @NotNull TreeNodeParameters<SvgTreeNode, WebExTree<SvgTreeNode>> parameters) {
        return parameters.node().getText(parameters);
    }

    @Override
    @Nullable
    protected Icon iconForValue(
            @NotNull TreeNodeParameters<SvgTreeNode, WebExTree<SvgTreeNode>> parameters) {
        return parameters.node().getNodeIcon(parameters);
    }
}

