package mpmToolbox.gui.mpmEditingTools.editDialogs.ornament;

import com.alee.laf.panel.WebPanel;
import meico.mei.Helper;
import meico.mpm.Mpm;
import mpmToolbox.gui.Settings;
import nu.xom.Attribute;
import nu.xom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A matrix view to edit an ornament's note pool and note order.
 * The left side holds the note pool (sorted by midi.pitch, top = high). It consists of the MSM notes
 * at the ornament's date (not editable) and additional notes (can be added, removed, edited).
 * The right side is a matrix, each column is a step in the note order, each row a pitch.
 * Below the matrix a range slider specifies the repetition region.
 * Everything is custom painted on one canvas.
 * @author Lars Engeln
 */
public class NoteMatrixComponent extends WebPanel {
    public static final Color MSM_COLOR = Settings.scoreNoteColor;        // color of notes that stem from the MSM
    public static final Color EXTRA_COLOR = withAlpha(Settings.foregroundColor, 28);    // color of additionally added notes
    private static final Color REGION_COLOR = Settings.scorePerformanceColor;
    private static final Color FLAG_COLOR = withAlpha(Settings.foregroundColor, 28);
    private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";
    private static final int MIN_COLUMNS = 8;                // the minimal space reserved for the matrix
    private static final int MIN_ROWS = 2;

    private static final int ROW_H = Settings.paddingInDialogs*4;
    private static final int COL_W = Settings.paddingInDialogs*4;
    private static final int FLAG_W = Settings.paddingInDialogs*4 - 4;
    private static final int FLAG_H = Settings.paddingInDialogs*4 - 4;
    private static final int HEADER_H = FLAG_H + Settings.paddingInDialogs*3;
    private static final int COLUMN_ACTION_H = FLAG_H + Math.max(2, Settings.paddingInDialogs / 2);
    private static final int REGION_H = Settings.paddingInDialogs*4;
    private static final int LABEL_W = Math.max(Settings.paddingInDialogs*4*2, 100);    // wide enough for enharmonic names like "a♯ / b♭ 4"
    private static final int BTN_W = Settings.paddingInDialogs*5;
    private static final int GAP = 4;

    /**
     * a row of the matrix
     */
    private static class Row {
        double pitch;
        final String id;
        final Element msmNote;                              // the MSM note or null if this is an additional note
        final Element extraXml;                             // the original ornament note element of an additional note, may be null
        ArrayList<Boolean> hits = new ArrayList<>();        // true if the note is played in the according column

        /**
         * Creates a row for an MSM note or an additional ornament note.
         * @param pitch MIDI pitch represented by this row
         * @param id XML ID used to reference the note
         * @param msmNote source MSM note, or null for an additional note
         * @param extraXml original ornament note, or null for an MSM note
         */
        Row(double pitch, String id, Element msmNote, Element extraXml) {
            this.pitch = pitch;
            this.id = id;
            this.msmNote = msmNote;
            this.extraXml = extraXml;
        }

        /**
         * Checks whether this row represents a note from the MSM.
         * @return true if this is an MSM row
         */
        boolean isMsm() {
            return this.msmNote != null;
        }
    }

    private final TreeMap<Double, Row> rows = new TreeMap<>();    // midi.pitch -> row (with the list of hits)
    private final HashMap<String, Double> idAlias = new HashMap<>();    // note IDs referenced in a note order that have no row of their own (pitch collided with another note)
    private int columns = 0;
    private List<Element> allMsmNotes = new ArrayList<>();  // all MSM notes at the date
    private String principalId = null;                      // xml:id of the one MSM note to be ornamented, null for all
    private boolean defaultColumn = false;                  // true while the matrix holds only the automatically created column (all MSM notes marked) that the user has not touched
    private int regionStart = -1;                           // repetition region, -1 if there is none
    private int regionEnd = -1;
    private boolean matrixEnabled = true;
    private Dimension lastMatrixSize = null;                // matrix size at the last window adjustment
    private Consumer<Double> notePlayer = null;

    private final Canvas canvas = new Canvas();
    private int repetitions = 1;                            // set by the surrounding GUI, -1 means as often as fits

    /**
     * Creates the note matrix and installs its custom-painted canvas.
     */
    public NoteMatrixComponent() {
        super(new BorderLayout());
        int pad = Settings.paddingInDialogs;
        this.setBorder(BorderFactory.createEmptyBorder(pad, pad * 2, pad * 2, pad * 2));

        this.add(this.canvas, BorderLayout.CENTER);
    }

    /**
     * Sets the repetition count displayed in the region and returned by {@link #getRepetitions()}.
     * @param repetitions -1 for automatic repetition, otherwise the requested count
     */
    public void setRepetitions(int repetitions) {
        this.repetitions = Math.max(-1, Math.min(999, repetitions));
        this.canvas.repaint();
    }

    /**
     * Sets the callback used to play a note when its label or hit is activated.
     * @param notePlayer callback receiving a MIDI pitch, or null to disable playback
     */
    public void setNotePlayer(Consumer<Double> notePlayer) {
        this.notePlayer = notePlayer;
    }

    /**
     * Enables or disables matrix columns, hits, and repetition editing; the note pool remains editable.
     * @param enable true to enable matrix editing, otherwise only the note pool remains editable
     */
    public void setMatrixEnabled(boolean enable) {
        this.matrixEnabled = enable;
        this.canvas.repaint();
    }

    /**
     * Removes all notes, columns, repetition settings, and in-progress editing state.
     */
    public void clear() {
        this.canvas.stopEdit();
        this.rows.clear();
        this.idAlias.clear();
        this.columns = 0;
        this.defaultColumn = false;
        this.regionStart = -1;
        this.regionEnd = -1;
        this.repetitions = 1;
        this.refresh();
    }

    /**
     * Replaces the MSM notes in the pool while retaining additional notes and hits at unchanged pitches.
     * @param msmNotes MSM note elements, preferably with XML IDs
     */
    public void setMsmNotes(List<Element> msmNotes) {
        this.allMsmNotes = new ArrayList<>(msmNotes);
        this.applyMsmNotes();
    }

    /**
     * Rebuilds the MSM rows while preserving additional rows and hits at unchanged pitches.
     */
    private void applyMsmNotes() {
        List<Element> msmNotes = this.getMsmNotesToApply();
        this.canvas.stopEdit();
        HashMap<Double, ArrayList<Boolean>> oldHits = this.removeMsmRows();
        this.idAlias.clear();

        for (Element n : msmNotes) {
            this.addMsmRow(n, oldHits);
        }
        this.rebuildDefaultColumn();
        this.normalizeHits();
        this.refresh();
    }

    /**
     * Selects the principal MSM note when it exists; otherwise uses all notes at the ornament date.
     * @return MSM notes to represent in the note pool
     */
    private List<Element> getMsmNotesToApply() {
        // No principal filter means every MSM note is available.
        if (this.principalId == null)
            return this.allMsmNotes;
        for (Element note : this.allMsmNotes)
            // Keep only the MSM note whose ID matches the principal selection.
            if (this.principalId.equals(xmlId(note)))
                return java.util.Collections.singletonList(note);
        return this.allMsmNotes;
    }

    /**
     * Saves hit data by pitch and removes old MSM rows before rebuilding them.
     * @return previous hit lists indexed by pitch
     */
    private HashMap<Double, ArrayList<Boolean>> removeMsmRows() {
        HashMap<Double, ArrayList<Boolean>> oldHits = new HashMap<>();
        for (Row row : new ArrayList<>(this.rows.values())) {
            oldHits.put(row.pitch, row.hits);
            // Remove only MSM rows; additional ornament rows are retained.
            if (row.isMsm())
                this.rows.remove(row.pitch);
        }
        return oldHits;
    }

    /**
     * Adds one valid MSM note, resolving duplicate pitches and retaining matching hits.
     * @param note MSM note element to add
     * @param oldHits hit lists from the previous matrix, indexed by pitch
     */
    private void addMsmRow(Element note, HashMap<Double, ArrayList<Boolean>> oldHits) {
        Double pitch = parsePitch(note);
        // Skip notes whose MIDI pitch could not be parsed.
        if (pitch == null)
            return;

        String id = xmlId(note);
        // Generate an ID only when the source note has none.
        if (id == null)
            id = newId();
        Row existing = this.rows.get(pitch);
        // Alias duplicate-pitch MSM IDs to the row already representing that pitch.
        if ((existing != null) && existing.isMsm()) {
            this.idAlias.put(id, pitch);
            return;
        }
        // Let the MSM row replace an additional row at the same pitch.
        if (existing != null)
            this.idAlias.put(existing.id, pitch);

        Row row = new Row(pitch, id, note, null);
        ArrayList<Boolean> hits = oldHits.get(pitch);
        // Restore hits only when a row at this pitch existed before rebuilding.
        if (hits != null)
            row.hits = new ArrayList<>(hits);
        this.rows.put(pitch, row);
    }

