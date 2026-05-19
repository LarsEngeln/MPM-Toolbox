package mpmToolbox.gui.msmTree;

import com.alee.api.annotations.NotNull;
import com.alee.api.annotations.Nullable;
import com.alee.extended.tree.WebExTree;
import com.alee.laf.tree.TreeNodeParameters;
import com.alee.laf.tree.WebTreeCellRenderer;

import javax.swing.*;
import java.awt.*;

/**
 * A custom tree cell renderer for MSM trees.
 * @author Axel Berndt
 */
public class MsmTreeCellRenderer extends WebTreeCellRenderer<MsmTreeNode, WebExTree<MsmTreeNode>, TreeNodeParameters<MsmTreeNode, WebExTree<MsmTreeNode>>> {
    /**
     * This returns the text to be written for node.
     * @param parameters
     * @return
     */
    @Override
    @Nullable
    protected String textForValue(@NotNull final TreeNodeParameters<MsmTreeNode, WebExTree<MsmTreeNode>> parameters) {
        MsmTreeNode node = parameters.node();
        // For measure nodes, prepend an expand/collapse indicator to the label
        if (node.getType() == MsmTreeNode.XmlNodeType.measure && node.getChildCount() > 0) {
            String arrow = parameters.isExpanded() ? "&#9660;" : "&#9654;";  // ▼ or ▶
            String text = node.getText(parameters);
            // insert the arrow symbol after the opening <html> tag
            if (text.startsWith("<html>"))
                return "<html><font color=\"silver\">" + arrow + "</font>&nbsp;" + text.substring(6);
            return "<html><font color=\"silver\">" + arrow + "</font>&nbsp;" + text + "</html>";
        }
        return node.getText(parameters);
    }

    /**
     * this returns the icon of the node
     * @param parameters
     * @return
     */
    @Override
    @Nullable
    protected Icon iconForValue (@NotNull final TreeNodeParameters<MsmTreeNode, WebExTree<MsmTreeNode>> parameters ) {
        return parameters.node().getNodeIcon(parameters);
    }

    /**
     * prevents wrapping
     */
    @Override
    public Dimension getPreferredSize() {
        int savedW = getWidth();
        int savedH = getHeight();
        setSize(Short.MAX_VALUE, Short.MAX_VALUE);
        Dimension d = super.getPreferredSize();
        setSize(savedW, savedH);
        return d;
    }
}
