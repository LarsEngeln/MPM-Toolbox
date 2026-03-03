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
import mpmToolbox.gui.audio.AnnotationPanel;
import mpmToolbox.supplementary.Tools;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * A modal dialog for configuring CSV annotation import.
 * Displays a preview of the CSV data (header + first ~10 rows)
 * and lets the user select the data type and unit for each column.
 * Also lets the user select an existing AnnotationData object to replace, or create a new one.
 * @author Lars Engeln
 */
public class CsvImportDialog extends WebDialog<CsvImportDialog> {
    private boolean ok = false;

    private final ArrayList<String[]> allRows = new ArrayList<>();      // all parsed rows (data only, no header)
    private String[] headerRow = null;                                  // the detected header row, or null
    private int columnCount = 0;
    private String delimiter;                                           // the detected delimiter

    private WebComboBox[] typeChoosers;                                 // one per column: time, curve, marks
    private WebComboBox[] unitChoosers;                                 // one per column: seconds, milliseconds, hz, %

    private WebComboBox targetChooser;                                  // choose existing AnnotationData or "New..."
    private WebTextField newNameField;                                  // name input for a new AnnotationData object
    private final ArrayList<AnnotationData> existingData;               // existing AnnotationData objects

    private static final String NEW_ENTRY = "<New annotation...>";

    /** the available data types for a column */
    public static final String TYPE_TIME   = "time";
    public static final String TYPE_CURVE  = "curve";
    public static final String TYPE_MARKS  = "marks";
    public static final String[] TYPES = { TYPE_TIME, TYPE_CURVE, TYPE_MARKS };

    /** the available units */
    public static final String UNIT_SECONDS      = "seconds";
    public static final String UNIT_MILLISECONDS = "milliseconds";
    public static final String UNIT_HZ           = "hz";
    public static final String UNIT_PERCENT      = "%";
    public static final String[] UNITS = { UNIT_SECONDS, UNIT_MILLISECONDS, UNIT_HZ, UNIT_PERCENT };

