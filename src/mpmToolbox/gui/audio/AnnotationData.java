package mpmToolbox.gui.audio;

import java.util.ArrayList;

/**
 * Holds annotation data, typically loaded from a CSV file.
 * An AnnotationData consists of any number of AnnotationLine objects
 * each carrying its own values, type and unit.
 * @author Lars Engeln
 */
public class AnnotationData {

    private String  name;
    private boolean visible = true;
    private final ArrayList<AnnotationLine> lines = new ArrayList<>();  // one per CSV column

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create an empty AnnotationData with a name.
     * @param name display name
     */
    public AnnotationData(String name) {
        this.name = (name != null && !name.isEmpty()) ? name : "Annotation";
    }

    // -------------------------------------------------------------------------
    // Name / visibility
    // -------------------------------------------------------------------------

    public String  getName()            { return this.name; }
    public void    setName(String name) { this.name = (name != null && !name.isEmpty()) ? name : "Annotation"; }

    public boolean isVisible()           { return this.visible; }
    public void    setVisible(boolean v) { this.visible = v; }

    // -------------------------------------------------------------------------
    // Lines (columns)
    // -------------------------------------------------------------------------

    /**
     * Add a column line. Its index corresponds to its position in the CSV.
     * @param line the AnnotationLine to add
     */
    public void addLine(AnnotationLine line) {
        if (line != null) this.lines.add(line);
    }

    /**
     * Replace the line at the given index.
     * @param index column index
     * @param line  the new AnnotationLine
     */
    public void setLine(int index, AnnotationLine line) {
        if (index >= 0 && index < this.lines.size() && line != null)
            this.lines.set(index, line);
    }

    /** Get the number of columns. */
    public int getLineCount() { return this.lines.size(); }

    /**
     * Get the line at the given index.
     * @param index column index
     * @return the AnnotationLine
     */
    public AnnotationLine getLine(int index) { return this.lines.get(index); }

    /** Get all lines. */
    public ArrayList<AnnotationLine> getLines() { return this.lines; }

    // -------------------------------------------------------------------------
    // Row count (derived from the first line's value count)
    // -------------------------------------------------------------------------

    /**
     * Get the number of data rows (= size of any line; all lines should have equal length).
     * @return row count, or 0 if no lines present
     */
    public int getRowCount() {
        return this.lines.isEmpty() ? 0 : this.lines.get(0).size();
    }

    /** Whether this AnnotationData contains any data. */
    public boolean isEmpty() { return this.getRowCount() == 0; }

    // -------------------------------------------------------------------------
    // Column lookup by type
    // -------------------------------------------------------------------------

    /**
     * Find the index of the first line with the given type, or -1 if none.
     * @param type the type to search for
     * @return line index or -1
     */
    public int getFirstLineIndexOfType(AnnotationLine.Type type) {
        for (int i = 0; i < this.lines.size(); i++)
            if (this.lines.get(i).getType() == type)
                return i;
        return -1;
    }

    /**
     * Return all line indices with the given type.
     * @param type the type to search for
     * @return list of indices
     */
    public ArrayList<Integer> getLineIndicesOfType(AnnotationLine.Type type) {
        ArrayList<Integer> result = new ArrayList<>();
        for (int i = 0; i < this.lines.size(); i++)
            if (this.lines.get(i).getType() == type)
                result.add(i);
        return result;
    }

    @Override
    public String toString() { return this.name; }
}
