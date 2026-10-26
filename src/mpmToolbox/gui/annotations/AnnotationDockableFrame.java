package mpmToolbox.gui.annotations;

import com.alee.api.data.CompassDirection;
import com.alee.extended.dock.WebDockableFrame;
import com.alee.laf.menu.WebMenu;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.managers.icon.Icons;
import com.alee.managers.style.StyleId;
import mpmToolbox.gui.ProjectPane;
import mpmToolbox.projectData.annotations.AnnotationData;
import mpmToolbox.projectData.annotations.AnnotationLink;
import mpmToolbox.projectData.annotations.AnnotationPlacement;
import mpmToolbox.projectData.score.ScorePage;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/**
 * Dockable frame listing project-wide annotations grouped by score page, audio file and analytics.
 */
public class AnnotationDockableFrame extends WebDockableFrame {
    private static final String UNPLACED_LABEL = "Without Placement";

    private final ProjectPane parent;
    private final JTree tree = new JTree();
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
        this.tree.setRootVisible(true);
        this.tree.addMouseListener(new AnnotationTreeMouseAdapter());
        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(this.tree, DnDConstants.ACTION_COPY, dragGestureEvent -> {
            TreePath path = this.tree.getPathForLocation(dragGestureEvent.getDragOrigin().x, dragGestureEvent.getDragOrigin().y);
            if (path == null) {
                return;
            }
            Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            if (userObject instanceof AnnotationData) {
                this.tree.setSelectionPath(path);
                dragGestureEvent.startDrag(DragSource.DefaultCopyDrop, new AnnotationTransferable((AnnotationData) userObject));
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
        LinkedHashMap<String, DefaultMutableTreeNode> scoreGroups = new LinkedHashMap<>();
        LinkedHashMap<String, DefaultMutableTreeNode> audioGroups = new LinkedHashMap<>();
        DefaultMutableTreeNode analyticsGroup = new DefaultMutableTreeNode(new PlacementGroup(AnnotationPlacement.TabType.ANALYTICS, "", "Analytics"));
        DefaultMutableTreeNode unplacedGroup = new DefaultMutableTreeNode(new PlacementGroup(null, "", UNPLACED_LABEL));

        for (ScorePage page : this.parent.getScore().getAllPages()) {       // this defines the order of the pages
            scoreGroups.put(page.getFile().getAbsolutePath(), new DefaultMutableTreeNode(new PlacementGroup(AnnotationPlacement.TabType.SCORE, page.getFile().getAbsolutePath(), page.getFile().getName())));
        }

        for (AnnotationData annotation : this.parent.getAnnotationManager().getAnnotations()) {
            LinkedHashSet<DefaultMutableTreeNode> targetGroups = new LinkedHashSet<>();
            for (AnnotationPlacement placement : annotation.getPlacements()) {
                switch (placement.getTabType()) {
                    case ANALYTICS:
                        targetGroups.add(analyticsGroup);
                        break;
                    case AUDIO:
                        targetGroups.add(audioGroups.computeIfAbsent(placement.getPagePath(), path -> new DefaultMutableTreeNode(new PlacementGroup(AnnotationPlacement.TabType.AUDIO, path, path.isEmpty() ? "Audio" : new File(path).getName()))));
                        break;
                    default:
                        targetGroups.add(scoreGroups.computeIfAbsent(placement.getPagePath(), path -> new DefaultMutableTreeNode(new PlacementGroup(AnnotationPlacement.TabType.SCORE, path, path.isEmpty() ? "Score" : new File(path).getName()))));
                }
            }
            if (targetGroups.isEmpty()) {
                targetGroups.add(unplacedGroup);
            }
            for (DefaultMutableTreeNode group : targetGroups) {
                group.add(new DefaultMutableTreeNode(annotation));
            }
        }

        ArrayList<DefaultMutableTreeNode> groups = new ArrayList<>(scoreGroups.values());
        groups.addAll(audioGroups.values());
        groups.add(analyticsGroup);
        groups.add(unplacedGroup);
        for (DefaultMutableTreeNode group : groups) {
            if (group.getChildCount() > 0) {        // only groups with annotations are shown
                root.add(group);
            }
        }

        this.treeModel.setRoot(root);
        for (int i = 0; i < this.tree.getRowCount(); ++i) {
            this.tree.expandRow(i);
        }
        this.tree.revalidate();
        this.tree.repaint();
    }

    /**
     * A group in the tree: a score page, an audio file, the analytics tab, or (tabType null) the annotations without placement.
     */
    private static class PlacementGroup {
        private final AnnotationPlacement.TabType tabType;
        private final String path;
        private final String label;

        private PlacementGroup(AnnotationPlacement.TabType tabType, String path, String label) {
            this.tabType = tabType;
            this.path = path;
            this.label = label;
        }

        private boolean contains(AnnotationPlacement placement) {
            if (placement.getTabType() != this.tabType) {
                return false;
            }
            return (this.tabType == AnnotationPlacement.TabType.ANALYTICS) || placement.getPagePath().equals(this.path);
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    /**
     * @param groupNode the tree group in which the annotation is listed
     * @return the indices of the annotation's placements that belong to the group
     */
    private ArrayList<Integer> placementIndicesInGroup(AnnotationData annotation, DefaultMutableTreeNode groupNode) {
        Object group = (groupNode == null) ? null : groupNode.getUserObject();
        ArrayList<Integer> indices = new ArrayList<>();
        if (!(group instanceof PlacementGroup)) {
            return indices;
        }
        for (int i = 0; i < annotation.getPlacements().size(); ++i) {
            if (((PlacementGroup) group).contains(annotation.getPlacements().get(i))) {
                indices.add(i);
            }
        }
        return indices;
    }

    private void removePlacements(AnnotationData annotation, ArrayList<Integer> indices) {
        for (int i = indices.size() - 1; i >= 0; --i) {
            annotation.removePlacement(indices.get(i));
        }
        this.parent.getAnnotationManager().notifyChanged();
    }

    private void editAnnotationText(AnnotationData annotation) {
        String current = annotation.getText();
        String updated = JOptionPane.showInputDialog(this, "Annotation text:", current);
        if (updated == null) {
            return;
        }
        annotation.setText(updated);
        this.parent.getAnnotationManager().notifyChanged();
    }

    private void addLink(AnnotationData annotation) {
        this.parent.getAnnotationManager().startLinking(annotation);        // the next click in a tab defines the link
    }

    private void removeLink(AnnotationData annotation) {
        if (annotation.getLinks().isEmpty()) {
            return;
        }
        String[] options = new String[annotation.getLinks().size()];
        for (int i = 0; i < annotation.getLinks().size(); ++i) {
            AnnotationLink link = annotation.getLinks().get(i);
            options[i] = i + ": " + link.getType() + " -> " + link.getTarget();
        }
        String selected = (String) JOptionPane.showInputDialog(this, "Remove which link?", "Remove Link", JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (selected == null) {
            return;
        }
        int index = Integer.parseInt(selected.substring(0, selected.indexOf(':')));
        annotation.removeLink(index);
        this.parent.getAnnotationManager().notifyChanged();
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
            if ((event.getClickCount() > 1) && SwingUtilities.isLeftMouseButton(event) && (userObject instanceof AnnotationData)) {
                editAnnotationText((AnnotationData) userObject);
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
                if (userObject instanceof AnnotationData) {
                    AnnotationData annotation = (AnnotationData) userObject;
                    menu.addSeparator();

                    WebMenuItem editText = new WebMenuItem("Edit Text");
                    editText.addActionListener(actionEvent -> editAnnotationText(annotation));
                    menu.add(editText);

                    WebMenu linksMenu = new WebMenu("Links");
                    WebMenuItem addLinkItem = new WebMenuItem("Add Link (click on target)");
                    addLinkItem.addActionListener(actionEvent -> addLink(annotation));
                    linksMenu.add(addLinkItem);
                    WebMenuItem removeLinkItem = new WebMenuItem("Remove Link");
                    removeLinkItem.setEnabled(!annotation.getLinks().isEmpty());
                    removeLinkItem.addActionListener(actionEvent -> removeLink(annotation));
                    linksMenu.add(removeLinkItem);
                    menu.add(linksMenu);

                    ArrayList<Integer> groupPlacements = placementIndicesInGroup(annotation, (DefaultMutableTreeNode) node.getParent());
                    if (!groupPlacements.isEmpty()) {
                        WebMenuItem removePlacement = new WebMenuItem("Remove Placement");
                        removePlacement.addActionListener(actionEvent -> removePlacements(annotation, groupPlacements));
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