    /**
     * constructor
     * @param file the CSV file to import
     * @param existingData the list of already existing AnnotationData objects to replace or add to
     */
    public CsvImportDialog(File file, ArrayList<AnnotationData> existingData) {
        super();
        this.existingData = (existingData != null) ? existingData : new ArrayList<>();
        this.setTitle("Import CSV: " + file.getName());
        this.setIconImages(Settings.getIcons(null));
        this.setModal(true);
        this.setResizable(true);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        this.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                ok = false;
                dispose();
            }
        });

        this.initKeyboardShortcuts();
        this.parseFile(file);
        this.buildGui(file.getName());

        this.pack();
        this.setMinimumSize(new Dimension(550, 400));
        this.setLocationRelativeTo(null);
    }

    /**
     * Parse the CSV file: detect delimiter, detect header, read all rows.
     * @param file
     */
    private void parseFile(File file) {
        ArrayList<String> rawLines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#"))
                    rawLines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        if (rawLines.isEmpty())
            return;

        // detect delimiter: try tab, semicolon, comma
        this.delimiter = detectDelimiter(rawLines.get(0));

        // parse all lines
        ArrayList<String[]> parsed = new ArrayList<>();
        for (String raw : rawLines)
            parsed.add(raw.split(this.delimiter, -1));

        // determine column count from first row
        this.columnCount = parsed.get(0).length;

        // detect header: if first row contains non-numeric values, treat as header
        if (isHeaderRow(parsed.get(0))) {
            this.headerRow = parsed.get(0);
            for (int i = 0; i < this.headerRow.length; i++)
                this.headerRow[i] = this.headerRow[i].trim();
            parsed.remove(0);
        }

        // trim all values
        for (String[] row : parsed) {
            for (int i = 0; i < row.length; i++)
                row[i] = row[i].trim();
        }

        this.allRows.addAll(parsed);
    }

    /**
     * Detect the delimiter used in the CSV.
     * @param firstLine
     * @return the delimiter string
     */
    private static String detectDelimiter(String firstLine) {
        if (firstLine.contains("\t")) return "\t";
        if (firstLine.contains(";"))  return ";";
        return ",";
    }

    /**
     * Check whether a row is likely a header (contains at least one non-numeric field).
     * @param row
     * @return true if it looks like a header
     */
    private static boolean isHeaderRow(String[] row) {
        for (String cell : row) {
            try {
                Double.parseDouble(cell.trim());
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build the dialog GUI.
     * @param defaultName the default name for a new annotation object (usually the filename)
     */
    private void buildGui(String defaultName) {
        GridBagLayout mainLayout = new GridBagLayout();
        this.setLayout(mainLayout);

        int row = 0;

        // --- Target object selection ---
        WebPanel targetPanel = new WebPanel(new GridBagLayout());
        targetPanel.setPadding(Settings.paddingInDialogs);
        GridBagLayout targetLayout = (GridBagLayout) targetPanel.getLayout();

        WebLabel targetLabel = new WebLabel("Import as:");
        targetLabel.setFontSizeAndStyle(12, Font.BOLD);
        targetLabel.setPadding(2, 4, 2, 8);
        Tools.addComponentToGridBagLayout(targetPanel, targetLayout, targetLabel, 0, 0, 1, 1, 0.0, 1.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.LINE_START);

        // build chooser items: all existing names + "New..."
        String[] targetItems = new String[this.existingData.size() + 1];
        targetItems[0] = NEW_ENTRY;
        for (int i = 0; i < this.existingData.size(); i++)
            targetItems[i + 1] = this.existingData.get(i).getName();

        this.targetChooser = new WebComboBox(targetItems);
        this.targetChooser.setSelectedIndex(0);
        Tools.addComponentToGridBagLayout(targetPanel, targetLayout, this.targetChooser, 1, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.CENTER);

        // name field for new annotation (only visible when "New..." is selected)
        this.newNameField = new WebTextField(defaultName);
        this.newNameField.setToolTipText("Name for the new annotation dataset");
        Tools.addComponentToGridBagLayout(targetPanel, targetLayout, this.newNameField, 2, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.CENTER);

        // show/hide name field based on selection
        this.targetChooser.addActionListener(e -> this.newNameField.setVisible(this.targetChooser.getSelectedIndex() == 0));

        Tools.addComponentToGridBagLayout(this, mainLayout, targetPanel, 0, row++, 1, 1, 1.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.LINE_START);

        // --- Column configuration panel (type + unit choosers) ---
        WebPanel configPanel = new WebPanel(new GridBagLayout());
        configPanel.setPadding(Settings.paddingInDialogs);
        GridBagLayout configLayout = (GridBagLayout) configPanel.getLayout();

        this.typeChoosers = new WebComboBox[this.columnCount];
        this.unitChoosers = new WebComboBox[this.columnCount];

        // each CSV column gets its own vertical sub-panel (name, type chooser, unit chooser)
        // all sub-panels are placed side by side
        for (int col = 0; col < this.columnCount; col++) {
            WebPanel colPanel = new WebPanel(new GridBagLayout());
            colPanel.setPadding(0, col > 0 ? Settings.paddingInDialogs : 0, 0, 0);
            GridBagLayout colLayout = (GridBagLayout) colPanel.getLayout();

            // column name label
            String name = (this.headerRow != null && col < this.headerRow.length) ? this.headerRow[col] : ("Column " + (col + 1));
            WebLabel nameLabel = new WebLabel(name);
            nameLabel.setFontSizeAndStyle(12, Font.BOLD);
            nameLabel.setHorizontalAlignment(WebLabel.CENTER);
            nameLabel.setPadding(2, 4, 4, 4);
            Tools.addComponentToGridBagLayout(colPanel, colLayout, nameLabel, 0, 0, 1, 1, 1.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.CENTER);

            // data type chooser
            this.typeChoosers[col] = new WebComboBox(TYPES);
            Tools.addComponentToGridBagLayout(colPanel, colLayout, this.typeChoosers[col], 0, 1, 1, 1, 1.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.CENTER);

            // unit chooser
            this.unitChoosers[col] = new WebComboBox(UNITS);
            Tools.addComponentToGridBagLayout(colPanel, colLayout, this.unitChoosers[col], 0, 2, 1, 1, 1.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.CENTER);

            Tools.addComponentToGridBagLayout(configPanel, configLayout, colPanel, col, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.PAGE_START);
        }

        this.applyDefaults();
        Tools.addComponentToGridBagLayout(this, mainLayout, configPanel, 0, row++, 1, 1, 1.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.LINE_START);

        // --- Preview table ---
        int previewCount = Math.min(10, this.allRows.size());
        String[] tableColumnNames = new String[this.columnCount];
        for (int c = 0; c < this.columnCount; c++)
            tableColumnNames[c] = (this.headerRow != null && c < this.headerRow.length) ? this.headerRow[c] : ("Column " + (c + 1));

        DefaultTableModel tableModel = new DefaultTableModel(tableColumnNames, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        for (int r2 = 0; r2 < previewCount; r2++) {
            String[] dataRow = this.allRows.get(r2);
            String[] paddedRow = new String[this.columnCount];
            for (int c = 0; c < this.columnCount; c++)
                paddedRow[c] = (c < dataRow.length) ? dataRow[c] : "";
            tableModel.addRow(paddedRow);
        }

        if (this.allRows.size() > previewCount) {
            String[] ellipsis = new String[this.columnCount];
            ellipsis[0] = "... (" + this.allRows.size() + " rows total)";
            for (int c = 1; c < this.columnCount; c++) ellipsis[c] = "...";
            tableModel.addRow(ellipsis);
        }

        WebTable previewTable = new WebTable(tableModel);
        previewTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        WebScrollPane scrollPane = new WebScrollPane(previewTable);
        scrollPane.setPreferredSize(new Dimension(500, 180));

        WebPanel tablePanel = new WebPanel(new BorderLayout());
        tablePanel.setPadding(Settings.paddingInDialogs);
        WebLabel previewLabel = new WebLabel("Preview:");
        previewLabel.setFontSizeAndStyle(12, Font.BOLD);
        previewLabel.setPadding(0, 0, 4, 0);
        tablePanel.add(previewLabel, BorderLayout.NORTH);
        tablePanel.add(scrollPane, BorderLayout.CENTER);
        Tools.addComponentToGridBagLayout(this, mainLayout, tablePanel, 0, row++, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.LINE_START);

        // --- OK / Cancel buttons ---
        GridBagLayout okLayout = new GridBagLayout();
        WebPanel okPanel = new WebPanel(okLayout);
        okPanel.setPadding(Settings.paddingInDialogs);

        WebButton okButton = new WebButton("Import", actionEvent -> { this.ok = true; this.dispose(); });
        okButton.setHorizontalAlignment(WebButton.CENTER);
        okButton.setPadding(Settings.paddingInDialogs * 2, Settings.paddingInDialogs, Settings.paddingInDialogs * 2, Settings.paddingInDialogs);
        Tools.addComponentToGridBagLayout(okPanel, okLayout, okButton, 0, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.LINE_START);

        WebButton cancelButton = new WebButton("Cancel", actionEvent -> { this.ok = false; this.dispose(); });
        cancelButton.setHorizontalAlignment(WebButton.CENTER);
        cancelButton.setPadding(Settings.paddingInDialogs * 2, Settings.paddingInDialogs, Settings.paddingInDialogs * 2, Settings.paddingInDialogs);
        Tools.addComponentToGridBagLayout(okPanel, okLayout, cancelButton, 1, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH, GridBagConstraints.LINE_START);

        Tools.addComponentToGridBagLayout(this, mainLayout, okPanel, 0, row, 1, 1, 1.0, 0.0, 0, 0, GridBagConstraints.HORIZONTAL, GridBagConstraints.LINE_START);
    }

    /**
     * Apply smart defaults based on the number of columns.
     */
    private void applyDefaults() {
        if (this.columnCount == 1) {
            this.typeChoosers[0].setSelectedItem(TYPE_MARKS);
            this.unitChoosers[0].setSelectedItem(UNIT_SECONDS);
        } else if (this.columnCount >= 2) {
            this.typeChoosers[0].setSelectedItem(TYPE_TIME);
            this.unitChoosers[0].setSelectedItem(UNIT_SECONDS);
            for (int i = 1; i < this.columnCount; i++) {
                this.typeChoosers[i].setSelectedItem(TYPE_CURVE);
                this.unitChoosers[i].setSelectedItem(UNIT_HZ);
            }
        }
    }

    /**
     * Keyboard shortcuts: ESC to cancel, ENTER to confirm.
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
     * Show the dialog and return whether the user confirmed.
     * @return true if Import was clicked, false if cancelled
     */
    public boolean showDialog() {
        this.setVisible(true);
        return this.ok;
    }

    /**
     * Returns the selected target AnnotationData object to replace, or null if a new one should be created.
     * @return the existing AnnotationData to replace, or null
     */
    public AnnotationData getTargetAnnotationData() {
        int idx = this.targetChooser.getSelectedIndex();
        if (idx == 0)
            return null;                                    // "New..."
        return this.existingData.get(idx - 1);
    }

    /**
     * Returns the name for the new annotation dataset (only relevant when getTargetAnnotationData() == null).
     * @return the name string
     */
    public String getNewAnnotationName() {
        String name = this.newNameField.getText().trim();
        return name.isEmpty() ? "Annotation" : name;
    }

    /**
     * Get the number of columns detected.
     * @return column count
     */
    public int getColumnCount() {
        return this.columnCount;
    }

    /**
     * Get the data type selected for a given column.
     * @param column the column index
     * @return "time", "curve", or "marks"
     */
    public String getColumnType(int column) {
        return (String) this.typeChoosers[column].getSelectedItem();
    }

    /**
     * Get the unit selected for a given column.
     * @param column the column index
     * @return "seconds", "milliseconds", "hz", or "%"
     */
    public String getColumnUnit(int column) {
        return (String) this.unitChoosers[column].getSelectedItem();
    }

    /**
     * Get all parsed data rows (excluding header).
     * @return all data rows
     */
    public ArrayList<String[]> getAllRows() {
        return this.allRows;
    }

    /**
     * Get the detected header row, or null if none was detected.
     * @return header row or null
     */
    public String[] getHeaderRow() {
        return this.headerRow;
    }

    /**
     * Build an AnnotationData object from the current dialog configuration.
     * Call this after showDialog() returned true.
     * The target name is taken from getNewAnnotationName() when no existing object is selected.
     * @return the built AnnotationData, or null if no valid entries could be parsed
     */
    public AnnotationData buildAnnotationData() {
        // find time column and first curve/marks column
        int timeCol = -1;
        int valueCol = -1;
        String timeUnit = UNIT_SECONDS;
        String valueLabel = "Annotation";

        for (int c = 0; c < this.columnCount; c++) {
            String type = this.getColumnType(c);
            if (type.equals(TYPE_TIME) && timeCol < 0) {
                timeCol = c;
                timeUnit = this.getColumnUnit(c);
            } else if ((type.equals(TYPE_CURVE) || type.equals(TYPE_MARKS)) && valueCol < 0) {
                valueCol = c;
                if (this.headerRow != null && c < this.headerRow.length && !this.headerRow[c].isEmpty())
                    valueLabel = this.headerRow[c];
            }
        }

        ArrayList<AnnotationPanel.AnnotationEntry> entries = new ArrayList<>();

        if (timeCol >= 0 && valueCol >= 0) {
            // two-column mode: explicit time + value
            double timeFactor = timeUnit.equals(UNIT_MILLISECONDS) ? 1.0 : 1000.0;
            for (String[] row : this.allRows) {
                if (timeCol >= row.length || valueCol >= row.length) continue;
                try {
                    double ms = Double.parseDouble(row[timeCol]) * timeFactor;
                    double value = Double.parseDouble(row[valueCol]);
                    entries.add(new AnnotationPanel.AnnotationEntry(ms, value));
                } catch (NumberFormatException e) { /* skip malformed rows */ }
            }
        } else if (valueCol >= 0) {
            // single-column marks: the value IS the timestamp, amplitude = 1
            double factor = this.getColumnUnit(valueCol).equals(UNIT_MILLISECONDS) ? 1.0 : 1000.0;
            for (String[] row : this.allRows) {
                if (valueCol >= row.length) continue;
                try {
                    double ms = Double.parseDouble(row[valueCol]) * factor;
                    entries.add(new AnnotationPanel.AnnotationEntry(ms, 1.0));
                } catch (NumberFormatException e) { /* skip malformed rows */ }
            }
        } else if (timeCol >= 0) {
            // only a time column → treat as marks, amplitude = 1
            double timeFactor = timeUnit.equals(UNIT_MILLISECONDS) ? 1.0 : 1000.0;
            valueLabel = "marks";
            for (String[] row : this.allRows) {
                if (timeCol >= row.length) continue;
                try {
                    double ms = Double.parseDouble(row[timeCol]) * timeFactor;
                    entries.add(new AnnotationPanel.AnnotationEntry(ms, 1.0));
                } catch (NumberFormatException e) { /* skip malformed rows */ }
            }
        }

        if (entries.isEmpty())
            return null;

        // determine the final name: from existing target or from the name field
        AnnotationData target = this.getTargetAnnotationData();
        String name = (target != null) ? target.getName() : this.getNewAnnotationName();
        if (valueLabel.equals("Annotation") || target != null)
            valueLabel = name;

        return new AnnotationData(valueLabel, entries);
    }
}

