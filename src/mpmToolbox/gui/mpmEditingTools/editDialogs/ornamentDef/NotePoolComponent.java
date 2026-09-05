package mpmToolbox.gui.mpmEditingTools.editDialogs.ornamentDef;

import com.alee.laf.button.WebButton;
import com.alee.laf.combobox.WebComboBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.text.WebTextField;
import com.alee.managers.style.StyleId;
import meico.mei.ornament.OrnamentDictionary;
import mpmToolbox.gui.Settings;
import mpmToolbox.supplementary.Tools;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class represents the notePool sub-panel in ornament editing dialogs.
 * It allows creating alternating note alterations as relative steps around the principal note.
 * @author Axel Berndt
 */
public class NotePoolComponent extends WebPanel {
    private static final String REPEAT_START = "|:";
    private static final String REPEAT_END = ":|";
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[-+]?\\d+");

    private final OrnamentDictionary ornamentDictionary = new OrnamentDictionary();
    private final ArrayList<AlterationField> fields = new ArrayList<>();
    private final WebComboBox presetChooser;
    private final WebPanel fieldsPanel = new WebPanel(new FlowLayout(FlowLayout.LEFT, Settings.paddingInDialogs / 2, 0));
    private final WebButton addFieldButton = new WebButton("+");
    private AlterationField selectedField = null;
    private AlterationField draggedField = null;
    private boolean dragging = false;

    /**
     * constructor
     */
    public NotePoolComponent() {
        super();
        GridBagLayout layout = new GridBagLayout();
        this.setLayout(layout);
        this.setBorder(BorderFactory.createCompoundBorder(new LineBorder(this.getBackground(), Settings.paddingInDialogs / 2), new EmptyBorder(Settings.paddingInDialogs, Settings.paddingInDialogs, Settings.paddingInDialogs, Settings.paddingInDialogs)));
        this.setBackground(Tools.brighter(this.getBackground(), 0.07));

        WebLabel presetsLabel = new WebLabel("Standard Alterations");
        presetsLabel.setHorizontalAlignment(WebLabel.RIGHT);
        presetsLabel.setPadding(0, 0, 0, Settings.paddingInDialogs);
        Tools.addComponentToGridBagLayout(this, layout, presetsLabel, 0, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.LINE_START);

        ArrayList<String> presetNames = new ArrayList<>();
        if (this.ornamentDictionary.getOrnamentLookup() != null)
            presetNames.addAll(this.ornamentDictionary.getOrnamentLookup().keySet());
        Collections.sort(presetNames);
        this.presetChooser = new WebComboBox(presetNames.toArray(new String[0]));
        this.presetChooser.setToolTip("Choose a standard ornament note-pool pattern from meico's OrnamentDictionary.");
        Tools.addComponentToGridBagLayout(this, layout, this.presetChooser, 1, 0, 2, 1, 4.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.LINE_START);

        WebButton applyPreset = new WebButton("Apply", actionEvent -> this.applySelectedPreset());
        applyPreset.setToolTip("Replace the current note-pool by the selected standard pattern.");
        applyPreset.setEnabled(!presetNames.isEmpty());
        Tools.addComponentToGridBagLayout(this, layout, applyPreset, 3, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.LINE_START);

        WebLabel listLabel = new WebLabel("Note Pool");
        listLabel.setHorizontalAlignment(WebLabel.RIGHT);
        listLabel.setPadding(Settings.paddingInDialogs, 0, Settings.paddingInDialogs, Settings.paddingInDialogs);
        Tools.addComponentToGridBagLayout(this, layout, listLabel, 0, 1, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.LINE_START);

        this.fieldsPanel.setToolTip("Drag and drop alteration fields to change order.");
        WebScrollPane fieldsScrollPane = new WebScrollPane(this.fieldsPanel);
        fieldsScrollPane.setStyleId(StyleId.scrollpaneUndecoratedButtonless);
        int width = getFontMetrics(this.getFont()).stringWidth("999999999999999");
        int height = (int) (this.getFont().getSize() * 8.0);
        fieldsScrollPane.setMinimumWidth(width);
        fieldsScrollPane.setMaximumWidth(Integer.MAX_VALUE);
        fieldsScrollPane.setMinimumHeight(height);
        fieldsScrollPane.setMaximumHeight(height);
        fieldsScrollPane.setPreferredSize(new Dimension(width, height));
        Tools.addComponentToGridBagLayout(this, layout, fieldsScrollPane, 1, 1, 2, 1, 4.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.LINE_START);

        this.addFieldButton.setPadding(Settings.paddingInDialogs);
        this.addFieldButton.setToolTip("Add a new alteration field.");
        this.addFieldButton.addActionListener(actionEvent -> this.addField("0", this.getInsertIndex()));
        this.refreshFieldsPanel();

        WebPanel repeatButtons = new WebPanel(new GridBagLayout());
        WebButton addRepeatStart = new WebButton(REPEAT_START, actionEvent -> this.addField(REPEAT_START, this.getInsertIndex()));
        addRepeatStart.setPadding(Settings.paddingInDialogs);
        addRepeatStart.setToolTip("Insert repetition start marker.");
        Tools.addComponentToGridBagLayout(repeatButtons, (GridBagLayout) repeatButtons.getLayout(), addRepeatStart, 0, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.LINE_START);

        WebButton addRepeatEnd = new WebButton(REPEAT_END, actionEvent -> this.addField(REPEAT_END, this.getInsertIndex()));
        addRepeatEnd.setPadding(Settings.paddingInDialogs);
        addRepeatEnd.setToolTip("Insert repetition end marker.");
        Tools.addComponentToGridBagLayout(repeatButtons, (GridBagLayout) repeatButtons.getLayout(), addRepeatEnd, 0, 1, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.LINE_START);

        Tools.addComponentToGridBagLayout(this, layout, repeatButtons, 3, 1, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.LINE_START);
    }

