package mpmToolbox.gui.audio;

import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Elements;

import java.io.File;
import java.nio.file.Path;
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
    private File    file    = null;                                      // the source CSV file (for project persistence)
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

    /** Get the source CSV file (may be null if not loaded from file). */
    public File getFile()              { return this.file; }
    /** Set the source CSV file reference. */
    public void setFile(File f)        { this.file = f; }

    // -------------------------------------------------------------------------
    // XML serialisation (project persistence)
    // -------------------------------------------------------------------------

    /**
     * Serialise this AnnotationData into an XML {@code <annotation>} element suitable
     * for embedding in the {@code .mpr} project file.
     * Returns null when no source file is known (the annotation cannot be
     * re-loaded on the next project open).
     *
     * @param basePath the directory of the {@code .mpr} file, used to build a relative path
     * @return an XML element, or null
     */
    public Element toXml(Path basePath) {
        if (this.file == null)
            return null;

        Element elt = new Element("annotation");
        Path relativePath = basePath.relativize(this.file.toPath());
        elt.addAttribute(new Attribute("file",    relativePath.toString()));
        elt.addAttribute(new Attribute("name",    this.name));
        elt.addAttribute(new Attribute("visible", Boolean.toString(this.visible)));

        for (int i = 0; i < this.lines.size(); i++) {
            AnnotationLine line   = this.lines.get(i);
            Element        colElt = new Element("column");
            colElt.addAttribute(new Attribute("index", Integer.toString(i)));
            colElt.addAttribute(new Attribute("name",  line.getName()));
            colElt.addAttribute(new Attribute("type",  line.getType().name()));
            if (line.getUnit() != null)
                colElt.addAttribute(new Attribute("unit", line.getUnit().name()));
            elt.appendChild(colElt);
        }
        return elt;
    }

    /**
     * Apply the metadata stored in an XML to an already-parsed annotation object.
     * The actual value data is left untouched.
     *
     * @param data    the target object whose metadata is to be overwritten
     * @param element the {@code <annotation>} XML element from the {@code .mpr} file
     */
    public static void applyXmlSettings(AnnotationData data, Element element) {
        if (data == null || element == null)
            return;

        String nameAttr = element.getAttributeValue("name");
        if (nameAttr != null && !nameAttr.isEmpty())
            data.setName(nameAttr);

        String visibleAttr = element.getAttributeValue("visible");
        if (visibleAttr != null)
            data.setVisible(Boolean.parseBoolean(visibleAttr));

        Elements columns = element.getChildElements("column");
        for (int i = 0; i < columns.size(); i++) {
            Element colElt  = columns.get(i);
            String  idxStr  = colElt.getAttributeValue("index");
            if (idxStr == null) continue;
            int idx;
            try { idx = Integer.parseInt(idxStr); } catch (NumberFormatException e) { continue; }
            if (idx < 0 || idx >= data.getLineCount()) continue;

            AnnotationLine line = data.getLine(idx);

            String colName = colElt.getAttributeValue("name");
            if (colName != null && !colName.isEmpty())
                line.setName(colName);

            String typeStr = colElt.getAttributeValue("type");
            if (typeStr != null) {
                try { line.setType(AnnotationLine.Type.valueOf(typeStr)); }
                catch (IllegalArgumentException ignored) {}
            }

            String unitStr = colElt.getAttributeValue("unit");
            if (unitStr != null) {
                try { line.setUnit(AnnotationLine.Unit.valueOf(unitStr)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
    }

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

    /**
     * Remove the line at the given index.
     * @param index column index to remove
     */
    public void removeLine(int index) {
        if (index >= 0 && index < this.lines.size())
            this.lines.remove(index);
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
