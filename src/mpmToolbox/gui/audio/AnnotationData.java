package mpmToolbox.gui.audio;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Holds annotation data loaded from a CSV file.
 * Each object has a name and a list of (milliseconds, value) entries.
 * @author Lars Engeln
 * */
public class AnnotationData {
    private String name;
    private final ArrayList<AnnotationPanel.AnnotationEntry> entries;

    /**
     * constructor
     * @param name the display name of this annotation dataset
     * @param entries the annotation entries
     */
    public AnnotationData(String name, ArrayList<AnnotationPanel.AnnotationEntry> entries) {
        this.name = (name != null && !name.isEmpty()) ? name : "Annotation";
        this.entries = (entries != null) ? entries : new ArrayList<>();
        this.entries.sort(Comparator.comparingDouble(e -> e.milliseconds));
    }

    /**
     * get the name of this annotation dataset
     * @return the name
     */
    public String getName() {
        return this.name;
    }

    /**
     * set the name of this annotation dataset
     * @param name the new name
     */
    public void setName(String name) {
        this.name = (name != null && !name.isEmpty()) ? name : "Annotation";
    }

    /**
     * get the annotation entries
     * @return the entries
     */
    public ArrayList<AnnotationPanel.AnnotationEntry> getEntries() {
        return this.entries;
    }

    /**
     * Replace all entries in this dataset with new ones.
     * @param newEntries the new entries
     */
    public void replaceEntries(ArrayList<AnnotationPanel.AnnotationEntry> newEntries) {
        this.entries.clear();
        if (newEntries != null)
            this.entries.addAll(newEntries);
        this.entries.sort(Comparator.comparingDouble(e -> e.milliseconds));
    }

    /**
     * returns the name for display purposes
     * @return the name
     */
    @Override
    public String toString() {
        return this.name;
    }
}