    /**
     * set the note-pool values.
     * @param alterations list of relative alteration entries
     */
    public void setNotePool(List<String> alterations) {
        this.fields.clear();
        this.selectedField = null;

        if (alterations != null) {
            for (String alteration : alterations) {
                if (alteration == null)
                    continue;
                String normalized = normalizeToken(alteration);
                if (!normalized.isEmpty())
                    this.addFieldInternal(normalized, this.fields.size());
            }
        }

        if (!this.fields.isEmpty())
            this.setSelectedField(this.fields.get(0));
        this.refreshFieldsPanel();
    }

    /**
     * read the note-pool values from the fields.
     * @return list of relative alteration entries
     */
    public ArrayList<String> getNotePool() {
        ArrayList<String> output = new ArrayList<>();
        for (AlterationField field : this.fields)
            output.add(field.getNormalizedValue());
        return output;
    }

    private void applySelectedPreset() {
        if (this.presetChooser.getSelectedItem() == null)
            return;

        String preset = this.presetChooser.getSelectedItem().toString();
        List<String> values = this.ornamentDictionary.get(preset);
        if (values == null)
            return;

        this.setNotePool(values);
    }

    private void addField(String value, int index) {
        this.addFieldInternal(normalizeToken(value), index);
        this.refreshFieldsPanel();
    }

    private void addFieldInternal(String value, int index) {
        final AlterationField field = new AlterationField(value);
        field.setRemoveAction(() -> this.removeField(field));
        this.installInteractionSupport(field);

        int i = Math.max(0, Math.min(index, this.fields.size()));
        this.fields.add(i, field);
        this.setSelectedField(field);
    }

    private void removeField(AlterationField field) {
        if (field == null)
            return;
        int index = this.fields.indexOf(field);
        if (index < 0)
            return;

        this.fields.remove(index);
        if (field == this.selectedField) {
            if (this.fields.isEmpty()) {
                this.selectedField = null;
            } else {
                this.setSelectedField(this.fields.get(Math.min(index, this.fields.size() - 1)));
            }
        }
        this.refreshFieldsPanel();
    }

    private int getInsertIndex() {
        if (this.selectedField == null)
            return this.fields.size();
        return this.fields.indexOf(this.selectedField) + 1;
    }

    private void refreshFieldsPanel() {
        this.fieldsPanel.removeAll();
        for (AlterationField field : this.fields)
            this.fieldsPanel.add(field);
        this.fieldsPanel.add(this.addFieldButton);
        this.fieldsPanel.revalidate();
        this.fieldsPanel.repaint();
    }

    private void setSelectedField(AlterationField field) {
        if (this.selectedField != null)
            this.selectedField.setSelected(false);
        this.selectedField = field;
        if (this.selectedField != null)
            this.selectedField.setSelected(true);
    }

    private void moveDraggedFieldTo(Point dropPoint) {
        if (this.draggedField == null)
            return;

        int from = this.fields.indexOf(this.draggedField);
        if (from < 0)
            return;

        int to = this.computeDropIndex(dropPoint.x);
        if (to > from)
            to--;
        if ((to < 0) || (to == from))
            return;

        this.fields.remove(from);
        this.fields.add(to, this.draggedField);
        this.setSelectedField(this.draggedField);
        this.refreshFieldsPanel();
    }

    private int computeDropIndex(int x) {
        for (int i = 0; i < this.fields.size(); ++i) {
            Rectangle b = this.fields.get(i).getBounds();
            if (x < (b.x + (b.width / 2)))
                return i;
        }
        return this.fields.size();
    }