    /**
     * Parses a note's MIDI pitch attribute.
     * @param note note element containing midi.pitch
     * @return parsed pitch, or null if the attribute is missing or invalid
     */
    private static Double parsePitch(Element note) {
        try {
            return Double.parseDouble(Helper.getAttributeValue("midi.pitch", note));
        } catch (NumberFormatException | NullPointerException ignored) {
            return null;
        }
    }

    /**
     * Restricts the default column to one MSM note so only that note is ornamented by default.
     * @param id XML ID of the principal MSM note (with or without leading '#'); null or empty marks all MSM notes
     */
    public void setPrincipalNote(String id) {
        // Normalize the principal ID only when a value was supplied.
        if (id != null) {
            id = id.trim();
            // Strip the optional reference marker before storing the ID.
            if (id.startsWith("#"))
                id = id.substring(1);
        }
        String newId = ((id == null) || id.isEmpty()) ? null : id;
        // Avoid rebuilding the note pool when the principal selection is unchanged.
        if (java.util.Objects.equals(newId, this.principalId))
            return;
        this.principalId = newId;
        this.applyMsmNotes();
    }

    /**
     * By default there is one column in which the MSM notes are marked (only the principal note if one is specified).
     * It is rebuilt as long as the user has not edited the matrix.
     * @return true if the column has been (re)built
     */
    private boolean rebuildDefaultColumn() {
        // Preserve a user-edited matrix instead of recreating its default column.
        if ((this.columns != 0) && !this.defaultColumn)
            return false;
        Double principalPitch = (this.principalId == null) ? null : this.findPitch(this.principalId);
        this.columns = 1;
        this.defaultColumn = true;
        this.regionStart = this.regionEnd = -1;
        // A pitch lookup is exact here because the row map is keyed by the same parsed MIDI values.
        for (Row row : this.rows.values()) {
            row.hits = new ArrayList<>();
            row.hits.add(row.isMsm() && ((principalPitch == null) || (row.pitch == principalPitch)));
        }
        return true;
    }

    /**
     * Replaces the additional note rows with the supplied ornament notes.
     * @param ornamentNotes note elements containing midi.pitch and optionally xml:id
     */
    public void setExtraNotes(List<Element> ornamentNotes) {
        this.canvas.stopEdit();
        for (Row r : new ArrayList<>(this.rows.values()))
            // Remove only additional rows before replacing the supplied notes.
            if (!r.isMsm())
                this.rows.remove(r.pitch);

        for (Element n : ornamentNotes) {
            Double pitch = parsePitch(n);
            // Ignore an additional note with an invalid or missing pitch.
            if (pitch == null)
                continue;
            String id = xmlId(n);
            // Generate an ID only when the ornament note has none.
            if (id == null)
                id = newId();
            // Alias notes that collide with an existing pitch instead of creating another row.
            if (this.rows.containsKey(pitch)) {
                this.idAlias.put(id, pitch);
                continue;
            }
            this.rows.put(pitch, new Row(pitch, id, null, n));
        }
        this.normalizeHits();
        this.refresh();
    }

    /**
     * Replaces the matrix contents from note-order tokens and repetition settings.
     * @param noteOrder note IDs and chord/repetition delimiters
     * @param repetitions repetition count, or -1 for automatic repetition
     */
    public void setNoteOrder(List<String> noteOrder, int repetitions) {
        NoteOrder parsedOrder = this.parseNoteOrder(noteOrder);
        this.columns = parsedOrder.columns.size();
        this.defaultColumn = false;
        for (Row row : this.rows.values()) {
            row.hits = new ArrayList<>();
            for (List<Double> column : parsedOrder.columns)
                row.hits.add(column.contains(row.pitch));
        }
        // Accept the repetition region only when its bounds identify existing columns.
        if ((parsedOrder.regionStart >= 0) && (parsedOrder.regionStart <= parsedOrder.regionEnd)
                && (parsedOrder.regionEnd < this.columns)) {
            this.regionStart = parsedOrder.regionStart;
            this.regionEnd = parsedOrder.regionEnd;
        } else {
            this.regionStart = this.regionEnd = -1;
        }
        this.repetitions = Math.max(-1, Math.min(999, repetitions));
        this.refresh();
    }

    /**
     * Converts note-order tokens to pitch columns and repetition-region bounds.
     * @param noteOrder note IDs and chord/repetition delimiters
     * @return parsed columns and repetition bounds
     */
    private NoteOrder parseNoteOrder(List<String> noteOrder) {
        NoteOrder result = new NoteOrder();
        ArrayList<Double> chord = null;
        // Convert delimiters into column groups while tracking the first loop start and closing marker.
        for (String token : noteOrder) {
            switch (token) {
                case "[":
                    chord = new ArrayList<>();
                    break;
                case "]":
                    // Add a chord column only when the closing marker follows at least one note.
                    if ((chord != null) && !chord.isEmpty())
                        result.columns.add(chord);
                    chord = null;
                    break;
                case "|:":
                    // Use the first repetition marker as the region start.
                    if (result.regionStart < 0)
                        result.regionStart = result.columns.size();
                    break;
                case ":|:":
                case ":|":
                    // A closing marker without an opening marker makes the region start at column zero.
                    if (result.regionStart < 0)
                        result.regionStart = 0;
                    // Record only the first closing marker as the region end.
                    if (result.regionEnd < 0)
                        result.regionEnd = result.columns.size() - 1;
                    break;
                default:
                    this.addOrderNote(token, chord, result);
            }
        }
        // Close an opened region at the final parsed column when no end marker appeared.
        if ((result.regionStart >= 0) && (result.regionEnd < 0))
            result.regionEnd = result.columns.size() - 1;
        return result;
    }

    /**
     * Adds a note token to the open chord or starts a single-note column.
     * @param token note ID, optionally prefixed by '#'
     * @param chord chord currently being parsed, or null outside a chord
     * @param order parsed note-order data to update
     */
    private void addOrderNote(String token, ArrayList<Double> chord, NoteOrder order) {
        String id = token.startsWith("#") ? token.substring(1) : token;
        Double pitch = this.findPitch(id);
        // Ignore order tokens that do not resolve to a known note ID.
        if (pitch == null)
            return;
        // Append the note to the open chord instead of creating a single-note column.
        if (chord != null) {
            chord.add(pitch);
            return;
        }
        ArrayList<Double> singleNoteColumn = new ArrayList<>();
        singleNoteColumn.add(pitch);
        order.columns.add(singleNoteColumn);
    }

    /**
     * Mutable result assembled while reading note-order tokens.
     */
    private static class NoteOrder {
        final ArrayList<List<Double>> columns = new ArrayList<>();
        int regionStart = -1;
        int regionEnd = -1;
    }

    /**
     * Serializes matrix hits and the repetition region into note-order tokens.
     * @return note-order tokens, e.g. "#n1 |: #n2 :|"
     */
    public ArrayList<String> getNoteOrder() {
        ArrayList<String> order = new ArrayList<>();
        boolean hasRegion = this.hasRepetitionRegion();
        boolean opened = false;
        boolean closed = false;
        for (int c = 0; c < this.columns; ++c) {
            ArrayList<String> ids = new ArrayList<>();
            for (Row row : this.rows.values())
                // Serialize only notes that are active in this matrix column.
                if (row.hits.get(c))
                    ids.add("#" + row.id);
            // Skip empty columns so the order contains no silent steps.
            if (ids.isEmpty())
                continue;
            // Open the loop when serialization reaches its selected start.
            if (hasRegion && !opened && (c >= this.regionStart) && (c <= this.regionEnd)) {
                order.add("|:");
                opened = true;
            }
            // Close the loop before serializing columns beyond its selected end.
            if (opened && !closed && (c > this.regionEnd)) {
                order.add(":|");
                closed = true;
            }
            // Serialize a lone note directly, without chord delimiters.
            if (ids.size() == 1) {
                order.add(ids.get(0));
            } else {
                order.add("[");
                order.addAll(ids);
                order.add("]");
            }
        }
        // Close a repetition region that extends through the final column.
        if (opened && !closed)
            order.add(":|");
        return order;
    }

