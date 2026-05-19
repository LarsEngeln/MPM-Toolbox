package mpmToolbox.gui;

import meico.mei.Helper;
import meico.msm.Msm;
import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Elements;

/**
 * Utility class for computing measure (bar) numbers from MSM tick dates.
 * @author Lars Engeln
 */
public class MeasureNumberLookup {

    /**
     * Compute the measure number for a given MSM tick date using the timeSignatureMap.
     * @param msmTicks    the tick date in MSM format
     * @param tsMap       the global timeSignatureMap element from the MSM, may be null
     * @param ppq         pulses per quarter note of the MSM
     * @return measure number
     */
    public static int getMeasureNumber(double msmTicks, Element tsMap, int ppq) {
        if (msmTicks < 0.0)
            return 1;

        int    measureNumber  = 1;
        double segmentStart   = 0.0;
        double    numerator      = 4;
        double    denominator    = 4;

        if (tsMap != null) {
            Elements children = tsMap.getChildElements();
            for (int i = 0; i < children.size(); i++) {
                Element ts      = children.get(i);
                String  dateStr = Helper.getAttributeValue("date", ts);
                if (dateStr.isEmpty()) continue;

                double markerDate = Double.parseDouble(dateStr);
                if (markerDate > msmTicks)
                    break;

                // count full measures from segmentStart to this marker
                double ticksPerMeasure = ppq * 4.0 * numerator / denominator;
                if (ticksPerMeasure > 0)
                    measureNumber += (int) Math.floor((markerDate - segmentStart) / ticksPerMeasure);

                segmentStart = markerDate;
                String numStr = Helper.getAttributeValue("numerator",   ts);
                String denStr = Helper.getAttributeValue("denominator", ts);
                if (!numStr.isEmpty()) numerator   = Double.parseDouble(numStr);
                if (!denStr.isEmpty()) denominator = Double.parseDouble(denStr);
            }
        }

        // count remaining measures from last segment start to msmTicks
        double ticksPerMeasure = ppq * 4.0 * numerator / denominator;
        if (ticksPerMeasure > 0)
            measureNumber += (int) Math.floor((msmTicks - segmentStart) / ticksPerMeasure);

        return measureNumber;
    }

    /**
     * Get the MSM tick date for an MPM map entry element.
     * Tries to resolve a noteid reference to an MSM note first.
     * Falls back to the date attribute value, scaling by PPQ ratio if necessary.
     * @param mpmElement the MPM map entry element
     * @param msm        the MSM object
     * @param mpmPpq     pulses per quarter note of the MPM performance
     * @return the tick date in MSM ticks
     */
    public static double getMsmTickDate(Element mpmElement, Msm msm, int mpmPpq) {
        if (mpmElement == null || msm == null)
            return 0.0;

        // try to resolve via noteid attribute (#xmlId -> MSM note -> date)
        Attribute noteidAttr = mpmElement.getAttribute("noteid");
        if (noteidAttr != null) {
            String id = noteidAttr.getValue();
            if (id.startsWith("#")) id = id.substring(1);
            Element note = findMsmElementById(id, msm);
            if (note != null) {
                String dateStr = Helper.getAttributeValue("date", note);
                if (!dateStr.isEmpty())
                    return Double.parseDouble(dateStr);
            }
        }

        // fallback: use date attribute with PPQ scaling
        String dateStr = Helper.getAttributeValue("date", mpmElement);
        if (dateStr.isEmpty())
            return 0.0;

        double mpmTicks = Double.parseDouble(dateStr);
        int    msmPpq   = msm.getPPQ();

        if (mpmPpq <= 0 || msmPpq <= 0 || mpmPpq == msmPpq)
            return mpmTicks;

        return mpmTicks * msmPpq / (double) mpmPpq;
    }

    /**
     * Return the global timeSignatureMap element from the MSM.
     * @param msm the MSM object
     * @return the timeSignatureMap element, or null
     */
    public static Element getTimeSignatureMap(Msm msm) {
        if (msm == null) return null;
        Element global = msm.getRootElement().getFirstChildElement("global");
        if (global == null) return null;
        Element dated = global.getFirstChildElement("dated");
        if (dated == null) return null;
        return dated.getFirstChildElement("timeSignatureMap");
    }

    /**
     * Find an MSM element (note, rest, …) by its xml:id across all parts.
     * @param id  the xml:id value (without leading #)
     * @param msm the MSM object
     * @return the found element, or null
     */
    private static Element findMsmElementById(String id, Msm msm) {
        if (id == null || id.isEmpty() || msm == null)
            return null;

        Elements parts = msm.getRootElement().getChildElements("part");
        for (int p = 0; p < parts.size(); p++) {
            Element dated = parts.get(p).getFirstChildElement("dated");
            if (dated == null) continue;
            Element score = dated.getFirstChildElement("score");
            if (score == null) continue;
            Elements scoreChildren = score.getChildElements();
            for (int n = 0; n < scoreChildren.size(); n++) {
                Element elt   = scoreChildren.get(n);
                Attribute xmlId = elt.getAttribute("id", "http://www.w3.org/XML/1998/namespace");
                if (xmlId != null && xmlId.getValue().equals(id))
                    return elt;
            }
        }
        return null;
    }
}

