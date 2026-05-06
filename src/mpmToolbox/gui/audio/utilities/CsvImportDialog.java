package mpmToolbox.gui.audio.utilities;

import com.alee.laf.button.WebButton;
import com.alee.laf.combobox.WebComboBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.table.WebTable;
import com.alee.laf.text.WebTextField;
import com.alee.laf.window.WebDialog;
import mpmToolbox.gui.Settings;
import mpmToolbox.gui.audio.AnnotationData;
import mpmToolbox.gui.audio.AnnotationLine;
import mpmToolbox.supplementary.Tools;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * A modal dialog for configuring CSV annotation import, and for editing existing AnnotationData objects.
 * Parses the CSV file into an AnnotationData object and lets the user configure column types and units.
 * Can also receive an existing AnnotationData to edit its column metadata.
 * @author Lars Engeln
 */
public class CsvImportDialog extends WebDialog<CsvImportDialog> {
    private boolean ok = false;

    // the current AnnotationData, either parsed from CSV or an existing one passed in for editing
    private AnnotationData annotationData;

    // per-column UI controls (indices match annotationData lines)
    private WebComboBox[]  typeChoosers;
    private WebComboBox[]  unitChoosers;
    private WebTextField[] nameFields;

    // config panel, replaced when columns change
    private WebPanel configPanel;

    // target selection (replace existing vs. create new)
    private WebComboBox  targetChooser;
    private WebTextField newNameField;
    private final ArrayList<AnnotationData> existingData;

    // preview table model, updated when columns are deleted
    private DefaultTableModel tableModel;

    private static final String NEW_ENTRY = "<New annotation>";

    // UI representations of the type enum (for combo boxes)
    private static final AnnotationLine.Type[] TYPES = AnnotationLine.Type.values();

    /**
     * Constructor for CSV import: parse the file and let the user configure columns.
     * @param file         the CSV file to import
     * @param existingData existing AnnotationData objects the user may choose to replace
     */
    public CsvImportDialog(File file, ArrayList<AnnotationData> existingData) {
        super();
        this.existingData = (existingData != null) ? existingData : new ArrayList<>();
        this.annotationData = parseFile(file);
        this.setTitle("Import CSV: " + this.annotationData.getName());
        this.init();
    }

    /**
     * Constructor for editing an existing AnnotationData (column types/units/name only – rows are unchanged).
     * @param data         the AnnotationData to edit
     * @param existingData all known AnnotationData objects (for the target chooser)
     */
    public CsvImportDialog(AnnotationData data, ArrayList<AnnotationData> existingData) {
        super();
        this.existingData = (existingData != null) ? existingData : new ArrayList<>();
        this.annotationData = data;
        this.setTitle("Edit Annotation: " + data.getName());
        this.init();
    }

