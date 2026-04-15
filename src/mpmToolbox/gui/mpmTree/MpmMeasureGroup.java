package mpmToolbox.gui.mpmTree;

import nu.xom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * A wrapper that groups MPM map entries belonging to the same measure.
 * Used in {@link mpmToolbox.gui.Settings.MeasureDisplayMode#MEASURE_NODE} display mode.
 * @author Lars Engeln
 */
public class MpmMeasureGroup {
    /** measure number */
    public final int measureNumber;

    /** The MPM map entry elements that fall into this measure */
    public final List<Element> elements;

    /**
     * constructor
     * @param measureNumber measure number
     */
    public MpmMeasureGroup(int measureNumber) {
        this.measureNumber = measureNumber;
        this.elements      = new ArrayList<>();
    }
}