    private void installInteractionSupport(AlterationField field) {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                field.setHovering(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (!field.isPointerInside())
                        field.setHovering(false);
                });
            }

            @Override
            public void mousePressed(MouseEvent e) {
                setSelectedField(field);
                draggedField = field;
                dragging = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                dragging = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if ((draggedField == null) || !dragging)
                    return;
                Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), fieldsPanel);
                moveDraggedFieldTo(p);
                draggedField = null;
                dragging = false;
            }
        };
        field.addInteractionListener(adapter);
    }

    private static String normalizeToken(String input) {
        if (input == null)
            return "";

        String token = input.trim();
        if (REPEAT_START.equals(token) || REPEAT_END.equals(token))
            return token;
        if (INTEGER_PATTERN.matcher(token).matches()) {
            try {
                return Integer.toString(Integer.parseInt(token));
            } catch (NumberFormatException e) {
                return "0";
            }
        }
        return "0";
    }

    private static boolean isIntegerToken(String token) {
        return (token != null) && INTEGER_PATTERN.matcher(token.trim()).matches();
    }

    /**
     * enable/disable the components
     * @param enabled true if this component should be enabled, false otherwise
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (Component c : this.getComponents()) {
            c.setEnabled(enabled);
            if (c instanceof Container)
                this.setEnabledRecursively((Container) c, enabled);
        }
    }

    private void setEnabledRecursively(Container container, boolean enabled) {
        for (Component child : container.getComponents()) {
            child.setEnabled(enabled);
            if (child instanceof Container)
                this.setEnabledRecursively((Container) child, enabled);
        }
    }

    /**
     * one editable alteration field with increase/decrease buttons.
     */
    private static class AlterationField extends WebPanel {
        private final WebTextField valueField = new WebTextField("0");
        private final WebButton upButton = new WebButton("▲");
        private final WebButton downButton = new WebButton("▼");
        private final WebButton removeButton = new WebButton("-");
        private Runnable removeAction = null;

        AlterationField(String value) {
            super(new GridBagLayout());
            this.setPadding(Settings.paddingInDialogs / 2);
            this.setBorder(new LineBorder(this.getBackground(), 1));

            this.valueField.setHorizontalAlignment(WebTextField.CENTER);
            this.valueField.setText(normalizeToken(value));
            int width = this.getFontMetrics(this.getFont()).stringWidth("99999");
            this.valueField.setMinimumWidth(width);
            this.valueField.setMaximumWidth(width);
            this.valueField.setToolTip("Allowed values: integer, |:, :|");

            this.upButton.setPadding(0);
            this.upButton.setToolTip("Raise this alteration by one step.");
            this.upButton.addActionListener(actionEvent -> this.shift(1));
            this.downButton.setPadding(0);
            this.downButton.setToolTip("Lower this alteration by one step.");
            this.downButton.addActionListener(actionEvent -> this.shift(-1));

            this.removeButton.setPadding(0);
            this.removeButton.setToolTip("Remove this alteration.");
            this.removeButton.setText(" ");
            this.removeButton.setEnabled(false);
            this.removeButton.addActionListener(actionEvent -> {
                if (this.removeAction != null)
                    this.removeAction.run();
            });

            this.valueField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    normalizeValue();
                }
            });
            this.valueField.addActionListener(actionEvent -> normalizeValue());
            this.valueField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    updateArrowAvailability();
                }
            });

            GridBagLayout layout = (GridBagLayout) this.getLayout();
            Tools.addComponentToGridBagLayout(this, layout, this.upButton, 0, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.CENTER);
            Tools.addComponentToGridBagLayout(this, layout, this.valueField, 0, 1, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.CENTER);
            Tools.addComponentToGridBagLayout(this, layout, this.downButton, 0, 2, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.CENTER);
            Tools.addComponentToGridBagLayout(this, layout, this.removeButton, 0, 3, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.CENTER);

            this.updateArrowAvailability();
        }

        void setRemoveAction(Runnable removeAction) {
            this.removeAction = removeAction;
        }

        String getNormalizedValue() {
            this.normalizeValue();
            return this.valueField.getText().trim();
        }

        void setSelected(boolean selected) {
            if (selected)
                this.setBorder(new LineBorder(Color.GRAY, 1));
            else
                this.setBorder(new LineBorder(this.getBackground(), 1));
        }

        void setHovering(boolean hovering) {
            this.removeButton.setText(hovering ? "-" : " ");
            this.removeButton.setEnabled(hovering);
        }

        void addInteractionListener(MouseAdapter adapter) {
            this.addMouseListener(adapter);
            this.addMouseMotionListener(adapter);
            this.valueField.addMouseListener(adapter);
            this.valueField.addMouseMotionListener(adapter);
            this.upButton.addMouseListener(adapter);
            this.upButton.addMouseMotionListener(adapter);
            this.downButton.addMouseListener(adapter);
            this.downButton.addMouseMotionListener(adapter);
            this.removeButton.addMouseListener(adapter);
            this.removeButton.addMouseMotionListener(adapter);
        }

        boolean isPointerInside() {
            PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi == null)
                return false;
            Point p = new Point(pi.getLocation());
            SwingUtilities.convertPointFromScreen(p, this);
            return this.contains(p);
        }

        private void normalizeValue() {
            this.valueField.setText(normalizeToken(this.valueField.getText()));
            this.updateArrowAvailability();
        }

        private void updateArrowAvailability() {
            boolean integerToken = isIntegerToken(this.valueField.getText());
            this.upButton.setVisible(integerToken);
            this.downButton.setVisible(integerToken);
        }

        private void shift(int delta) {
            String value = this.valueField.getText();
            if (!isIntegerToken(value))
                return;

            try {
                int parsed = Integer.parseInt(value.trim());
                this.valueField.setText(Integer.toString(parsed + delta));
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
