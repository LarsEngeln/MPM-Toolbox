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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Dockable frame listing project-wide annotations grouped by score page.
 */
public class AnnotationDockableFrame extends WebDockableFrame {
    private static final String NO_SCORE_PAGE_LABEL = "Ohne ScorePage";

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

        WebScrollPane scrollPane = new WebScrollPane(this.tree);
        scrollPane.setStyleId(StyleId.scrollpaneUndecoratedButtonless);
        this.add(scrollPane);

        this.refreshTree();
        this.minimize();
    }

    public synchronized void refreshTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Annotations");
        HashMap<String, DefaultMutableTreeNode> pageGroups = new HashMap<>();
        ArrayList<ScorePage> pages = this.parent.getScore().getAllPages();
        for (ScorePage page : pages) {
            DefaultMutableTreeNode group = new DefaultMutableTreeNode(page.getFile().getName());
            pageGroups.put(page.getFile().getAbsolutePath(), group);
            root.add(group);
        }
        DefaultMutableTreeNode noPageGroup = new DefaultMutableTreeNode(NO_SCORE_PAGE_LABEL);
        root.add(noPageGroup);

        for (AnnotationData annotation : this.parent.getAnnotationManager().getAnnotations()) {
            DefaultMutableTreeNode annotationNode = new DefaultMutableTreeNode(annotation);
            DefaultMutableTreeNode targetGroup = noPageGroup;
            for (AnnotationPlacement placement : annotation.getPlacements()) {
                if ((placement.getTabType() == AnnotationPlacement.TabType.SCORE) && !placement.getPagePath().isEmpty()) {
                    DefaultMutableTreeNode group = pageGroups.get(placement.getPagePath());
                    if (group != null) {
                        targetGroup = group;
                        break;
                    }
                }
            }
            targetGroup.add(annotationNode);
        }

        this.treeModel.setRoot(root);
        for (int i = 0; i < this.tree.getRowCount(); ++i) {
            this.tree.expandRow(i);
        }
        this.tree.revalidate();
        this.tree.repaint();
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
            WebMenuItem toggleVisibility = new WebMenuItem(parent.getAnnotationManager().isOverlayVisible() ? "Hide Annotation Overlay" : "Show Annotation Overlay");
            toggleVisibility.addActionListener(actionEvent -> parent.getAnnotationManager().setOverlayVisible(!parent.getAnnotationManager().isOverlayVisible()));
            menu.add(toggleVisibility);

            WebMenuItem addAnnotation = new WebMenuItem("Add Annotation");
            addAnnotation.addActionListener(actionEvent -> {
                ScorePage page = parent.getCurrentScorePage();
                if (page != null) {
                    parent.getAnnotationManager().create(AnnotationPlacement.TabType.SCORE, page.getImage().getWidth() * 0.5, page.getImage().getHeight() * 0.5, page.getFile().getAbsolutePath());
                }
            });
            menu.add(addAnnotation);

            if (path != null) {
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

                    WebMenuItem deleteAnnotation = new WebMenuItem("Delete Annotation");
                    deleteAnnotation.addActionListener(actionEvent -> parent.getAnnotationManager().remove(annotation));
                    menu.add(deleteAnnotation);
                }
            }

            menu.show(tree, event.getX(), event.getY());
        }
    }
}