    /**
     * Gets the repetition count represented by the matrix.
     * @return repetition count, or 0 when no repetition region is set
     */
    public int getRepetitions() {
        return this.hasRepetitionRegion() ? this.repetitions : 0;
    }

    /**
     * Checks whether a valid repetition region is set.
     * @return true if the region bounds identify existing columns
     */
    public boolean hasRepetitionRegion() {
        return (this.regionStart >= 0) && (this.regionEnd >= this.regionStart) && (this.regionEnd < this.columns);
    }

    /**
     * Creates MPM note elements for additional notes and any used MSM notes.
     * @param onlyUsed if true, includes only notes referenced by matrix hits
     * @return new MPM note elements needed by the ornament
     */
    public ArrayList<Element> getExtraNotes(boolean onlyUsed) {
        ArrayList<Element> notes = new ArrayList<>();
        for (Row row : this.rows.values()) {
            // Omit unused rows when the caller requests only referenced notes.
            if (onlyUsed && !row.hits.contains(true))
                continue;
            // MSM rows need a minimal child note only when they are referenced by the order.
            if (row.isMsm()) {
                // The renderer resolves every note-order ID among the ornament's children.
                // Include the MSM reference element only for the used-notes export.
                if (onlyUsed)
                    notes.add(this.createMsmNoteCopy(row));
            } else {
                notes.add(this.createExtraNote(row));
            }
        }
        return notes;
    }

    /**
     * Creates the minimal ornament note element required to reference an MSM note in the order.
     * @param row MSM row to copy
     * @return a detached MPM note element carrying its pitch and XML ID
     */
    private Element createMsmNoteCopy(Row row) {
        Element note = new Element("note", Mpm.MPM_NAMESPACE);
        note.addAttribute(new Attribute("midi.pitch", pitchString(row.pitch)));
        note.addAttribute(new Attribute("intm", "0.0hs"));
        note.addAttribute(new Attribute("xml:id", XML_NS, row.id));
        return note;
    }

    /**
     * Copies an additional note's attributes and updates its pitch and XML ID.
     * @param row additional note row to serialize
     * @return a detached MPM note element
     */
    private Element createExtraNote(Row row) {
        Element note = new Element("note", Mpm.MPM_NAMESPACE);
        // Copy source ornament attributes when an original element is available.
        if (row.extraXml != null) {
            for (int i = 0; i < row.extraXml.getAttributeCount(); ++i)
                note.addAttribute((Attribute) row.extraXml.getAttribute(i).copy());
        }
        Attribute pitchAttribute = note.getAttribute("midi.pitch");
        // Remove the old pitch before writing the row?s current pitch.
        if (pitchAttribute != null)
            note.removeAttribute(pitchAttribute);
        Attribute idAttribute = note.getAttribute("id", XML_NS);
        // Remove the old XML ID before writing the row?s current ID.
        if (idAttribute != null)
            note.removeAttribute(idAttribute);
        note.addAttribute(new Attribute("midi.pitch", pitchString(row.pitch)));
        note.addAttribute(new Attribute("xml:id", XML_NS, row.id));
        return note;
    }

    /**
     * Reads an element's XML ID.
     * @param element element to inspect
     * @return XML ID value, or null if absent
     */
    private static String xmlId(Element element) {
        Attribute id = element.getAttribute("id", XML_NS);
        return (id == null) ? null : id.getValue();
    }

