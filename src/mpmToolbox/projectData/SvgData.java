package mpmToolbox.projectData;

import com.kitfox.svg.RenderableElement;
import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGElement;
import com.kitfox.svg.SVGException;
import com.kitfox.svg.SVGUniverse;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.ParsingException;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Data model for an SVG file used in MPM Toolbox.
 * Holds both the nu.xom XML representation (for tree display) and
 * the svg-salamander SVGDiagram (for rendering as overlay).
 *
 * @author Lars Engeln
 */
public class SvgData {

    private final File file;
    private final Element xmlRoot;      // nu.xom root element for tree display
    private final SVGDiagram diagram;   // svg-salamander diagram for rendering
    private Element highlightedElement = null;  // the currently highlighted XOM element (tree ↔ score sync)

    /**
     * Constructor – parses the given SVG file.
     *
     * @param file the SVG file to load
     * @throws IOException      on I/O errors
     * @throws ParsingException if the SVG is not well-formed XML
     */
    public SvgData(File file) throws IOException, ParsingException {
        this.file = file;

        // parse XML via nu.xom for the tree view
        Builder builder = new Builder();
        Document doc = builder.build(file);
        this.xmlRoot = doc.getRootElement();

        // parse via svg-salamander for rendering
        SVGUniverse universe = new SVGUniverse();
        URI svgUri = universe.loadSVG(file.toURI().toURL());
        this.diagram = universe.getDiagram(svgUri);
        if (this.diagram != null) {
            this.diagram.setIgnoringClipHeuristic(true);
        }
    }

    /**
     * The SVG source file.
     * @return file
     */
    public File getFile() {
        return this.file;
    }

    /**
     * The nu.xom root element of the SVG document (for tree display).
     * @return root element
     */
    public Element getXmlRoot() {
        return this.xmlRoot;
    }

    /**
     * The svg-salamander diagram (for rendering as overlay).
     * @return diagram, may be {@code null} if parsing failed
     */
    public SVGDiagram getDiagram() {
        return this.diagram;
    }

    /**
     * Returns the display name (file name without path).
     * @return name
     */
    public String getName() {
        return this.file.getName();
    }

    /**
     * Render this SVG scaled to the given target width and height using Graphics2D.
     * The SVG is scaled uniformly to fit within the target rectangle.
     *
     * @param g2     the Graphics2D context (already transformed for the score coordinate system)
     * @param targetWidth  the target width in pixels
     * @param targetHeight the target height in pixels
     */
    public void render(Graphics2D g2, int targetWidth, int targetHeight) {
        if (this.diagram == null)
            return;

        float svgW = this.diagram.getWidth();
        float svgH = this.diagram.getHeight();
        if (svgW <= 0 || svgH <= 0)
            return;

        double scaleX = targetWidth / svgW;
        double scaleY = targetHeight / svgH;

        Graphics2D g2Copy = (Graphics2D) g2.create();
        try {
            g2Copy.scale(scaleX, scaleY);
            this.diagram.render(g2Copy);
        } catch (SVGException e) {
            e.printStackTrace();
        } finally {
            g2Copy.dispose();
        }
    }

    /**
     * Get the currently highlighted XOM element (used for tree ↔ score synchronisation).
     */
    public Element getHighlightedElement() {
        return this.highlightedElement;
    }

    /**
     * Set the currently highlighted XOM element.
     * Pass {@code null} to clear the highlight.
     */
    public void setHighlightedElement(Element element) {
        this.highlightedElement = element;
    }

    /**
     * Returns the bounding rectangle of the SVG element with the given {@code id},
     * scaled to image-pixel coordinates (using {@code imageW}/{@code imageH}).
     * Returns {@code null} if the element cannot be found or has no bounding box.
     */
    public Rectangle2D getBoundsInImageSpace(String id, int imageW, int imageH) {
        if (this.diagram == null || id == null || id.isEmpty())
            return null;
        float svgW = this.diagram.getWidth();
        float svgH = this.diagram.getHeight();
        if (svgW <= 0 || svgH <= 0)
            return null;
        SVGElement svgEl = this.diagram.getElement(id);
        if (svgEl == null || !(svgEl instanceof RenderableElement))
            return null;
        try {
            Rectangle2D bounds = ((RenderableElement) svgEl).getBoundingBox();
            if (bounds == null)
                return null;
            double scaleX = imageW / svgW;
            double scaleY = imageH / svgH;
            return new Rectangle2D.Double(
                    bounds.getX()      * scaleX,
                    bounds.getY()      * scaleY,
                    bounds.getWidth()  * scaleX,
                    bounds.getHeight() * scaleY);
        } catch (SVGException e) {
            return null;
        }
    }

    /**
     * Hit-test at position ({@code imageX}, {@code imageY}) in image-pixel coordinates.
     * Returns the topmost XOM {@link Element} whose SVG counterpart covers that point,
     * or {@code null} if none is found.
     *
     * @param imageX  x position in image pixels
     * @param imageY  y position in image pixels
     * @param imageW  total image width in pixels
     * @param imageH  total image height in pixels
     */
    public Element pickElementAt(float imageX, float imageY, int imageW, int imageH) {
        if (this.diagram == null)
            return null;
        float svgW = this.diagram.getWidth();
        float svgH = this.diagram.getHeight();
        if (svgW <= 0 || svgH <= 0)
            return null;

        float svgX = imageX * svgW / imageW;
        float svgY = imageY * svgH / imageH;

        List<List<SVGElement>> retVec = new ArrayList<>();
        try {
            this.diagram.pick(new Point2D.Float(svgX, svgY), false, retVec);
        } catch (SVGException e) {
            return null;
        }

        // find the deepest element with an id that matches a XOM element
        for (int i = retVec.size() - 1; i >= 0; i--) {
            List<SVGElement> path = retVec.get(i);
            for (int j = path.size() - 1; j >= 0; j--) {
                SVGElement svgEl = path.get(j);
                String id = svgEl.getId();
                if (id != null && !id.isEmpty()) {
                    Element found = findElementById(this.xmlRoot, id);
                    if (found != null)
                        return found;
                }
            }
        }
        return null;
    }

    /**
     * Recursively search the XOM tree for an element with the given {@code id} attribute.
     */
    private static Element findElementById(Element root, String id) {
        if (id.equals(root.getAttributeValue("id")))
            return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            if (root.getChild(i) instanceof Element) {
                Element found = findElementById((Element) root.getChild(i), id);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    /**
     * Human-readable representation.
     * @return file name
     */
    @Override
    public String toString() {
        return this.getName();
    }
}


