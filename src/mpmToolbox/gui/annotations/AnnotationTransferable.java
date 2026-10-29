package mpmToolbox.gui.annotations;

import mpmToolbox.projectData.annotations.AnnotationData;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

/**
 * Carries an annotation from the annotation tree to the panes (within the same JVM).
 * @author Lars Engeln
 */
public class AnnotationTransferable implements Transferable {
    public static final DataFlavor FLAVOR = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + AnnotationData.class.getName(), "Annotation");

    private final AnnotationData annotation;

    public AnnotationTransferable(AnnotationData annotation) {
        this.annotation = annotation;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{FLAVOR};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return FLAVOR.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) {
        return this.annotation;
    }
}
