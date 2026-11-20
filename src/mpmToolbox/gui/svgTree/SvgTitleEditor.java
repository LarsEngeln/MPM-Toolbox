package mpmToolbox.gui.svgTree;

import com.alee.laf.label.WebLabel;
import com.alee.laf.text.WebTextField;
import mpmToolbox.gui.Settings;
import mpmToolbox.gui.mpmEditingTools.editDialogs.EditDialog;

import java.awt.GridBagConstraints;

/**
 * Dialog for editing the title of an SVG element.
 */
public class SvgTitleEditor extends EditDialog<String> {
    private WebTextField title;

    public SvgTitleEditor() {
        super("Edit SVG Title");
    }

    @Override
    public void makeContentPanel() {
        WebLabel label = new WebLabel("Title:");
        label.setHorizontalAlignment(WebLabel.RIGHT);
        label.setPadding(Settings.paddingInDialogs);
        this.addToContentPanel(label, 0, 0, 1, 1, 0.0, 1.0, 0, 0, GridBagConstraints.BOTH);

        this.title = new WebTextField();
        this.title.setMinimumWidth(getFontMetrics(this.title.getFont()).stringWidth("wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww"));
        this.title.setPadding(Settings.paddingInDialogs);
        this.addToContentPanel(this.title, 1, 0, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH);
    }

    @Override
    public String edit(String input) {
        this.title.setText(input == null ? "" : input);
        this.title.selectAll();
        this.setVisible(true);
        return this.isOk() ? this.title.getText() : null;
    }
}
