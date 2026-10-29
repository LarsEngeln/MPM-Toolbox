package mpmToolbox.gui;

import com.alee.api.data.Orientation;
import com.alee.extended.split.MultiSplitState;
import com.alee.extended.split.MultiSplitViewState;
import com.alee.extended.split.WebMultiSplitPane;

import javax.swing.SwingUtilities;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores the view sizes of a WebMultiSplitPane as relative fractions ("0.3,0.5,0.2") and restores them
 * as soon as the pane has a real size.
 */
public class SplitPaneStateHelper {

    /**
     * @return the fractions as comma separated string or null if they cannot be determined (e.g. pane never displayed)
     */
    public static String capture(WebMultiSplitPane pane) {
        List<java.awt.Component> views = pane.getViewComponents();       // actual sizes; the view states hold -1 for views that were never resized
        boolean vertical = pane.getOrientation() == Orientation.vertical;
        double sum = 0.0;
        for (java.awt.Component c : views)
            sum += vertical ? c.getHeight() : c.getWidth();
        if ((views.isEmpty()) || (sum <= 0.0))
            return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < views.size(); ++i) {
            if (i > 0)
                sb.append(',');
            sb.append((vertical ? views.get(i).getHeight() : views.get(i).getWidth()) / sum);
        }
        return sb.toString();
    }

    /**
     * apply the fractions once the pane has a size
     */
    public static void apply(WebMultiSplitPane pane, String fractions) {
        final double[] f;
        try {
            String[] parts = fractions.split(",");
            f = new double[parts.length];
            for (int i = 0; i < parts.length; ++i)
                f[i] = Double.parseDouble(parts[i].trim());
        } catch (Exception e) {
            return;
        }

        final boolean[] done = {false};
        final ComponentAdapter[] listener = new ComponentAdapter[1];
        Runnable tryApply = () -> {
            if (done[0] || !applyNow(pane, f))
                return;
            done[0] = true;
            pane.removeComponentListener(listener[0]);
        };
        listener[0] = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(tryApply);                // after the layout pass that caused the resize
            }

            @Override
            public void componentShown(ComponentEvent e) {
                SwingUtilities.invokeLater(tryApply);
            }
        };
        pane.addComponentListener(listener[0]);
        SwingUtilities.invokeLater(tryApply);
    }

    private static boolean applyNow(WebMultiSplitPane pane, double[] f) {
        int length = (pane.getOrientation() == Orientation.vertical) ? pane.getHeight() : pane.getWidth();
        MultiSplitState current = pane.getMultiSplitState();
        if (current == null)
            return false;
        List<MultiSplitViewState> currentStates = current.states();
        int n = currentStates.size();
        if ((length <= 0) || (n != f.length))
            return false;

        double sum = 0.0;
        for (double v : f)
            sum += v;
        if (sum <= 0.0)
            return false;

        double available = length - (n - 1) * pane.getDividerSize();
        if (available <= 0)
            return false;

        List<MultiSplitViewState> states = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < n; ++i) {
            MultiSplitViewState s = new MultiSplitViewState();
            int size = (i == n - 1) ? Math.max(0, (int) Math.round(available) - used) : (int) Math.round(f[i] / sum * available);
            used += size;
            s.setSize(size);
            s.setExpanded(false);
            states.add(s);
        }
        MultiSplitState state = new MultiSplitState();
        state.setStates(states);
        pane.setMultiSplitState(state);
        pane.revalidate();
        pane.repaint();
        return true;
    }
}
