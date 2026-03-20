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

    private String           name;
    private Type             type;
    private Unit             unit;
    private final ArrayList<Double> values = new ArrayList<>();     // the actual column data

    /**
     * constructor
     * @param name display name of the column
     * @param type the role this column plays (TIME, CURVE, MARKS)
     * @param unit the physical unit of the values
     */
    public AnnotationLine(String name, Type type, Unit unit) {
        this.name = (name != null && !name.isEmpty()) ? name : "Column";
        this.type = (type != null) ? type : Type.CURVE;
        this.unit = (unit != null) ? unit : Unit.SECONDS;
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
     * @param t
     */
    public void setType(Type t) { if(t == null) return; this.type = t; }

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
