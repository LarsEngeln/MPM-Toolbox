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
public class SettingsDialog extends WebDialog<SettingsDialog> {

    private Settings.MeasureDisplayMode selectedMode = Settings.measureDisplayMode;
    private final ProjectPane projectPane;

    /**
     * Constructor
     * @param parent     the owning MpmToolbox (used to centre the dialog over its frame)
     * @param projectPane the active ProjectPane (may be null when no project is loaded)
     */
    public SettingsDialog(WebFrame parent, ProjectPane projectPane) {
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

        // Section header
        WebLabel sectionLabel = new WebLabel("Measure display in MSM / MPM trees:");
        sectionLabel.setFont(sectionLabel.getFont().deriveFont(Font.BOLD));
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(sectionLabel, c);

        // Measure display
        ButtonGroup group = new ButtonGroup();

        WebRadioButton rbNone = new WebRadioButton("None (default – no measure info)");
        rbNone.setSelected(Settings.measureDisplayMode == Settings.MeasureDisplayMode.NONE);
        rbNone.addActionListener(e -> this.selectedMode = Settings.MeasureDisplayMode.NONE);
        group.add(rbNone);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(rbNone, c);

        WebRadioButton rbPrefix = new WebRadioButton("Prefix  – e.g. \"[42] c4\"");
        rbPrefix.setSelected(Settings.measureDisplayMode == Settings.MeasureDisplayMode.PREFIX);
        rbPrefix.addActionListener(e -> this.selectedMode = Settings.MeasureDisplayMode.PREFIX);
        group.add(rbPrefix);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(rbPrefix, c);

        WebRadioButton rbMeasureNode = new WebRadioButton("Measure nodes – group entries under bar nodes");
        rbMeasureNode.setSelected(Settings.measureDisplayMode == Settings.MeasureDisplayMode.MEASURE_NODE);
        rbMeasureNode.addActionListener(e -> this.selectedMode = Settings.MeasureDisplayMode.MEASURE_NODE);
        group.add(rbMeasureNode);
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(rbMeasureNode, c);


        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        content.add(new WebSeparator(), c);


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
        Settings.measureDisplayMode = this.selectedMode;
        Settings.writeSettingsFile();
        if (this.projectPane != null)
            this.projectPane.refreshTreeDisplayMode();
        this.dispose();
    }
}