    /**
     * Generates an XML ID for a newly created ornament note.
     * @return unique ornament-note ID
     */
    private static String newId() {
        return "ornamentNote_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Resolves a note ID to its pitch, including IDs aliased to a colliding row.
     * @param id note ID without a leading '#'
     * @return MIDI pitch, or null when the ID is unknown
     */
    private Double findPitch(String id) {
        for (Row row : this.rows.values())
            // Resolve a direct row ID before consulting pitch-collision aliases.
            if (row.id.equals(id))
                return row.pitch;
        return this.idAlias.get(id);
    }

    /**
     * Keeps each row's hit list aligned with the current number of columns.
     */
    private void normalizeHits() {
        for (Row row : this.rows.values()) {
            while (row.hits.size() < this.columns)
                row.hits.add(false);
            while (row.hits.size() > this.columns)
                row.hits.remove(row.hits.size() - 1);
        }
    }

    /**
     * Recalculates canvas layout, redraws it, and adjusts the containing window if needed.
     */
    private void refresh() {
        this.canvas.updateHover();
        this.canvas.revalidate();
        this.canvas.repaint();
        this.growWindow();
    }

    /**
     * Resizes the surrounding dialog to follow the matrix, capped at 80% of the screen.
     * When that limit is reached, the surrounding scroll pane handles overflow. The dialog is
     * only resized when the matrix dimensions change, preserving manual resizing otherwise.
     */
    private void growWindow() {
        Window w = SwingUtilities.getWindowAncestor(this);
        // Skip window resizing when the component has no displayable ancestor.
        if ((w == null) || !w.isDisplayable())
            return;
        Dimension matrixSize = this.canvas.getPreferredSize();
        // Preserve the current window size when the matrix dimensions did not change.
        if (matrixSize.equals(this.lastMatrixSize))
            return;
        this.lastMatrixSize = matrixSize;
        Dimension need = w.getPreferredSize();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        // Keep a margin around the dialog so the system desktop remains visible.
        int maxW = Math.max(1, (int) Math.floor(screen.width * 0.8));
        int maxH = Math.max(1, (int) Math.floor(screen.height * 0.8));
        int extraW = 0;
        int extraH = 0;
        JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        // Account for scrollbar space when the dialog content exceeds the screen cap.
        if (sp != null) {   // a scrollbar that appears takes space, so enlarge the window by its size
            // Reserve horizontal space when vertical scrolling will be needed.
            if (need.height > maxH) {
                extraW = sp.getVerticalScrollBar().getPreferredSize().width;
                extraH = sp.getHorizontalScrollBar().getPreferredSize().height + 8;     // a bit more height to keep the matrix fully visible
            }
            // Reserve vertical space when the dialog width will require horizontal scrolling.
            if (need.width + extraW > maxW)
                extraH = Math.max(extraH, sp.getHorizontalScrollBar().getPreferredSize().height + 8);
        }
        int nw = Math.min(need.width + extraW, maxW);
        int nh = Math.min(need.height + extraH, maxH);
        // Resize only when the computed dimensions differ from the current window.
        if ((nw != w.getWidth()) || (nh != w.getHeight())) {
            w.setSize(nw, nh);
            w.validate();
        }
    }

    /**
     * Formats a row's pitch as a readable note name when it is an integer MIDI pitch.
     * @param row row whose pitch should be formatted
     * @return note label, or the numeric pitch for fractional values
     */
    private static String label(Row row) {
        // Use the numeric pitch for fractional MIDI values without a standard note name.
        if (row.pitch != Math.rint(row.pitch))
            return Double.toString(row.pitch);
        int midi = (int) row.pitch;
        String[] names = {"c", "c\u266F / d\u266D", "d", "d\u266F / e\u266D", "e", "f", "f\u266F / g\u266D", "g", "g\u266F / a\u266D", "a", "a\u266F / b\u266D", "b"};
        // MIDI note names repeat every 12 semitones; MIDI octave numbering starts at -1.
        return names[midi % 12] + " " + (midi / 12 - 1);
    }

    /**
     * Formats a MIDI pitch without a redundant decimal part.
     * @param pitch pitch to format
     * @return integer or decimal representation of the pitch
     */
    private static String pitchString(double pitch) {
        return (pitch == Math.rint(pitch)) ? Integer.toString((int) pitch) : Double.toString(pitch);
    }

    /**
     * Creates a copy of a color with a clamped alpha value.
     * @param color source color
     * @param alpha requested alpha channel
     * @return color with alpha in the range 0-255
     */
    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    /**
     * Adds an empty additional-note row when its pitch is valid and unused.
     * @param pitch MIDI pitch to add
     */
    private void addRow(double pitch) {
        // Reject out-of-range pitches and duplicate rows before inserting a note.
        if ((pitch < 0) || (pitch > 127) || this.rows.containsKey(pitch))
            return;
        this.rows.put(pitch, new Row(pitch, newId(), null, null));
        this.normalizeHits();
        this.refresh();
    }

    /**
     * Inserts an empty matrix column and adjusts the repetition region around it.
     * @param index column insertion boundary
     */
    private void insertColumn(int index) {
        for (Row row : this.rows.values())
            row.hits.add(index, false);
        this.columns++;
        this.defaultColumn = false;
        // Adjust repetition bounds only when a region is active.
        if (this.regionStart >= 0) {
            // Shift the whole region when insertion occurs at or before its start.
            if (index <= this.regionStart) {
                this.regionStart++;
                this.regionEnd++;
            // Extend the region when insertion falls inside its current bounds.
            } else if (index <= this.regionEnd) {
                this.regionEnd++;
            }
        }
        this.refresh();
    }

    /**
     * Removes a matrix column and contracts or clears the repetition region as needed.
     * @param column index of the column to remove
     */
    private void removeColumn(int column) {
        for (Row row : this.rows.values())
            row.hits.remove(column);
        this.columns--;
        this.defaultColumn = false;
        // Update repetition bounds only when a region is active.
        if (this.regionStart >= 0) {
            // Shift both region bounds when removing a preceding column.
            if (column < this.regionStart) {
                this.regionStart--;
                this.regionEnd--;
            // Shrink the region when the removed column lies within it.
            } else if (column <= this.regionEnd) {
                this.regionEnd--;
            }
            // Clear the region if removal leaves invalid bounds or no columns.
            if ((this.regionEnd < this.regionStart) || (this.columns == 0))
                this.regionStart = this.regionEnd = -1;
        }
        this.refresh();
    }

    ///////////////////////////////////////////////////////////////////////////////////////

    private enum Drag {NONE, CREATE, LEFT, RIGHT, MOVE}

    private enum Pointer {NONE, UP, DOWN, LEFT, RIGHT}

    /**
     * Holds the hit-test results for the current mouse position.
     */
    private static class HoverState {
        int row = -1;
        int column = -1;
        int columnBoundary = -1;
        int columnRemoval = -1;
        int noteInsertIndex = -1;
    }

    /**
     * the custom painted note pool, matrix and region slider
     */
    private class Canvas extends JComponent {
        private int hoverRow = -1;                          // displayed row index under the mouse, -1 if none
        private int hoverCol = -1;
        private int hoverColumnBoundary = -1;
        private int hoverColumnRemoval = -1;
        private int hoverNoteInsertIndex = -1;
        private Point lastPoint = null;
        private Drag drag = Drag.NONE;
        private int dragOffset = 0;
        private JTextField editor = null;
        private Row editRow = null;

        /**
         * Creates the canvas and installs mouse handlers for hover, editing, and dragging.
         */
        Canvas() {
            this.setLayout(null);
            this.setOpaque(false);
            ToolTipManager.sharedInstance().registerComponent(this);

            MouseAdapter mouse = new MouseAdapter() {
                /**
                 * Starts click actions for left-button presses.
                 * @param e mouse press event
                 */
                @Override
                public void mousePressed(MouseEvent e) {
                    // Ignore non-left-button presses; other mouse actions are not click commands.
                    if (SwingUtilities.isLeftMouseButton(e))
                        onMouseClick(e);
                }

                /**
                 * Ends any active region drag.
                 * @param e mouse release event
                 */
                @Override
                public void mouseReleased(MouseEvent e) {
                    drag = Drag.NONE;
                }

                /**
                 * Updates hover state as the pointer moves.
                 * @param e mouse movement event
                 */
                @Override
                public void mouseMoved(MouseEvent e) {
                    lastPoint = logical(e.getPoint());
                    updateHover();
                }

                /**
                 * Updates the active drag and hover state while the pointer moves.
                 * @param e mouse drag event
                 */
                @Override
                public void mouseDragged(MouseEvent e) {
                    lastPoint = logical(e.getPoint());
                    dragTo(e.getX());
                    updateHover();
                }

                /**
                 * Clears hover state after the pointer leaves the canvas.
                 * @param e mouse exit event
                 */
                @Override
                public void mouseExited(MouseEvent e) {
                    lastPoint = null;
                    updateHover();
                }
            };
            this.addMouseListener(mouse);
            this.addMouseMotionListener(mouse);
        }

        ///// geometry /////

        /**
         * Gets the viewport's horizontal scroll offset.
         * @return nonnegative x-offset in canvas coordinates
         */
        private int scrollX() {
            JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
            return (vp == null) ? 0 : Math.max(0, SwingUtilities.convertPoint(vp, 0, 0, this).x);   // viewport's left edge in canvas coordinates (the panel border shifts the canvas)
        }

        /**
         * Maps a mouse point to the unscrolled layout, shifting points over the sticky pool.
         * @param point mouse point in canvas coordinates
         * @return point in logical, unscrolled coordinates
         */
        private Point logical(Point point) {
            int sx = this.scrollX();
            return ((sx > 0) && (point.x >= sx) && (point.x < sx + this.mx()))
                    ? new Point(point.x - sx, point.y) : point;
        }

        /**
         * Gets the horizontal offset at which matrix columns begin.
         * @return left edge of the matrix
         */
        private int mx() {
            return FLAG_W + LABEL_W + BTN_W + GAP;
        }

        /**
         * Gets the top y-coordinate of a displayed row.
         * @param i displayed row index or the boundary after the last row
         * @return row top in canvas coordinates
         */
        private int rowY(int i) {
            return HEADER_H + i * ROW_H;
        }

        /**
         * Gets the top y-coordinate of the repetition timeline.
         * @return region bar y-coordinate
         */
        private int regionY() {
            return rowY(rows.size() + 1) + 6;
        }

        /**
         * Returns note rows ordered from highest pitch to lowest.
         * @return a snapshot of the rows in display order
         */
        private List<Row> displayRows() {
            return new ArrayList<>(rows.descendingMap().values());
        }

        /**
         * Checks whether a point lies within the repetition timeline.
         * @param point logical canvas point
         * @return true if the point is within the timeline and matrix width
         */
        private boolean inRegionBar(Point point) {
            return (columns > 0) && (point.y >= regionY()) && (point.y <= regionY() + REGION_H)
                    && (point.x >= mx()) && (point.x <= mx() + columns * COL_W);
        }

        /**
         * Computes the minimum canvas size needed for the current matrix and note pool.
         * @return preferred canvas dimensions
         */
        @Override
        public Dimension getPreferredSize() {
            int shownRows = Math.max(rows.size(), MIN_ROWS);
            return new Dimension(mx() + (Math.max(columns, MIN_COLUMNS) + 1) * COL_W + 4, HEADER_H + (shownRows + 1) * ROW_H + REGION_H + 12);
        }

        ///// hover /////

        /**
         * Recalculates hovered rows, columns, and insertion/removal controls.
         */
        void updateHover() {
            HoverState hover = this.calculateHoverState(this.lastPoint);
            // Redraw hover affordances only when the hit-test result changed.
            if ((hover.row != this.hoverRow) || (hover.column != this.hoverCol)
                    || (hover.columnBoundary != this.hoverColumnBoundary)
                    || (hover.columnRemoval != this.hoverColumnRemoval)
                    || (hover.noteInsertIndex != this.hoverNoteInsertIndex)) {
                this.hoverRow = hover.row;
                this.hoverCol = hover.column;
                this.hoverColumnBoundary = hover.columnBoundary;
                this.hoverColumnRemoval = hover.columnRemoval;
                this.hoverNoteInsertIndex = hover.noteInsertIndex;
                this.repaint();
            }
        }

        /**
         * Computes all hover targets from a logical canvas point.
         * @param point current mouse position, or null when the pointer is outside
         * @return hover targets, initialized to no target when the point is null
         */
        private HoverState calculateHoverState(Point point) {
            HoverState hover = new HoverState();
            // No point means the pointer is outside, so there are no active hover targets.
            if (point == null)
                return hover;
            int matrixX = this.mx();
            int matrixBottom = this.rowY(rows.size());
            this.calculateRowHover(point, matrixBottom, matrixX, hover);
            // Compute column-header hover only for points inside the header.
            if ((point.y >= 0) && (point.y < HEADER_H))
                hover.columnBoundary = this.columnBoundaryForHover(point.x);
            // Check note insertion flags only in the flag strip.
            if (point.x < FLAG_W) {
                int boundary = this.noteInsertIndexAt(point.y);
                // Set an insertion target only when a flag actually covers the pointer.
                if (boundary >= 0)
                    hover.noteInsertIndex = boundary;
            }
            this.calculateColumnRemovalHover(point, matrixBottom, matrixX, hover);
            return hover;
        }

        /**
         * Calculates hovered note and matrix-cell targets for a point within the row area.
         * @param point logical canvas point
         * @param matrixBottom y-coordinate after the last row
         * @param matrixX left edge of the matrix
         * @param hover state to update
         */
        private void calculateRowHover(Point point, int matrixBottom, int matrixX, HoverState hover) {
            // Ignore points outside the note-row area before calculating row targets.
            if ((point.y < HEADER_H) || (point.y >= matrixBottom))
                return;
            int rowIndex = (point.y - HEADER_H) / ROW_H;
            hover.row = (rowIndex < rows.size()) ? rowIndex : -1;
            // Resolve a matrix cell only when the pointer lies over the column grid.
            if ((point.x >= matrixX) && (point.x < matrixX + columns * COL_W)) {
                hover.column = (point.x - matrixX) / COL_W;
                int withinColumn = (point.x - matrixX) % COL_W;
                hover.columnBoundary = (withinColumn < COL_W / 2) ? hover.column : hover.column + 1;
                hover.columnRemoval = hover.column;
            }
            // Points left of the matrix belong to the note-pool insertion area.
            if (point.x < matrixX)
                hover.noteInsertIndex = ((point.y - HEADER_H) % ROW_H < ROW_H / 2) ? rowIndex : rowIndex + 1;
        }

        /**
         * Calculates the hovered column-removal target below the final note row.
         * @param point logical canvas point
         * @param matrixBottom y-coordinate after the last row
         * @param matrixX left edge of the matrix
         * @param hover state to update
         */
        private void calculateColumnRemovalHover(Point point, int matrixBottom, int matrixX, HoverState hover) {
            // Limit removal hover feedback to the strip beneath rows and over matrix columns.
            if ((point.y < matrixBottom) || (point.y >= matrixBottom + 6 + FLAG_H)
                    || (point.x < matrixX) || (point.x >= matrixX + columns * COL_W))
                return;
            hover.columnRemoval = (point.x - matrixX) / COL_W;
        }

        /**
         * Provides contextual help for note-pool, matrix, and repetition controls.
         * @param e mouse event used to determine the hovered control
         * @return tooltip text, or null when the point has no tooltip
         */
        @Override
        public String getToolTipText(MouseEvent e) {
            Point p = this.logical(e.getPoint());
            // Give the repetition control priority over other tooltip targets.
            if (this.inRegionBar(p))
                return "Drag to set the repetition region; double-click it to remove";
            String noteInsertionTooltip = this.noteInsertionTooltip(p);
            // Return a detected insertion tooltip before checking other controls.
            if (noteInsertionTooltip != null)
                return noteInsertionTooltip;
            // Header points can only describe column insertion controls.
            if (p.y < HEADER_H)
                return this.columnInsertionTooltip(p);
            // Report the removal action only when the pointer is on its flag.
            if (this.isColumnRemovalFlag(p))
                return "Remove this column";
            return this.notePoolTooltip(p);
        }

        /**
         * Returns a tooltip for an eligible note-pool insertion boundary.
         * @param point logical canvas point
         * @return insertion status text, or null when no insertion boundary is active
         */
        private String noteInsertionTooltip(Point point) {
            boolean overNotePool = point.x < FLAG_W + LABEL_W;
            boolean overInsertFlag = (point.x < FLAG_W)
                    && (this.noteInsertIndexAt(point.y) == this.hoverNoteInsertIndex);
            boolean overHoveredNote = (point.y >= HEADER_H)
                    && ((point.y - HEADER_H) / ROW_H == this.hoverRow);
            // Show insertion status only for an active boundary within the note pool.
            if ((overNotePool || overInsertFlag) && (this.hoverNoteInsertIndex >= 0)
                    && (this.hoverNoteInsertIndex <= rows.size()) && (overHoveredNote || overInsertFlag)) {
                Double pitch = this.notePitchAtBoundary(this.hoverNoteInsertIndex);
                // Explain pitch collisions before reporting that insertion is available.
                if (pitch != null && rows.containsKey(pitch))
                    return "A note with this pitch already exists.";
                // Explain out-of-range suggestions instead of offering an invalid insertion.
                if (pitch == null || pitch < 0 || pitch > 127)
                    return "No MIDI pitch available at this boundary.";
                return "Add a note at this boundary";
            }
            return null;
        }

        /**
         * Returns a tooltip when the pointer is over a column insertion flag.
         * @param point logical canvas point
         * @return insertion tooltip, or null when no flag is under the pointer
         */
        private String columnInsertionTooltip(Point point) {
            return (point.y >= 2) && (point.y < 2 + FLAG_H) && (this.columnBoundaryAt(point.x) >= 0)
                    ? "Add a column at this boundary" : null;
        }

        /**
         * Checks whether a point is over a column-removal flag.
         * @param point logical canvas point
         * @return true if the point is within a visible removal flag
         */
        private boolean isColumnRemovalFlag(Point point) {
            int matrixBottom = this.rowY(rows.size());
            // A removal tooltip requires the point to lie inside the column-flag strip.
            if ((point.y < matrixBottom + 6) || (point.y >= matrixBottom + 6 + FLAG_H)
                    || (point.x < this.mx()) || (point.x >= this.mx() + columns * COL_W))
                return false;
            int column = (point.x - this.mx()) / COL_W;
            int buttonCenter = this.mx() + column * COL_W + COL_W / 2;
            return Math.abs(point.x - buttonCenter) <= FLAG_W / 2;
        }

        /**
         * Returns a tooltip for a note-pool row or its insertion affordance.
         * @param point logical canvas point
         * @return note-pool tooltip, or null when no note-pool control is under the pointer
         */
        private String notePoolTooltip(Point point) {
            List<Row> disp = displayRows();
            int rowIndex = Math.min((point.y - HEADER_H) / ROW_H, disp.size());
            // The left flag strip is not a note label or row tooltip.
            if (point.x < FLAG_W)
                return (rowIndex == disp.size()) ? "Add a note below the lowest" : "Add a note above";
            // Show note details only when the pointer is within an existing label row.
            if ((rowIndex < disp.size()) && (point.x < FLAG_W + LABEL_W))
                return disp.get(rowIndex).isMsm()
                        ? "Note from the MSM (fixed)" : "Additional note; double-click to edit the midi.pitch";
            return null;
        }

        ///// interaction /////

        /**
         * Dispatches a left click to the corresponding note-pool, matrix, or region action.
         * @param event mouse event describing the click
         */
        private void onMouseClick(MouseEvent event) {
            this.commit();
            Point point = this.logical(event.getPoint());
            // Stop dispatch once an insertion control has consumed the click.
            if (this.handleColumnInsertionClick(point) || this.handleNoteInsertionClick(point))
                return;
            // Header clicks are handled by insertion controls and do not select rows.
            if (point.y < HEADER_H)
                return;
            // Handle repetition-region gestures before row actions.
            if (this.inRegionBar(point)) {
                // The repetition slider is read-only while matrix editing is disabled.
                if (matrixEnabled)
                    this.pressRegion(event);
                return;
            }

            List<Row> displayedRows = this.displayRows();
            int rowIndex = (point.y - HEADER_H) / ROW_H;
            // Stop dispatch when the column-removal control handled the click.
            if (this.handleColumnRemovalClick(point, rowIndex, displayedRows.size()))
                return;
            // Ignore clicks below the final displayed note row.
            if (rowIndex >= displayedRows.size())
                return;
            this.handleRowClick(event, point, displayedRows.get(rowIndex), rowIndex);
        }

        /**
         * Handles a click on the column insertion flags in the matrix header.
         * @param point click position in logical canvas coordinates
         * @return true if the header flag area consumed the click
         */
        private boolean handleColumnInsertionClick(Point point) {
            // Only the matrix header strip can contain a column-insertion flag.
            if ((point.y < 2) || (point.y >= 2 + FLAG_H) || (point.x < this.mx()))
                return false;
            // Do not modify columns while matrix editing is disabled.
            if (matrixEnabled) {
                int boundary = this.columnBoundaryAt(point.x);
                // Insert only when the pointer is close to a valid grid boundary.
                if (boundary >= 0)
                    insertColumn(boundary);
            }
            return true;
        }

        /**
         * Adds a note when the active note-pool insertion flag was clicked.
         * @param point click position in logical canvas coordinates
         * @return true if the insertion flag consumed the click
         */
        private boolean handleNoteInsertionClick(Point point) {
            // Only the note-pool flag strip can activate note insertion.
            if ((point.x >= FLAG_W) || (this.hoverNoteInsertIndex < 0))
                return false;
            int insertIndex = this.noteInsertIndexAt(point.y);
            // Require the click boundary to match the currently hovered insertion flag.
            if (insertIndex != this.hoverNoteInsertIndex)
                return false;
            Double pitch = this.notePitchAtBoundary(insertIndex);
            // Create a row only for an available, unused suggested pitch.
            if ((pitch != null) && !rows.containsKey(pitch))
                addRow(pitch);
            return true;
        }

        /**
         * Removes a matrix column when its removal flag is clicked.
         * @param point click position in logical canvas coordinates
         * @param rowIndex displayed row index under the click
         * @param rowCount number of displayed note rows
         * @return true if the removal flag consumed the click
         */
        private boolean handleColumnRemovalClick(Point point, int rowIndex, int rowCount) {
            int mx = this.mx();
            // Only an enabled matrix removal flag under the final row can remove a column.
            if (!matrixEnabled || (rowIndex != rowCount) || (point.x < mx)
                    || (point.x >= mx + columns * COL_W))
                return false;
            int column = (point.x - mx) / COL_W;
            int buttonCenter = mx + column * COL_W + COL_W / 2;
            boolean inFlag = Math.abs(point.x - buttonCenter) <= FLAG_W / 2;
            boolean inFlagY = (point.y >= this.rowY(rowCount) + 6)
                    && (point.y < this.rowY(rowCount) + 6 + FLAG_H);
            // Require the pointer to lie within both dimensions of the removal flag.
            if (!inFlag || !inFlagY)
                return false;
            removeColumn(column);
            return true;
        }

        /**
         * Handles label playback/editing, note deletion, and matrix-hit toggles for a row.
         * @param event mouse event describing the click
         * @param point click position in logical canvas coordinates
         * @param row selected note row
         * @param rowIndex displayed row index
         */
        private void handleRowClick(MouseEvent event, Point point, Row row, int rowIndex) {
            // Clicks on the insertion gutter do not activate note rows.
            if (point.x < FLAG_W)
                return;
            // Label clicks play notes or open the editor rather than editing matrix hits.
            if (point.x < FLAG_W + LABEL_W) {
                // Double-click an additional note label to edit its pitch.
                if ((event.getClickCount() >= 2) && !row.isMsm())
                    this.startEdit(row, rowIndex);
                // Play the selected note when a playback callback is installed.
                else if (notePlayer != null)
                    notePlayer.accept(row.pitch);
                return;
            }
            // The note-pool action area handles deletion separately from matrix cells.
            if (point.x < this.mx()) {
                // MSM notes are fixed and cannot be removed from the note pool.
                if (!row.isMsm()) {
                    rows.remove(row.pitch);
                    refresh();
                }
                return;
            }

            int column = (point.x - this.mx()) / COL_W;
            // Toggle hits only for existing columns while the matrix is enabled.
            if (matrixEnabled && (column < columns)) {
                boolean hit = !row.hits.get(column);
                row.hits.set(column, hit);
                defaultColumn = false;
                // Play a note only when the click turns its hit on.
                if (hit && (notePlayer != null))
                    notePlayer.accept(row.pitch);
                this.repaint();
            }
        }

        /**
         * Opens an inline pitch editor for an additional note.
         * @param row note row to edit
         * @param index displayed row index used to position the editor
         */
        private void startEdit(Row row, int index) {
            this.stopEdit();
            this.editRow = row;
            this.editor = new JTextField(pitchString(row.pitch));
            this.editor.setHorizontalAlignment(SwingConstants.CENTER);
            this.editor.setBounds(FLAG_W + this.scrollX(), this.rowY(index), LABEL_W, ROW_H);
            this.editor.addActionListener(e -> this.commit());
            this.editor.addFocusListener(new FocusAdapter() {
                /**
                 * Commits the pitch when the editor loses focus.
                 * @param e focus event
                 */
                @Override
                public void focusLost(FocusEvent e) {
                    commit();
                }
            });
            this.editor.addKeyListener(new KeyAdapter() {
                /**
                 * Cancels inline editing when Escape is pressed.
                 * @param e key event
                 */
                @Override
                public void keyPressed(KeyEvent e) {
                    // Escape cancels the inline edit instead of committing it.
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
                        stopEdit();
                }
            });
            this.add(this.editor);
            this.editor.selectAll();
            this.repaint();
            JTextField f = this.editor;
            SwingUtilities.invokeLater(f::requestFocusInWindow);
        }

        /**
         * Closes the active inline editor without applying its contents.
         */
        void stopEdit() {
            JTextField f = this.editor;
            this.editor = null;
            this.editRow = null;
            // Remove and repaint only when an editor component exists.
            if (f != null) {
                this.remove(f);
                this.repaint();
            }
        }

        /**
         * Applies a valid edited pitch and leaves the row unchanged for invalid or duplicate values.
         */
        private void commit() {
            // There is nothing to commit when no inline editor is open.
            if (this.editor == null)
                return;
            String text = this.editor.getText().trim();
            Row row = this.editRow;
            this.stopEdit();
            try {
                double p = Double.parseDouble(text.replace(',', '.'));
                // Reject invalid, unchanged, or duplicate pitches without altering the row.
                if ((p < 0) || (p > 127) || (p == row.pitch) || rows.containsKey(p))
                    return;
                rows.remove(row.pitch);
                row.pitch = p;
                rows.put(p, row);
                refresh();
            } catch (NumberFormatException ignored) {
            }
        }

        /**
         * Maps a canvas x-coordinate to a clamped matrix column.
         * @param x canvas x-coordinate
         * @return column index in the current matrix
         */
        private int cellAt(int x) {
            return Math.max(0, Math.min(columns - 1, (x - this.mx()) / COL_W));
        }

        /**
         * Finds a column boundary within the clickable insertion-flag tolerance.
         * @param x canvas x-coordinate
         * @return boundary index, or -1 when none is close enough
         */
        private int columnBoundaryAt(int x) {
            // Round to the nearest grid boundary, then reject points outside the visible flag width.
            int c = Math.round((float) (x - this.mx()) / COL_W);
            int boundaryX = this.mx() + c * COL_W;
            return (c >= 0) && (c <= columns) && (Math.abs(x - boundaryX) <= FLAG_W / 2) ? c : -1;
        }

        /**
         * Finds the nearest column boundary for hover feedback.
         * @param x canvas x-coordinate
         * @return boundary index, or -1 outside the header's hover area
         */
        private int columnBoundaryForHover(int x) {
            int firstBoundary = this.mx();
            int lastBoundary = firstBoundary + columns * COL_W;
            // Reject x-coordinates outside the header boundary hover span.
            if ((x < firstBoundary - FLAG_W / 2) || (x > lastBoundary + FLAG_W / 2))
                return -1;
            // With no columns, the only available boundary is the initial one.
            if (columns == 0)
                return 0;
            return Math.max(0, Math.min(columns, Math.round((float) (x - firstBoundary) / COL_W)));
        }

        /**
         * Gets the y-coordinate of a note insertion flag.
         * @param index boundary index in displayed-row order
         * @return top y-coordinate of the flag
         */
        private int noteInsertFlagY(int index) {
            return this.rowY(index) - FLAG_H / 2;
        }

        /**
         * Finds the note insertion boundary covered by a y-coordinate.
         * @param y canvas y-coordinate
         * @return boundary index, or -1 if no insertion flag is under the point
         */
        private int noteInsertIndexAt(int y) {
            for (int i = 0; i <= rows.size(); ++i) {
                int flagTop = this.noteInsertFlagY(i);
                // Return the row boundary whose insertion flag contains this y-coordinate.
                if ((y >= flagTop) && (y < flagTop + FLAG_H))
                    return i;
            }
            return -1;
        }

        /**
         * Suggests a MIDI pitch for inserting a row at a displayed boundary.
         * @param index boundary index in displayed-row order
         * @return suggested pitch, or null for an invalid boundary
         */
        private Double notePitchAtBoundary(int index) {
            List<Row> display = this.displayRows();
            // Reject boundaries outside the displayed note list.
            if (index < 0 || index > display.size())
                return null;
            // Use middle C as the initial pitch when the note pool is empty.
            if (display.isEmpty())
                return 60.0;
            // The pool is descending by pitch, so adjacent boundary pitches differ by one semitone.
            // The first displayed boundary is one semitone above the highest row.
            if (index == 0)
                return display.get(0).pitch + 1.0;
            // The final displayed boundary is one semitone below the lowest row.
            if (index == display.size())
                return display.get(index - 1).pitch - 1.0;
            return display.get(index).pitch + 1.0;
        }

        /**
         * Computes the painted bounds of the repetition region.
         * @return region rectangle in canvas coordinates
         */
        private Rectangle regionRect() {
            return new Rectangle(this.mx() + regionStart * COL_W, this.regionY() + 2, (regionEnd - regionStart + 1) * COL_W, REGION_H - 4);
        }

        /**
         * Begins creating, moving, resizing, or removing the repetition region.
         * @param e mouse press within the region bar
         */
        private void pressRegion(MouseEvent e) {
            int c = this.cellAt(e.getX());
            // Resize, move, or remove handles are meaningful only for an existing region.
            if (hasRepetitionRegion()) {
                Rectangle r = this.regionRect();
                // A press near the left edge starts resizing the region from the left.
                if (Math.abs(e.getX() - r.x) <= 6) {
                    this.drag = Drag.LEFT;
                    return;
                }
                // A press near the right edge starts resizing the region from the right.
                if (Math.abs(e.getX() - (r.x + r.width)) <= 6) {
                    this.drag = Drag.RIGHT;
                    return;
                }
                // A press inside the region moves it or removes it on a double-click.
                if (r.contains(e.getPoint())) {
                    // Double-clicking the selected region clears it.
                    if (e.getClickCount() >= 2) {
                        regionStart = regionEnd = -1;
                        this.repaint();
                        return;
                    }
                    this.drag = Drag.MOVE;
                    this.dragOffset = c - regionStart;
                    return;
                }
            }
            regionStart = regionEnd = c;
            this.drag = Drag.CREATE;
            this.dragOffset = c;
            this.repaint();
        }

        /**
         * Updates repetition-region bounds while a region drag is active.
         * @param x current canvas x-coordinate
         */
        private void dragTo(int x) {
            // Ignore drag events until a region drag mode has been selected.
            if (this.drag == Drag.NONE)
                return;
            int c = this.cellAt(x);
            switch (this.drag) {
                case CREATE:
                    regionStart = Math.min(this.dragOffset, c);
                    regionEnd = Math.max(this.dragOffset, c);
                    break;
                case LEFT:
                    regionStart = Math.min(c, regionEnd);
                    break;
                case RIGHT:
                    regionEnd = Math.max(c, regionStart);
                    break;
                case MOVE: {
                    int len = regionEnd - regionStart;
                    // Clamp the translated start so the whole region stays inside the matrix.
                    int s = Math.max(0, Math.min(columns - 1 - len, c - this.dragOffset));
                    regionStart = s;
                    regionEnd = s + len;
                    break;
                }
                default:
            }
            this.repaint();
        }

        ///// painting /////

        /**
         * Draws text vertically centered in a rectangle, optionally centered horizontally.
         * @param g graphics context
         * @param s text to draw
         * @param x rectangle left edge
         * @param y rectangle top edge
         * @param w rectangle width
         * @param h rectangle height
         * @param center whether to center the text horizontally
         */
        private void drawText(Graphics2D g, String s, int x, int y, int w, int h, boolean center) {
            FontMetrics fm = g.getFontMetrics();
            int tx = center ? x + (w - fm.stringWidth(s)) / 2 : x;
            g.drawString(s, tx, y + (h + fm.getAscent() - fm.getDescent()) / 2);
        }

        /**
         * Draws an enabled flag-style button.
         * @param g graphics context
         * @param x button left edge
         * @param y button top edge
         * @param w button width
         * @param h button height
         * @param text button label
         * @param pointer edge indicated by the flag pointer
         */
        private void drawFlag(Graphics2D g, int x, int y, int w, int h, String text, Pointer pointer) {
            this.drawFlag(g, x, y, w, h, text, pointer, true);
        }

        /**
         * Draws a flag-style button in its enabled or disabled appearance.
         * @param g graphics context
         * @param x button left edge
         * @param y button top edge
         * @param w button width
         * @param h button height
         * @param text button label
         * @param pointer edge indicated by the flag pointer
         * @param enabled whether the button should look enabled
         */
        private void drawFlag(Graphics2D g, int x, int y, int w, int h, String text, Pointer pointer, boolean enabled) {
            g.setColor(enabled ? FLAG_COLOR : withAlpha(FLAG_COLOR, 10));
            g.fillRoundRect(x, y, w, h, 5, 5);
            int pointerWidth = Math.min(w - 4, 12);
            int pointerDepth = 8;
            // Draw the flag pointer toward the top edge when requested.
            if (pointer == Pointer.UP)
                g.fillPolygon(  new int[]{x + w / 2 - pointerWidth / 2, x + w / 2 + pointerWidth / 2, x + w / 2},
                                new int[]{y, y, y - pointerDepth}, 3);
            // Draw the flag pointer toward the bottom edge when requested.
            else if (pointer == Pointer.DOWN)
                g.fillPolygon(  new int[]{x + w / 2 - pointerWidth / 2, x + w / 2 + pointerWidth / 2, x + w / 2},
                                new int[]{y + h, y + h, y + h + pointerDepth}, 3);
            // Draw the flag pointer toward the left edge when requested.
            else if (pointer == Pointer.LEFT)
                g.fillPolygon(  new int[]{x, x, x - pointerDepth},
                                new int[]{y + h / 2 - pointerWidth / 2, y + h / 2 + pointerWidth / 2, y + h / 2}, 3);
            // Draw the flag pointer toward the right edge when requested.
            else if (pointer == Pointer.RIGHT)
                g.fillPolygon(  new int[]{x + w, x + w, x + w + pointerDepth},
                                new int[]{y + h / 2 - pointerWidth / 2, y + h / 2 + pointerWidth / 2, y + h / 2}, 3);
            g.setColor(enabled ? Color.WHITE : withAlpha(Color.WHITE, 100));
            this.drawText(g, text, x, y, w, h, true);
        }

        /**
         * Paints the matrix and sticky note pool.
         * @param g0 Swing-provided graphics context
         */
        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Color foreground = (this.getForeground() != null) ? this.getForeground() : Settings.foregroundColor;
                List<Row> displayedRows = this.displayRows();
                this.drawMatrix(g, foreground, displayedRows);
                this.drawNotePool(g, foreground, displayedRows);
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints the matrix background, column headers, hit cells, and repetition slider.
         * @param g graphics context
         * @param foreground current foreground color
         * @param displayedRows rows in top-to-bottom display order
         */
        private void drawMatrix(Graphics2D g, Color foreground, List<Row> displayedRows) {
            int rowCount = displayedRows.size();
            int matrixX = this.mx();
            // Paint a region highlight only when valid repetition bounds exist.
            if (hasRepetitionRegion()) {
                g.setColor(withAlpha(REGION_COLOR, matrixEnabled ? 35 : 15));
                g.fillRect(matrixX + regionStart * COL_W, HEADER_H,
                        (regionEnd - regionStart + 1) * COL_W, rowCount * ROW_H);
            }
            this.drawColumnHeaders(g, foreground, matrixX);
            this.drawHitCells(g, foreground, displayedRows, matrixX);
            this.drawColumnRemovalFlag(g, matrixX, rowCount);
            this.drawRegionSlider(g, foreground, matrixX);
        }

        /**
         * Paints the insertion flag and numbered labels for matrix columns.
         * @param g graphics context
         * @param foreground current foreground color
         * @param matrixX left edge of the matrix
         */
        private void drawColumnHeaders(Graphics2D g, Color foreground, int matrixX) {
            // Show a column insertion flag only for an enabled matrix boundary.
            if (matrixEnabled && (this.hoverColumnBoundary >= 0))
                this.drawFlag(g, matrixX + this.hoverColumnBoundary * COL_W - FLAG_W / 2,
                        2, FLAG_W, FLAG_H, "+", Pointer.DOWN);
            for (int column = 0; column < columns; ++column) {
                int columnX = matrixX + column * COL_W;
                g.setColor(withAlpha(foreground, matrixEnabled ? 200 : 90));
                this.drawText(g, Integer.toString(column + 1), columnX, COLUMN_ACTION_H, COL_W,
                        HEADER_H - COLUMN_ACTION_H, true);
            }
        }

        /**
         * Paints note rows and their active hit cells.
         * @param g graphics context
         * @param foreground current foreground color
         * @param displayedRows rows in top-to-bottom display order
         * @param matrixX left edge of the matrix
         */
        private void drawHitCells(Graphics2D g, Color foreground, List<Row> displayedRows, int matrixX) {
            for (int rowIndex = 0; rowIndex < displayedRows.size(); ++rowIndex) {
                Row row = displayedRows.get(rowIndex);
                int top = this.rowY(rowIndex);
                boolean hovered = rowIndex == this.hoverRow;
                Color noteColor = row.isMsm() ? MSM_COLOR : EXTRA_COLOR;
                Color hitColor = row.isMsm() ? MSM_COLOR : Settings.scorePerformanceColor;
                // Fall back to the component foreground when the note color is unavailable.
                if (noteColor == null)
                    noteColor = foreground;

                for (int column = 0; column < columns; ++column) {
                    int columnX = matrixX + column * COL_W;
                    g.setColor(withAlpha(foreground, (hovered || (column == this.hoverCol)) ? 28 : 14));
                    g.fillRoundRect(columnX + 2, top + 2, COL_W - 4, ROW_H - 4, 6, 6);
                    // Draw a hit marker only for active note-column pairs.
                    if (row.hits.get(column)) {
                        // Center each hit marker within its cell; alpha distinguishes enabled and disabled states.
                        g.setColor(matrixEnabled ? withAlpha(hitColor, 190) : withAlpha(hitColor, 90));
                        g.fillOval(columnX + (COL_W - 14) / 2, top + (ROW_H - 14) / 2, 14, 14);
                    }
                }
            }
        }

        /**
         * Paints the removal flag beneath the currently hovered column.
         * @param g graphics context
         * @param matrixX left edge of the matrix
         * @param rowCount number of displayed note rows
         */
        private void drawColumnRemovalFlag(Graphics2D g, int matrixX, int rowCount) {
            // Hide the removal affordance when editing is disabled or no column is hovered.
            if (!matrixEnabled || (this.hoverColumnRemoval < 0))
                return;
            int y = this.rowY(rowCount) + 6;
            int column = this.hoverColumnRemoval;
            this.drawFlag(g, matrixX + column * COL_W + (COL_W - FLAG_W) / 2, y,
                    FLAG_W, FLAG_H, "-", Pointer.UP);
        }

        /**
         * Paints the repetition timeline, selected region, handles, and repetition count.
         * @param g graphics context
         * @param foreground current foreground color
         * @param matrixX left edge of the matrix
         */
        private void drawRegionSlider(Graphics2D g, Color foreground, int matrixX) {
            // Do not draw a timeline when there are no columns.
            if (columns <= 0)
                return;
            int y = this.regionY();
            g.setColor(withAlpha(foreground, 40));
            g.fillRoundRect(matrixX, y + REGION_H / 2 - 2, columns * COL_W, 4, 4, 4);
            // Skip region fills and handles when no valid region is selected.
            if (!hasRepetitionRegion())
                return;

            Rectangle region = this.regionRect();
            Color regionColor = matrixEnabled ? REGION_COLOR : withAlpha(REGION_COLOR, 80);
            g.setColor(withAlpha(regionColor, 90));
            g.fillRoundRect(region.x, region.y, region.width, region.height, 8, 8);
            g.setColor(regionColor);
            g.drawRoundRect(region.x, region.y, region.width, region.height, 8, 8);
            // The narrow end caps are the drag handles for resizing the selected region.
            g.fillRoundRect(region.x - 2, region.y, 5, region.height, 3, 3);
            g.fillRoundRect(region.x + region.width - 3, region.y, 5, region.height, 3, 3);

            String text = "|: x" + ((repetitions < 0) ? "auto" : Integer.toString(repetitions)) + " :|";
            // Draw the repetition label only when it fits inside the selected region.
            if (g.getFontMetrics().stringWidth(text) < region.width - 14) {
                g.setColor(foreground);
                this.drawText(g, text, region.x, region.y, region.width, region.height, true);
            }
        }

        /**
         * Paints the sticky note pool and its hover controls over the matrix canvas.
         * @param g base graphics context
         * @param foreground current foreground color
         * @param displayedRows rows in top-to-bottom display order
         */
        private void drawNotePool(Graphics2D g, Color foreground, List<Row> displayedRows) {
            // Translate the pool with the viewport so it remains visible during horizontal scrolling.
            int scrollX = this.scrollX();
            Graphics2D poolGraphics = (Graphics2D) g.create();
            try {
                poolGraphics.translate(scrollX, 0);
                // Paint an opaque sticky-pool background only after horizontal scrolling.
                if (scrollX > 0) {
                    Container parent = this.getParent();
                    poolGraphics.setColor((parent != null) ? parent.getBackground() : this.getBackground());
                    poolGraphics.fillRect(0, 0, this.mx(), this.getHeight());
                }
                this.drawNoteLabels(poolGraphics, foreground, displayedRows);
                this.drawNoteInsertionFlag(poolGraphics, displayedRows.size());
            } finally {
                poolGraphics.dispose();
            }
        }

        /**
         * Paints note labels and the delete control for a hovered additional note.
         * @param g translated graphics context for the sticky note pool
         * @param foreground current foreground color
         * @param displayedRows rows in top-to-bottom display order
         */
        private void drawNoteLabels(Graphics2D g, Color foreground, List<Row> displayedRows) {
            for (int rowIndex = 0; rowIndex < displayedRows.size(); ++rowIndex) {
                Row row = displayedRows.get(rowIndex);
                int top = this.rowY(rowIndex);
                boolean hovered = rowIndex == this.hoverRow;
                Color labelColor = row.isMsm() ? MSM_COLOR : EXTRA_COLOR;
                // Fall back to the foreground when the note label color is unavailable.
                if (labelColor == null)
                    labelColor = foreground;

                g.setColor(withAlpha(labelColor, hovered ? 80 : 45));
                g.fillRoundRect(FLAG_W, top + 1, LABEL_W, ROW_H - 2, 8, 8);
                g.setColor(labelColor);
                g.drawRoundRect(FLAG_W, top + 1, LABEL_W, ROW_H - 2, 8, 8);
                g.setColor(foreground);
                // Hide the label while its inline editor is displayed over it.
                if (row != this.editRow)
                    this.drawText(g, label(row), FLAG_W + 8, top, LABEL_W, ROW_H, false);
                // Show a delete control only for a hovered additional note.
                if (hovered && !row.isMsm())
                    this.drawFlag(g, FLAG_W + LABEL_W + (BTN_W - FLAG_W) / 2,
                            top + (ROW_H - FLAG_H) / 2, FLAG_W, FLAG_H, "\u2212", Pointer.LEFT);
            }
        }

        /**
         * Paints the insertion control for the currently hovered note-pool boundary.
         * @param g translated graphics context for the sticky note pool
         * @param rowCount number of displayed note rows
         */
        private void drawNoteInsertionFlag(Graphics2D g, int rowCount) {
            int insertIndex = this.hoverNoteInsertIndex;
            // Draw the insertion flag only for a valid note-pool boundary.
            if ((insertIndex < 0) || (insertIndex > rowCount))
                return;
            Double pitch = this.notePitchAtBoundary(insertIndex);
            boolean enabled = (pitch != null) && (pitch >= 0) && (pitch <= 127) && !rows.containsKey(pitch);
            int boundaryY = this.rowY(insertIndex);
            this.drawFlag(g, 0, boundaryY - FLAG_H / 2, FLAG_W, FLAG_H, "+", Pointer.RIGHT, enabled);
        }
    }
}