    /**
     * initializes the dialog properties and builds the GUI
     */
    private void init() {
        this.setIconImages(Settings.getIcons(null));
        this.setModal(true);
        this.setResizable(true);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { ok = false; dispose(); }
        });
        this.initKeyboardShortcuts();
        this.buildGui();
        this.pack();
        this.setMinimumSize(new Dimension(520, 420));
        this.setLocationRelativeTo(null);
    }

    /**
     * Parse a CSV file into an AnnotationData object.
     * Applies smart defaults for column types and units.
     * @param file the CSV file
     * @return the parsed AnnotationData (rows + columns with defaults)
     */
    public static AnnotationData parseFile(File file) {
        ArrayList<String> rawLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    // strip UTF-8 BOM if present
                    if (line.startsWith("\uFEFF")) line = line.substring(1);
                    first = false;
                }
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#"))
                    rawLines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new AnnotationData(file.getName());
        }

        if (rawLines.isEmpty())
            return new AnnotationData(file.getName());

        String delimiter = detectDelimiter(rawLines.get(0));

        // split all lines
        ArrayList<String[]> parsed = new ArrayList<>();
        for (String raw : rawLines)
            parsed.add(raw.split(delimiter, -1));

        int colCount = parsed.get(0).length;

        // detect header
        String[] headerRow = null;
        if (isHeaderRow(parsed.get(0))) {
            headerRow = parsed.remove(0);
            for (int i = 0; i < headerRow.length; i++)
                headerRow[i] = headerRow[i].trim();
        }

        // build AnnotationData
        String name = file.getName();
        if (name.contains(".csv"))
            name = name.substring(0, name.lastIndexOf('.')); // strip file extension for default name
        AnnotationData data = new AnnotationData(name);

        // add lines with defaults
        for (int c = 0; c < colCount; c++) {
            String colName = (headerRow != null && c < headerRow.length) ? headerRow[c] : ("Column " + (c + 1));
            AnnotationLine.Type type;
            AnnotationLine.Unit unit;
            if (colCount == 1) {
                type = AnnotationLine.Type.MARKS;
                unit = AnnotationLine.Unit.SECONDS;
            } else if (c == 0) {
                type = AnnotationLine.Type.TIME;
                unit = AnnotationLine.Unit.SECONDS;
            } else {
                type = AnnotationLine.Type.CURVE;
                unit = AnnotationLine.Unit.HZ;
            }
            data.addLine(new AnnotationLine(colName, type, unit));
        }

        // add rows, distribute each cell value into its column's AnnotationLine
        int maxRowCount = 0;
        for (String[] row : parsed) {
            double[] values = new double[colCount];
            boolean valid = true;
            maxRowCount = Math.max(maxRowCount, row.length);
            for (int c = 0; c < row.length; c++) {
                if(row[c].trim().equals(""))
                    continue;
                try {
                    values[c] = Double.parseDouble(row[c].trim());      // TODO: parseDouble if CURVE & MARKS, may extend with TEXT later
                } catch (NumberFormatException e) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                for (int c = 0; c < colCount; c++)
                    data.getLine(c).addValue(values[c]);
            }
        }
        if(maxRowCount < colCount){
            // if there are more header columns than value columns, delete all leftover columns/lines
            for(int c = maxRowCount; c < colCount; c++) {
                data.getLines().remove(c);
            }
        }

        return data;
    }

    /**
     * Detects the delimiter used in the CSV line. Checks for tab, then semicolon, defaults to comma.
     * @param line
     * @return
     */
    private static String detectDelimiter(String line) {
        if (line.contains("\t")) return "\t";
        if (line.contains(";"))  return ";";
        return ",";
    }

    /**
     * Detects whether the given row is likely a header row. If any cell cannot be parsed as a number, we assume it's a header. TODO: change to user select with checkbox "First row is header"
     * @param row
     * @return
     */
    private static boolean isHeaderRow(String[] row) {
        for (String cell : row) {
            try { Double.parseDouble(cell.trim()); }
            catch (NumberFormatException e) { return true; }
        }
        return false;
    }

    /**
     * Builds the GUI components and layout.
     */
    private void buildGui() {
        GridBagLayout mainLayout = new GridBagLayout();
        this.setLayout(mainLayout);
        int row = 0;

        WebPanel targetPanel = new WebPanel(new GridBagLayout());
        targetPanel.setPadding(Settings.paddingInDialogs);
        GridBagLayout targetLayout = (GridBagLayout) targetPanel.getLayout();

        WebLabel targetLabel = new WebLabel("Import as:");
        targetLabel.setFontStyle(Font.BOLD);
        targetLabel.setPadding(2, 4, 2, 8);
        Tools.addComponentToGridBagLayout(targetPanel, targetLayout, targetLabel, 0, 0, 1, 1, 0.0, 1.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.LINE_START);

        String[] targetItems = new String[this.existingData.size() + 1];
        targetItems[0] = NEW_ENTRY;
        for (int i = 0; i < this.existingData.size(); i++)
            targetItems[i + 1] = this.existingData.get(i).getName();

        this.targetChooser = new WebComboBox(targetItems);
        for (int i = 0; i < this.existingData.size(); i++) {
            if (this.existingData.get(i) == this.annotationData) {
                this.targetChooser.setSelectedIndex(i + 1);
                break;
            }
        }
        Tools.addComponentToGridBagLayout(targetPanel, targetLayout, this.targetChooser, 1, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.CENTER);

        this.newNameField = new WebTextField(this.annotationData.getName());
        this.newNameField.setToolTipText("Name for the new annotation dataset");
        this.newNameField.setVisible(this.targetChooser.getSelectedIndex() == 0);
        Tools.addComponentToGridBagLayout(targetPanel, targetLayout, this.newNameField, 2, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.CENTER);

        Tools.addComponentToGridBagLayout(this, mainLayout, targetPanel, 0, row++, 1, 1, 1.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.LINE_START);

        // --- Column configuration panel (rebuilt dynamically) ---
        this.configPanel = new WebPanel(new GridBagLayout());
        this.configPanel.setPadding(Settings.paddingInDialogs);
        this.rebuildConfigPanel();
        Tools.addComponentToGridBagLayout(this, mainLayout, this.configPanel, 0, row++, 1, 1, 1.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.LINE_START);

        // targetChooser: when an existing AnnotationData is selected, mirror its lines into the config
        this.targetChooser.addActionListener(e -> {
            this.newNameField.setVisible(this.targetChooser.getSelectedIndex() == 0);
            int idx = this.targetChooser.getSelectedIndex();
            if (idx > 0) {
                // clone lines from the selected existing AnnotationData into annotationData
                AnnotationData selected = this.existingData.get(idx - 1);
                // keep the raw values of annotationData but adopt the line metadata
                for (int c = 0; c < Math.min(this.annotationData.getLineCount(), selected.getLineCount()); c++) {
                    AnnotationLine src = selected.getLine(c);
                    AnnotationLine dst = this.annotationData.getLine(c);
                    dst.setName(src.getName());
                    dst.setType(src.getType());
                    dst.setUnit(src.getUnit());
                }
            }
            this.rebuildConfigPanel();
            this.rebuildPreviewTable();
            this.revalidate();
            this.repaint();
        });

        // --- Preview table ---
        this.tableModel = new DefaultTableModel(this.buildTableColumnNames(), 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        this.fillPreviewRows();

        WebTable previewTable = new WebTable(this.tableModel);
        previewTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        WebScrollPane scrollPane = new WebScrollPane(previewTable);
        scrollPane.setPreferredSize(new Dimension(500, 226));

        WebPanel tablePanel = new WebPanel(new BorderLayout());
        tablePanel.setPadding(Settings.paddingInDialogs);
        WebLabel previewLabel = new WebLabel("Preview:");
        previewLabel.setFontSizeAndStyle(12, Font.BOLD);
        previewLabel.setPadding(0, 0, 4, 0);
        tablePanel.add(previewLabel, BorderLayout.NORTH);
        tablePanel.add(scrollPane, BorderLayout.CENTER);
        Tools.addComponentToGridBagLayout(this, mainLayout, tablePanel, 0, row++, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.LINE_START);

        // --- OK / Cancel ---
        GridBagLayout okLayout = new GridBagLayout();
        WebPanel okPanel = new WebPanel(okLayout);
        okPanel.setPadding(Settings.paddingInDialogs);

        WebButton okButton = new WebButton("Confirm", ae -> { this.ok = true; this.dispose(); });
        okButton.setHorizontalAlignment(WebButton.CENTER);
        okButton.setPadding(Settings.paddingInDialogs * 2, Settings.paddingInDialogs, Settings.paddingInDialogs * 2, Settings.paddingInDialogs);
        Tools.addComponentToGridBagLayout(okPanel, okLayout, okButton, 0, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.LINE_START);

        WebButton cancelButton = new WebButton("Cancel", ae -> { this.ok = false; this.dispose(); });
        cancelButton.setHorizontalAlignment(WebButton.CENTER);
        cancelButton.setPadding(Settings.paddingInDialogs * 2, Settings.paddingInDialogs, Settings.paddingInDialogs * 2, Settings.paddingInDialogs);
        Tools.addComponentToGridBagLayout(okPanel, okLayout, cancelButton, 1, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.LINE_START);

        Tools.addComponentToGridBagLayout(this, mainLayout, okPanel, 0, row, 1, 1, 1.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.LINE_START);
    }

    /**
     * (Re)builds the column-configuration sub-panel in-place.
     * Each column gets a rename field, type chooser, unit chooser, and a delete button.
     */
    private void rebuildConfigPanel() {
        this.configPanel.removeAll();
        GridBagLayout layout = (GridBagLayout) this.configPanel.getLayout();

        int colCount = this.annotationData.getLineCount();
        this.typeChoosers = new WebComboBox[colCount];
        this.unitChoosers = new WebComboBox[colCount];
        this.nameFields   = new WebTextField[colCount];

        for (int col = 0; col < colCount; col++) {
            final int colIdx = col;
            AnnotationLine line = this.annotationData.getLine(col);

            WebPanel colPanel = new WebPanel(new GridBagLayout());
            colPanel.setPadding(0, col > 0 ? Settings.paddingInDialogs : 0, 0, 0);
            GridBagLayout colLayout = (GridBagLayout) colPanel.getLayout();

            // editable name field
            this.nameFields[col] = new WebTextField(line.getName());
            this.nameFields[col].setHorizontalAlignment(WebTextField.CENTER);
            this.nameFields[col].setToolTipText("Column name");
            this.nameFields[col].setFontStyle(Font.BOLD);
            Tools.addComponentToGridBagLayout(colPanel, colLayout, this.nameFields[col], 0, 0, 1, 1, 1.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.CENTER);

            // type chooser
            this.typeChoosers[col] = new WebComboBox(TYPES);
            this.typeChoosers[col].setSelectedItem(line.getType());
            Tools.addComponentToGridBagLayout(colPanel, colLayout, this.typeChoosers[col], 0, 1, 1, 1, 1.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.CENTER);

            // unit chooser
            AnnotationLine.Unit[] validUnits = AnnotationLine.getValidUnits(line.getType());
            this.unitChoosers[col] = new WebComboBox(validUnits);
            if (line.getUnit() != null) this.unitChoosers[col].setSelectedItem(line.getUnit());
            this.unitChoosers[col].setVisible(validUnits.length > 0);
            Tools.addComponentToGridBagLayout(colPanel, colLayout, this.unitChoosers[col], 0, 2, 1, 1, 1.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.CENTER);

            // when the type changes, set the unit chooser with the valid units
            final WebComboBox unitChooser = this.unitChoosers[col];
            this.typeChoosers[col].addActionListener(ae -> {
                AnnotationLine.Type selectedType = (AnnotationLine.Type) this.typeChoosers[colIdx].getSelectedItem();
                AnnotationLine.Unit[] newUnits = AnnotationLine.getValidUnits(selectedType);
                unitChooser.removeAllItems();
                for (AnnotationLine.Unit u : newUnits) unitChooser.addItem(u);
                unitChooser.setVisible(newUnits.length > 0);
                if (newUnits.length > 0) {
                    AnnotationLine.Unit def = AnnotationLine.getDefaultUnit(selectedType);
                    unitChooser.setSelectedItem(def != null ? def : newUnits[0]);
                }
            });

            // delete button
            WebButton deleteBtn = new WebButton("remove");
            deleteBtn.setToolTipText("Remove this column");
            deleteBtn.setFontStyle(Font.BOLD);
            deleteBtn.setPadding(2, 4, 2, 4);
            deleteBtn.addActionListener(ae -> {
                this.applyNamesTypesUnits();            // persist current UI state before removing
                this.annotationData.removeLine(colIdx);
                this.rebuildConfigPanel();
                this.rebuildPreviewTable();
                this.configPanel.revalidate();
                this.configPanel.repaint();
            });
            Tools.addComponentToGridBagLayout(colPanel, colLayout, deleteBtn, 0, 3, 1, 1, 1.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.CENTER);

            Tools.addComponentToGridBagLayout(this.configPanel, layout, colPanel, col, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.PAGE_START);
        }
    }

    /**
     * Write current UI name/type/unit values back into the AnnotationData lines.
     * Called before structural changes (delete) so no edits are lost.
     */
    private void applyNamesTypesUnits() {
        int colCount = Math.min(this.annotationData.getLineCount(), this.nameFields.length);
        for (int c = 0; c < colCount; c++) {
            String n = this.nameFields[c].getText().trim();
            this.annotationData.getLine(c).setName(n.isEmpty() ? ("Column " + (c + 1)) : n);
            AnnotationLine.Type selectedType = (AnnotationLine.Type) this.typeChoosers[c].getSelectedItem();
            this.annotationData.getLine(c).setType(selectedType);
            // only set unit when the chooser is visible (TEXT type has no units)
            if (this.unitChoosers[c].isVisible()) {
                AnnotationLine.Unit selectedUnit = (AnnotationLine.Unit) this.unitChoosers[c].getSelectedItem();
                if (selectedUnit != null)
                    this.annotationData.getLine(c).setUnit(selectedUnit);
            }
        }
    }

    /** Build the column-name array for the preview table from the current annotationData lines. */
    private String[] buildTableColumnNames() {
        int colCount = this.annotationData.getLineCount();
        String[] names = new String[colCount];
        for (int c = 0; c < colCount; c++)
            names[c] = this.annotationData.getLine(c).getName();
        return names;
    }

    /** Fill (or refill) the preview table model from the current annotationData. */
    private void fillPreviewRows() {
        this.tableModel.setRowCount(0);
        int colCount = this.annotationData.getLineCount();
        // update column identifiers
        this.tableModel.setColumnCount(0);
        for (String name : this.buildTableColumnNames())
            this.tableModel.addColumn(name);

        int previewCount = Math.min(10, this.annotationData.getRowCount());
        for (int r = 0; r < previewCount; r++) {
            Object[] rowData = new Object[colCount];
            for (int c = 0; c < colCount; c++)
                rowData[c] = this.annotationData.getLine(c).getValue(r);
            this.tableModel.addRow(rowData);
        }
        if (this.annotationData.getRowCount() > previewCount) {
            Object[] ellipsis = new Object[colCount];
            ellipsis[0] = "... (" + this.annotationData.getRowCount() + " rows total)";
            for (int c = 1; c < colCount; c++) ellipsis[c] = "...";
            this.tableModel.addRow(ellipsis);
        }
    }

    /** Rebuild the preview table (column headers + rows) after a structural change. */
    private void rebuildPreviewTable() {
        if (this.tableModel != null)
            this.fillPreviewRows();
    }

    /**
     * Initialize keyboard shortcuts.
     */
    private void initKeyboardShortcuts() {
        InputMap inputMap = this.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "Cancel");
        this.getRootPane().getActionMap().put("Cancel", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { ok = false; dispose(); }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "OK");
        this.getRootPane().getActionMap().put("OK", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { ok = true; dispose(); }
        });
    }

    /**
     * Show the dialog
     * @return true if the user confirmed with Import
     */
    public boolean showDialog() {
        this.setVisible(true);
        return this.ok;
    }

    /**
     * Returns the existing AnnotationData to replace, or null if a new one should be created.
     * @return target or null
     */
    public AnnotationData getTargetAnnotationData() {
        int idx = this.targetChooser.getSelectedIndex();
        return (idx == 0) ? null : this.existingData.get(idx - 1);
    }

    /**
     * Returns the name for a new annotation dataset.
     * @return name string
     */
    public String getNewAnnotationName() {
        String name = this.newNameField.getText().trim();
        return name.isEmpty() ? "Annotation" : name;
    }

    /**
     * Build the final AnnotationData from the parsed CSV and the user's column configuration.
     * Column type and unit are taken from the combo boxes; rows are unchanged.
     * If replacing an existing object, the rows of that object are replaced.
     * @return the configured AnnotationData, or null if no rows could be parsed
     */
    public AnnotationData buildAnnotationData() {
        if (this.annotationData.isEmpty())
            return null;

        // apply current UI state (names, types, units) back onto the AnnotationData lines
        this.applyNamesTypesUnits();

        // determine name
        AnnotationData target = this.getTargetAnnotationData();
        String name = (target != null) ? target.getName() : this.getNewAnnotationName();
        this.annotationData.setName(name);

        return this.annotationData;
    }
}

