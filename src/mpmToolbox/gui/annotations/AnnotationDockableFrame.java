package mpmToolbox.gui.annotations;

import com.alee.api.data.CompassDirection;
import com.alee.extended.dock.WebDockableFrame;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.managers.icon.Icons;
import com.alee.managers.style.StyleId;
import mpmToolbox.gui.ProjectPane;
import mpmToolbox.gui.Settings;
import mpmToolbox.projectData.annotations.AnnotationData;
import mpmToolbox.projectData.annotations.AnnotationPlacement;
import mpmToolbox.projectData.score.ScorePage;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.Color;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

/**
 * Dockable frame listing project-wide annotations, each with its placements as sub nodes.
 * @author Lars Engeln
 */
public class AnnotationDockableFrame extends WebDockableFrame {
    private final ProjectPane parent;
    private final JTree tree = new JTree() {
        @Override
        public String convertValueToText(Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Object userObject = (value instanceof DefaultMutableTreeNode) ? ((DefaultMutableTreeNode) value).getUserObject() : null;
            if (userObject instanceof AnnotationData) {
                AnnotationData annotation = (AnnotationData) userObject;
                String title = annotation.getDisplayTitle();
                if (title.isEmpty()) {
                    title = userObject.toString();
                }
                if (!annotation.getPlacements().isEmpty()) {
                    return "<html>" + escapeHtml(title) + "&nbsp;<font color=\"" + toHtmlColor(Settings.annotationCollapsedColor) + "\">&#9679;</font></html>";
                }
                return title;     // the same label as the tooltip of the collapsed overlay
            }
            return super.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
        }
    };
    private final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode("Annotations"));

    public AnnotationDockableFrame(ProjectPane parent) {
        super("annotationFrame", "Annotations");
        this.parent = parent;
        this.parent.getAnnotationManager().addChangeListener(this::refreshTree);
        this.setTitle("Annotations");
        this.setIcon(Icons.table);
        this.setClosable(false);
        this.setMaximizable(false);
        this.setPosition(CompassDirection.west);

        this.tree.setModel(this.treeModel);
        javax.swing.tree.DefaultTreeCellRenderer renderer = new javax.swing.tree.DefaultTreeCellRenderer();
        renderer.setLeafIcon(null);
        renderer.setOpenIcon(null);
        renderer.setClosedIcon(null);
        renderer.setTextNonSelectionColor(java.awt.Color.WHITE);
        renderer.setTextSelectionColor(java.awt.Color.WHITE);
        renderer.setBackgroundNonSelectionColor(new java.awt.Color(0, 0, 0, 0));
        this.tree.setCellRenderer(renderer);
        this.tree.setRootVisible(true);
        this.tree.addMouseListener(new AnnotationTreeMouseAdapter());
        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(this.tree, DnDConstants.ACTION_COPY, dragGestureEvent -> {
            TreePath path = this.tree.getPathForLocation(dragGestureEvent.getDragOrigin().x, dragGestureEvent.getDragOrigin().y);
            if (path == null) {
                return;
            }
            AnnotationData annotation = annotationOf(((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject());
            if (annotation != null) {
                this.tree.setSelectionPath(path);
                dragGestureEvent.startDrag(DragSource.DefaultCopyDrop, new AnnotationTransferable(annotation));
            }
        });

        WebScrollPane scrollPane = new WebScrollPane(this.tree);
        scrollPane.setStyleId(StyleId.scrollpaneUndecoratedButtonless);
        this.add(scrollPane);

        this.refreshTree();
        this.minimize();
    }

    public synchronized void refreshTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Annotations");
        for (AnnotationData annotation : this.parent.getAnnotationManager().getAnnotations()) {
            DefaultMutableTreeNode annotationNode = new DefaultMutableTreeNode(annotation);
            for (AnnotationPlacement placement : annotation.getPlacements()) {
                annotationNode.add(new DefaultMutableTreeNode(new PlacementEntry(annotation, placement)));
            }
            root.add(annotationNode);
        }

        // remember which annotations the user has expanded, everything else stays collapsed
        java.util.Set<AnnotationData> expanded = new java.util.HashSet<>();
        DefaultMutableTreeNode oldRoot = (DefaultMutableTreeNode) this.treeModel.getRoot();
        for (int i = 0; i < oldRoot.getChildCount(); ++i) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) oldRoot.getChildAt(i);
            if (this.tree.isExpanded(new TreePath(child.getPath())) && (child.getUserObject() instanceof AnnotationData)) {
                expanded.add((AnnotationData) child.getUserObject());
            }
        }

        this.treeModel.setRoot(root);
        this.tree.expandRow(0);
        for (int i = 0; i < root.getChildCount(); ++i) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            if (expanded.contains(child.getUserObject())) {
                this.tree.expandPath(new TreePath(child.getPath()));
            }
        }
        this.tree.revalidate();
        this.tree.repaint();
    }

    /**
     * A placement below its annotation in the tree; its text names the place where the annotation is shown.
     */
    private static class PlacementEntry {
        private final AnnotationData annotation;
        private final AnnotationPlacement placement;

        private PlacementEntry(AnnotationData annotation, AnnotationPlacement placement) {
            this.annotation = annotation;
            this.placement = placement;
        }

        @Override
        public String toString() {
            String source = this.placement.getSource();
            String name = source.isEmpty() ? "" : new File(source).getName();
            switch (this.placement.getTabType()) {
                case ANALYTICS:
                    return "[Analytics] Analytics";
                case AUDIO:
                    String pane = this.placement.getPane();
                    String audio = name.isEmpty() ? "Audio" : name;
                    return "[Audio] " + (pane.isEmpty() ? audio : (audio + " (" + pane + ")"));
                default:
                    return "[Score] " + (name.isEmpty() ? "Score" : name);
            }
        }
    }

    /**
     * @return the annotation that a tree node represents (an annotation node or one of its placement nodes), or null
     */
    private static AnnotationData annotationOf(Object userObject) {
        if (userObject instanceof AnnotationData) {
            return (AnnotationData) userObject;
        }
        return (userObject instanceof PlacementEntry) ? ((PlacementEntry) userObject).annotation : null;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String toHtmlColor(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private void removePlacement(PlacementEntry entry) {
        int index = entry.annotation.getPlacements().indexOf(entry.placement);
        if (index >= 0) {
            entry.annotation.removePlacement(index);
            this.parent.getAnnotationManager().notifyChanged();
        }
    }

    private void editAnnotation(AnnotationData annotation) {
        if (AnnotationOverlayPanel.editText(this, annotation)) {
            this.parent.getAnnotationManager().notifyChanged();
        }
    }

    private class AnnotationTreeMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent event) {
            TreePath path = tree.getPathForLocation(event.getX(), event.getY());
            if (path == null) {
                return;
            }
            tree.setSelectionPath(path);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object userObject = node.getUserObject();
            if ((event.getClickCount() > 1) && SwingUtilities.isLeftMouseButton(event) && (annotationOf(userObject) != null)) {
                editAnnotation(annotationOf(userObject));
            }
        }

        @Override
        public void mousePressed(MouseEvent event) {
            this.showMenu(event);
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            this.showMenu(event);
        }

        private void showMenu(MouseEvent event) {
            if (!event.isPopupTrigger()) {
                return;
            }

            TreePath path = tree.getPathForLocation(event.getX(), event.getY());
            if (path != null) {
                tree.setSelectionPath(path);
            }

            WebPopupMenu menu = new WebPopupMenu();
            if ((path != null) && (path.getPathCount() > 1)) {      // not for the root node
                WebMenuItem addAnnotation = new WebMenuItem("Add Annotation");
                addAnnotation.addActionListener(actionEvent -> {
                    ScorePage page = parent.getCurrentScorePage();
                    if (page != null) {
                        parent.getAnnotationManager().create(AnnotationPlacement.TabType.SCORE, page.getImage().getWidth() * 0.5, page.getImage().getHeight() * 0.5, page.getFile().getAbsolutePath());
                    }
                });
                menu.add(addAnnotation);

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObject = node.getUserObject();
                AnnotationData annotation = annotationOf(userObject);
                if (annotation != null) {
                    menu.addSeparator();

                    if (userObject instanceof PlacementEntry) {
                        WebMenuItem removePlacement = new WebMenuItem("Remove Placement");
                        removePlacement.addActionListener(actionEvent -> removePlacement((PlacementEntry) userObject));
                        menu.add(removePlacement);
                    }

                    WebMenuItem deleteAnnotation = new WebMenuItem("Delete Annotation");
                    deleteAnnotation.addActionListener(actionEvent -> parent.getAnnotationManager().remove(annotation));
                    menu.add(deleteAnnotation);
                }
            }

            if (menu.getComponentCount() > 0)
                menu.show(tree, event.getX(), event.getY());
        }
    }
}
