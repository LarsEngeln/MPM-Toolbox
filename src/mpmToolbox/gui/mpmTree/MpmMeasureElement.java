package mpmToolbox.gui.mpmTree;

import nu.xom.Attribute;
import nu.xom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * A virtual Element subclass that represents a measure node in the MPM tree.
 * @author Lars Engeln
 */
public class MpmMeasureElement extends Element {
    /** measure number */
    public final int measureNumber;

    /** The MPM map entry elements that fall into this measure */
    public final List<Element> elements;

    /**
     * constructor
     * @param measureNumber measure number
     */
    public MpmMeasureElement(int measureNumber) {
        super("measure");
        this.measureNumber = measureNumber;
        this.elements      = new ArrayList<>();
        this.addAttribute(new Attribute("number", String.valueOf(measureNumber)));
    }
}

