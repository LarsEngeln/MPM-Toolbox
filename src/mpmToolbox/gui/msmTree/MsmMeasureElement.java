package mpmToolbox.gui.msmTree;

import nu.xom.Attribute;
import nu.xom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * A virtual ELement subclass that represents a measure node in the MSM tree.
 * @author Lars Engeln
 */
public class MsmMeasureElement extends Element {
    /** measure number */
    public final int measureNumber;

    /** The score child elements (notes, rests, …) that fall into this measure */
    public final List<Element> scoreElements;

    /**
     * constructor
     * @param measureNumber measure number
     */
    public MsmMeasureElement(int measureNumber) {
        super("measure");
        this.measureNumber = measureNumber;
        this.scoreElements = new ArrayList<>();
        this.addAttribute(new Attribute("number", String.valueOf(measureNumber)));
    }
}

