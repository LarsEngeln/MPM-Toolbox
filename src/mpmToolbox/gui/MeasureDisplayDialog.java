package mpmToolbox.gui;

import com.alee.laf.button.WebButton;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.radiobutton.WebRadioButton;
import com.alee.laf.separator.WebSeparator;
import com.alee.laf.window.WebDialog;
import com.alee.laf.window.WebFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * A settings dialog for the MPM Toolbox.
 * Currently exposes the measure/bar-number display mode for the MSM and MPM trees.
 * @author Lars Engeln
 */
public class MeasureDisplayDialog extends WebDialog<MeasureDisplayDialog> {

    private Settings.MeasureDisplayMode selectedMsmMode = Settings.msmMeasureDisplayMode;
    private Settings.MeasureDisplayMode selectedMpmMode = Settings.mpmMeasureDisplayMode;
    private final ProjectPane projectPane;

    /**
     * Constructor
     * @param parent     the owning MpmToolbox (used to centre the dialog over its frame)
     * @param projectPane the active ProjectPane (may be null when no project is loaded)
     */
    public MeasureDisplayDialog(WebFrame parent, ProjectPane projectPane) {
        super();
        this.setTitle("Settings");
        this.setModal(true);
        this.setIconImages(Settings.getIcons(null));
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.projectPane = projectPane;
        this.buildGui();
        this.pack();
        this.setLocationRelativeTo(parent);
        this.setVisible(true);
    }

    /** Build the dialog content. */
    private void buildGui() {
        GridBagLayout layout = new GridBagLayout();
        WebPanel content = new WebPanel(layout);
        content.setPadding(Settings.paddingInDialogs);

        int row = 0;
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 4, 4, 4);

        // ── Info ────────────────────────────────────────────────────────
        WebLabel infoLabel = new WebLabel("<html><body style='width:320px'><i>Measures are not explicitly stored in MSM (the information is lost while converting MEI to MSM), the measure numbers are derived via the time-signature maps. Thereby, structural measures are always start counting with '1', although semantic measure of the visual score could be different!</i></body></html>");
        infoLabel.setForeground(infoLabel.getForeground().darker());
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(infoLabel, c);

        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(new WebSeparator(), c);

        // ── MSM Tree Section ────────────────────────────────────────────
        WebLabel msmLabel = new WebLabel("Measure display in MSM tree:");
        msmLabel.setFont(msmLabel.getFont().deriveFont(Font.BOLD));
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(msmLabel, c);

        ButtonGroup msmGroup = new ButtonGroup();

        WebRadioButton msmNone = new WebRadioButton("None (default – no measure info)");
        msmNone.setSelected(Settings.msmMeasureDisplayMode == Settings.MeasureDisplayMode.NONE);
        msmNone.addActionListener(e -> this.selectedMsmMode = Settings.MeasureDisplayMode.NONE);
        msmGroup.add(msmNone);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(msmNone, c);

        WebRadioButton msmPrefix = new WebRadioButton("Prefix – e.g. \"[42] c4\"");
        msmPrefix.setSelected(Settings.msmMeasureDisplayMode == Settings.MeasureDisplayMode.PREFIX);
        msmPrefix.addActionListener(e -> this.selectedMsmMode = Settings.MeasureDisplayMode.PREFIX);
        msmGroup.add(msmPrefix);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(msmPrefix, c);

        WebRadioButton msmMeasureNode = new WebRadioButton("Measure nodes – a virtual group structure that is not part of the MSM");
        msmMeasureNode.setSelected(Settings.msmMeasureDisplayMode == Settings.MeasureDisplayMode.MEASURE_NODE);
        msmMeasureNode.addActionListener(e -> this.selectedMsmMode = Settings.MeasureDisplayMode.MEASURE_NODE);
        msmGroup.add(msmMeasureNode);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(msmMeasureNode, c);

        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(new WebSeparator(), c);

        // ── MPM Tree Section ────────────────────────────────────────────
        WebLabel mpmLabel = new WebLabel("Measure display in MPM tree:");
        mpmLabel.setFont(mpmLabel.getFont().deriveFont(Font.BOLD));
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(mpmLabel, c);

        ButtonGroup mpmGroup = new ButtonGroup();

        WebRadioButton mpmNone = new WebRadioButton("None (default – no measure info)");
        mpmNone.setSelected(Settings.mpmMeasureDisplayMode == Settings.MeasureDisplayMode.NONE);
        mpmNone.addActionListener(e -> this.selectedMpmMode = Settings.MeasureDisplayMode.NONE);
        mpmGroup.add(mpmNone);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(mpmNone, c);

        WebRadioButton mpmPrefix = new WebRadioButton("Prefix – e.g. \"[42] c4\"");
        mpmPrefix.setSelected(Settings.mpmMeasureDisplayMode == Settings.MeasureDisplayMode.PREFIX);
        mpmPrefix.addActionListener(e -> this.selectedMpmMode = Settings.MeasureDisplayMode.PREFIX);
        mpmGroup.add(mpmPrefix);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(mpmPrefix, c);

        WebRadioButton mpmMeasureNode = new WebRadioButton("Measure nodes – a virtual group structure that is not part of the MPM");
        mpmMeasureNode.setSelected(Settings.mpmMeasureDisplayMode == Settings.MeasureDisplayMode.MEASURE_NODE);
        mpmMeasureNode.addActionListener(e -> this.selectedMpmMode = Settings.MeasureDisplayMode.MEASURE_NODE);
        mpmGroup.add(mpmMeasureNode);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(mpmMeasureNode, c);

        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(new WebSeparator(), c);

        // ── OK / Cancel ─────────────────────────────────────────────────
        WebButton okButton = new WebButton("OK");
        okButton.setMnemonic(KeyEvent.VK_ENTER);
        okButton.addActionListener(this::onOk);

        WebButton cancelButton = new WebButton("Cancel");
        cancelButton.addActionListener(e -> this.dispose());

        c.gridwidth = 1;
        c.weightx = 0.5;
        c.gridx = 0; c.gridy = row;
        content.add(okButton, c);

        c.gridx = 1; c.gridy = row;
        content.add(cancelButton, c);

        this.setContentPane(content);

        // Close, same as Cancel
        this.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { dispose(); }
        });

        // ESC -> Cancel
        this.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        this.getRootPane().getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { dispose(); }
        });

        // ENTER -> OK
        this.getRootPane().setDefaultButton(okButton);
    }

    /** Apply changes and close. */
    private void onOk(ActionEvent e) {
        Settings.msmMeasureDisplayMode = this.selectedMsmMode;
        Settings.mpmMeasureDisplayMode = this.selectedMpmMode;
        Settings.writeSettingsFile();
        if (this.projectPane != null)
            this.projectPane.refreshTreeDisplayMode();
        this.dispose();
    }
}
