package mpmToolbox.gui.annotations;

import com.alee.laf.label.WebLabel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.text.WebTextArea;
import com.alee.laf.text.WebTextField;
import mpmToolbox.gui.Settings;
import mpmToolbox.gui.mpmEditingTools.editDialogs.EditDialog;
import mpmToolbox.projectData.annotations.AnnotationData;

import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.Component;
import java.awt.GridBagConstraints;

/**
 * Dialog for creating or editing an annotation's title, locus, measure range, and text.
 * Existing annotations are updated only when the dialog is confirmed.
 * @author Lars Engeln
 */
public class AnnotationEditor extends EditDialog<AnnotationData> {
    private WebTextField title;
    private WebTextField part;
    private WebTextField measureFrom;
    private WebTextField measureTo;
    private WebTextArea text;

    /**
     * Creates the editor and centers it relative to the given component.
     * @param parent component relative to which the dialog is positioned
     */
    public AnnotationEditor(Component parent) {
        super("Edit annotation");
        this.setLocationRelativeTo(parent);
    }

    /**
     * Builds the annotation fields and their measure-range input constraints.
     */
    @Override
    public void makeContentPanel() {
        this.title = new WebTextField();
        this.title.setPadding(Settings.paddingInDialogs);
        this.title.setToolTipText("Enter the annotation title.");
        this.addField("Title", this.title, 0);

        this.part = new WebTextField();
        this.part.setPadding(Settings.paddingInDialogs);
        this.part.setToolTipText("Enter the locus or leave it empty.");
        this.addField("Locus:", this.part, 1);

        this.measureFrom = new WebTextField();
        this.measureTo = new WebTextField();
        this.measureFrom.setPadding(Settings.paddingInDialogs);
        this.measureTo.setPadding(Settings.paddingInDialogs);
        this.measureFrom.setColumns(5);
        this.measureTo.setColumns(5);
        ((AbstractDocument) this.measureFrom.getDocument()).setDocumentFilter(new MeasureInputFilter());
        ((AbstractDocument) this.measureTo.getDocument()).setDocumentFilter(new MeasureInputFilter());
        String measureHint = "Enter an integer, ? for unknown, or leave empty.";
        this.measureFrom.setToolTipText(measureHint);
        this.measureTo.setToolTipText(measureHint);
        this.measureTo.setEnabled(false);
        this.measureFrom.getDocument().addDocumentListener(new DocumentListener() {
            private void updateMeasureToEnabled() {
                measureTo.setEnabled(!measureFrom.getText().isEmpty());
                if (!measureTo.isEnabled()) {
                    // Defer clearing until the document notification has finished.
                    SwingUtilities.invokeLater(() -> {
                        if (measureFrom.getText().isEmpty()) {
                            measureTo.setText("");
                        }
                    });
                }
            }

            @Override
            public void insertUpdate(DocumentEvent event) {
                this.updateMeasureToEnabled();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                this.updateMeasureToEnabled();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                this.updateMeasureToEnabled();
            }
        });
        WebLabel measureLabel = new WebLabel("Measure:");
        measureLabel.setPadding(Settings.paddingInDialogs);
        this.addToContentPanel(measureLabel, 0, 2, 1, 1, 0.0, 0.0, 0, 0, GridBagConstraints.NONE);
        this.addToContentPanel(this.measureFrom, 1, 2, 1, 1, 0.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL);
        this.addToContentPanel(new WebLabel("-"), 2, 2, 1, 1, 0.0, 0.0, 0, 0, GridBagConstraints.NONE);
        this.addToContentPanel(this.measureTo, 3, 2, 1, 1, 1.0, 0.0, 0, 0, GridBagConstraints.NONE);

        this.text = new WebTextArea("", 8, 36);
        this.text.setLineWrap(true);
        this.text.setWrapStyleWord(true);
        this.addToContentPanel(new WebScrollPane(this.text), 0, 3, 4, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH);
    }

    /**
     * Adds a labeled text field to the specified row of the content panel.
     * @param label field label
     * @param field input field
     * @param row grid row
     */
    private void addField(String label, WebTextField field, int row) {
        WebLabel fieldLabel = new WebLabel(label);
        fieldLabel.setPadding(Settings.paddingInDialogs);
        this.addToContentPanel(fieldLabel, 0, row, 1, 1, 0.0, 0.0, 0, 0, GridBagConstraints.NONE);
        this.addToContentPanel(field, 1, row, 3, 1, 1.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL);
    }

    /**
     * Opens the editor, applying changes only if the user confirms the dialog.
     * @param annotation annotation to edit, or null to create one
     * @return the edited or newly created annotation; the original annotation on cancellation,
     *         or null if creation was canceled
     */
    @Override
    public AnnotationData edit(AnnotationData annotation) {
        if (annotation != null) {
            this.title.setText(annotation.getTitle());
            this.part.setText(annotation.getPart());
            this.measureFrom.setText(annotation.getMeasureFrom());
            this.measureTo.setText(annotation.getMeasureTo());
            this.text.setText(annotation.getText());
        }

        this.setVisible(true);
        if (!this.isOk()) {
            return annotation;
        }

        if (annotation == null) {
            annotation = new AnnotationData();
        }
        annotation.setTitle(this.title.getText());
        annotation.setPart(this.part.getText());
        annotation.setMeasureFrom(this.measureFrom.getText());
        annotation.setMeasureTo(this.measureTo.getText());
        annotation.setText(this.text.getText());
        return annotation;
    }

    /**
     * Accepts integers, a question mark, and intermediate empty or minus-only input.
     * An invalid edit clears the field.
     */
    private static class MeasureInputFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attributes) throws BadLocationException {
            this.replace(fb, offset, 0, text, attributes);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attributes) throws BadLocationException {
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            String replacement = (text == null) ? "" : text;
            String candidate = current.substring(0, offset) + replacement + current.substring(offset + length);
            if (candidate.matches("-?[0-9]+|\\?|") || candidate.equals("-")) {
                fb.replace(offset, length, replacement, attributes);
            } else {
                fb.replace(0, fb.getDocument().getLength(), "", attributes);
            }
        }
    }
}
