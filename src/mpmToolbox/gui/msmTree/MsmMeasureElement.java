package mpmToolbox.gui.msmTree;

import nu.xom.Attribute;
import nu.xom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * A synthetic {@link Element} subclass that represents a measure node in the MSM tree.
 * Because {@link MsmTreeNode} stores user objects as {@link nu.xom.Node},
 * this class extends {@link Element} so it can be stored there directly.
 * Used in {@link mpmToolbox.gui.Settings.MeasureDisplayMode#MEASURE_NODE} display mode.
 * @author Axel Berndt
 */
public class MsmMeasureElement extends Element {
    /** 1-based measure number */
    public final int measureNumber;

    /** The score child elements (notes, rests, …) that fall into this measure */
    public final List<Element> scoreElements;

    /**
     * constructor
     * @param measureNumber 1-based measure number
     */
    public MsmMeasureElement(int measureNumber) {
        super("measure");
        this.measureNumber = measureNumber;
        this.scoreElements = new ArrayList<>();
        this.addAttribute(new Attribute("number", String.valueOf(measureNumber)));
    }
}

