package mpmToolbox.gui.audio;

import java.util.ArrayList;

/**
 * Describes one column (data line) in an AnnotationData object.
 * Holds the column's display name, its data type, its physical unit, and the column's values.
 * @author Lars Engeln
 */
public class AnnotationLine {

    /**
     * The role a column plays in the data.
     */
    public enum Type {
        TIME("time"),
        CURVE("curve"),
        MARKS("marks"),
        TEXT("text");

        private final String label;
        Type(String label) { this.label = label; }
        @Override public String toString() { return this.label; }
    }

    /**
     * The unit of the values in a column.
     */
    public enum Unit {
        SECONDS("seconds"),
        MILLISECONDS("milliseconds"),
        HZ("hz"),
        PERCENT("%");

        private final String label;
        Unit(String label) { this.label = label; }
        @Override public String toString() { return this.label; }

        /**
         * Convert a value in this unit to milliseconds. Returns the value unchanged if already ms.
         * @param value
         * @return
         */
        public double toMilliseconds(double value) {
            return (this == SECONDS) ? value * 1000.0 : value;
        }
    }

    /** Units valid for time-like types (TIME, MARKS). */
    private static final Unit[] TIME_UNITS  = { Unit.SECONDS, Unit.MILLISECONDS };
    /** Units valid for curve/value types (CURVE). */
    private static final Unit[] CURVE_UNITS = { Unit.HZ, Unit.PERCENT };
    /** TEXT columns carry no meaningful unit. */
    private static final Unit[] TEXT_UNITS  = {};

    /**
     * Returns the set of {@link Unit} values that are valid for the given {@link Type}.
     * <ul>
     *   <li>TIME / MARKS → SECONDS, MILLISECONDS</li>
     *   <li>CURVE        → HZ, PERCENT</li>
     *   <li>TEXT         → (none)</li>
     * </ul>
     * @param type the column type
     * @return array of valid units; never {@code null}
     */
    public static Unit[] getValidUnits(Type type) {
        if (type == null) return TIME_UNITS;
        switch (type) {
            case TIME:
            case MARKS:  return TIME_UNITS;
            case CURVE:  return CURVE_UNITS;
            case TEXT:   return TEXT_UNITS;
            default:     return TIME_UNITS;
        }
    }

    /**
     * Returns the default {@link Unit} for the given {@link Type}.
     * @param type the column type
     * @return a sensible default unit
     */
    public static Unit getDefaultUnit(Type type) {
        if (type == null) return Unit.SECONDS;
        switch (type) {
            case TIME:
            case MARKS:  return Unit.SECONDS;
            case CURVE:  return Unit.HZ;
            case TEXT:   return null;
            default:     return Unit.SECONDS;
        }
    }

    private String           name;
    private Type             type;
    private Unit             unit;
    private final ArrayList<Double> values = new ArrayList<>();     // the actual column data

    /**
     * constructor
     * @param name display name of the column
     * @param type the role this column plays (TIME, CURVE, MARKS, TEXT)
     * @param unit the physical unit of the values; if null or invalid for the given type, a default is chosen
     */
    public AnnotationLine(String name, Type type, Unit unit) {
        this.name = (name != null && !name.isEmpty()) ? name : "Column";
        this.type = (type != null) ? type : Type.CURVE;
        // ensure the unit is valid for the chosen type
        this.unit = isUnitValidForType(unit, this.type) ? unit : getDefaultUnit(this.type);
    }

    /** Returns true when {@code unit} is among the valid units for {@code type}. */
    private static boolean isUnitValidForType(Unit unit, Type type) {
        if (unit == null) return false;
        for (Unit u : getValidUnits(type))
            if (u == unit) return true;
        return false;
    }

    /**
     * return the display name
     * @return
     */
    public String getName()         { return this.name; }

    /**
     * set the display name. If the given name is null or empty, a default name is used.
     * @param n
     */
    public void   setName(String n) { this.name = (n != null && !n.isEmpty()) ? n : getType().toString(); }

    /**
     * return the type of this line
     * @return
     */
    public Type getType()       { return this.type; }
    /**
     * set the type of this line. If the given type is null, the type remains unchanged.
     * If the current unit is not valid for the new type, the unit is reset to the default for the new type.
     * @param t
     */
    public void setType(Type t) {
        if (t == null) return;
        this.type = t;
        if (!isUnitValidForType(this.unit, this.type))
            this.unit = getDefaultUnit(this.type);
    }

    /**
     * return the unit of the values in this line
     * @return
     */
    public Unit getUnit()       { return this.unit; }
    /**
     * set the unit of the values in this line. If the given unit is null, the unit remains unchanged.
     * @param u
     */
    public void setUnit(Unit u) { if(u == null) return; this.unit = u; }


    /**
     * Append a value to this column.
     * @param value the value to add
     */
    public void addValue(double value) {
        this.values.add(value);
    }

    /**
     * Get the value at a specific row index.
     * @param index row index
     * @return the value
     */
    public double getValue(int index) {
        return this.values.get(index);
    }

    /**
     * Replace all values in this column.
     * @param values the new values
     */
    public void setValues(ArrayList<Double> values) {
        this.values.clear();
        if (values != null)
            this.values.addAll(values);
    }

    /**
     * Get all values in this column.
     * @return the list of values
     */
    public ArrayList<Double> getValues() {
        return this.values;
    }

    /**
     * Get the number of values (= rows) in this column.
     * @return row count
     */
    public int size() {
        return this.values.size();
    }

    /**
     * String representation of this line.
     * @return a string describing this line
     */
    @Override
    public String toString() { return this.name + " [" + this.type + ", " + this.unit + "]"; }
}
