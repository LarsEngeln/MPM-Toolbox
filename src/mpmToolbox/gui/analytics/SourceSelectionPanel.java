package mpmToolbox.gui.analytics;

import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import mpmToolbox.gui.Settings;
import mpmToolbox.gui.analytics.AnalyticsDocumentData.SourceEntry;

import javax.swing.*;
import java.awt.*;

/**
 * Displays audio files and performances as selectable tiles.
 */
public class SourceSelectionPanel extends WebPanel {
    private final AnalyticsDocumentData parent;
    private final WebPanel tilePanel = new WebPanel();

    public SourceSelectionPanel(AnalyticsDocumentData parent) {
        super(new BorderLayout());
        this.parent = parent;

        this.tilePanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
        this.add(this.tilePanel, BorderLayout.CENTER);
        this.setPreferredSize(new Dimension(0, 120));
        this.rebuildTiles();
    }

    protected void rebuildTiles() {
        this.tilePanel.removeAll();

        for (SourceEntry source : this.parent.getAudioSources())
            this.tilePanel.add(this.makeTile(source));

        for (SourceEntry source : this.parent.getPerformanceSources())
            this.tilePanel.add(this.makeTile(source));

        this.tilePanel.revalidate();
        this.tilePanel.repaint();
    }

    private WebPanel makeTile(SourceEntry source) {
        WebPanel tile = new WebPanel(new BorderLayout(5, 5));
        tile.setPreferredSize(new Dimension(140, 80));
        tile.setBorder(BorderFactory.createLineBorder(source.getColor(), 2));
        tile.setBackground(new Color(40, 40, 40));

        WebLabel nameLabel = new WebLabel(source.getName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 11f));
        nameLabel.setForeground(source.getColor());
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        tile.add(nameLabel, BorderLayout.NORTH);

        JCheckBox checkBox = new JCheckBox("Active", source.isEnabled());
        checkBox.setOpaque(false);
        checkBox.setForeground(Color.WHITE);
        checkBox.setHorizontalAlignment(SwingConstants.CENTER);
        checkBox.addActionListener(actionEvent -> {
            source.setEnabled(checkBox.isSelected());
            this.parent.getVolumePanel().repaint();
        });
        tile.add(checkBox, BorderLayout.CENTER);

        return tile;
    }
}
