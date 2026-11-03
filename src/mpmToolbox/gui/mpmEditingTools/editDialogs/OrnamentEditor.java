package mpmToolbox.gui.mpmEditingTools.editDialogs;

import com.alee.laf.button.WebToggleButton;
import com.alee.laf.grouping.GroupPane;
import com.alee.laf.label.WebLabel;
import com.alee.laf.spinner.WebSpinner;
import com.alee.laf.text.WebTextField;
import com.alee.extended.button.WebSplitButton;
import com.alee.laf.button.WebButton;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.menu.WebPopupMenu;
import meico.mei.Helper;
import meico.mpm.Mpm;
import meico.mpm.elements.Performance;
import meico.mpm.elements.maps.OrnamentationMap;
import meico.mpm.elements.maps.data.OrnamentData;
import meico.msm.Msm;
import mpmToolbox.gui.ProjectPane;
import mpmToolbox.gui.Settings;
import meico.midi.EventMaker;
import mpmToolbox.gui.mpmEditingTools.editDialogs.ornament.NoteMatrixComponent;
import mpmToolbox.supplementary.Tools;
import nu.xom.Element;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.math.RoundingMode;
import java.util.ArrayList;

/**
 * The ornament editor.
 * @author Lars Engeln
 */
public class OrnamentEditor extends EditDialog<OrnamentData> {
    private ProjectPane projectPane;
    private Msm msm;
    private Performance performance;
    private WebToggleButton ascendingPitchToggle;
    private WebToggleButton descendingPitchToggle;
    private WebToggleButton noteOrderToggle;
    private NoteMatrixComponent noteMatrix;
    private WebSpinner repetitions;
    private WebSpinner scale;
    private WebTextField noteId;
    private WebSplitButton noteIdChooser;
    private ArrayList<String> noteIds = new ArrayList<>();
    private OrnamentData currentOrnamentData = null;

    /**
     * constructor
     * @param map the map that gets or holds the ornament element
     */
    public OrnamentEditor(OrnamentationMap map, ProjectPane projectPane, Performance performance) {
        super("Edit Ornament", map);
        this.projectPane = projectPane;
        this.msm = projectPane.getMsm();
        this.performance = performance;
    }

    /**
     * the GUI
     */
    @Override
    public void makeContentPanel() {
        this.addDateInput(0);
        this.date.addChangeListener(changeEvent -> {
            this.fullNameRefUpdate(Mpm.ORNAMENTATION_STYLE);
            this.updateMsmDate();
            this.updateNoteList();
        });

        /////////////

        WebLabel noteIdLabel = new WebLabel("Note ID (optional):");
        noteIdLabel.setHorizontalAlignment(WebLabel.RIGHT);
        noteIdLabel.setPadding(Settings.paddingInDialogs);
        this.addToContentPanel(noteIdLabel, 0, 1, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH);

        this.noteId = new WebTextField();
        this.noteId.setMinimumWidth(getFontMetrics(this.noteId.getFont()).stringWidth("wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww"));
        this.noteId.setHorizontalAlignment(WebTextField.LEFT);
        this.noteId.setPadding(Settings.paddingInDialogs);
        this.noteId.setToolTip("Assign this ornament to a specific note at the date.\nLeave blank to use the first note at the date.");
        this.noteId.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {checkNoteId();}
            @Override
            public void removeUpdate(DocumentEvent e) {checkNoteId();}
            @Override
            public void changedUpdate(DocumentEvent e) {checkNoteId();}
        });
        this.addToContentPanel(this.noteId, 1, 1, 2, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH);

        this.noteIdChooser = new WebSplitButton("Choose");
        this.noteIdChooser.setHorizontalAlignment(WebButton.CENTER);
        this.noteIdChooser.setPadding(Settings.paddingInDialogs);
        this.addToContentPanel(this.noteIdChooser, 3, 1, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH);

        /////////////

        this.addNameRef("Select Ornament:", 2, true);

        // note.order
        WebLabel noteOrderLabel = new WebLabel("Note Order:");
        noteOrderLabel.setHorizontalAlignment(WebLabel.RIGHT);
        noteOrderLabel.setPadding(Settings.paddingInDialogs);
        noteOrderLabel.setToolTip("The order in which the notes are processed by the ornament.");
        this.addToContentPanel(noteOrderLabel, 0, 3, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH);

        this.noteMatrix = new NoteMatrixComponent();
        this.noteMatrix.setNotePlayer(this::playPitch);
        JScrollPane noteMatrixScrollPane = new JScrollPane(this.noteMatrix, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED) {
            private Dimension fittingSize = null;   // the size this pane had while no scrollbar was needed

            @Override
            public Dimension getMinimumSize() {     // a scroll pane's default minimum is tiny, so the GridBagLayout would squash the matrix as soon as the dialog is capped
                if (!this.getVerticalScrollBar().isVisible() && !this.getHorizontalScrollBar().isVisible())
                    this.fittingSize = new Dimension(this.getPreferredSize());      // keep the last size that showed everything
                return (this.fittingSize != null) ? new Dimension(this.fittingSize) : this.getPreferredSize();
            }
        };
        noteMatrixScrollPane.setBorder(BorderFactory.createEmptyBorder());
        noteMatrixScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        noteMatrixScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        noteMatrixScrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);    // repaint fully, the note pool is drawn sticky at the left edge
        this.addToContentPanel(noteMatrixScrollPane, 0, 4, 4, 1, 1.0, 100.0, 10, 0, GridBagConstraints.BOTH);   // high weighty: the matrix gets the space, other rows stay compact

        // repetitions
        WebLabel repetitionsLabel = new WebLabel("Repetitions:");
        repetitionsLabel.setHorizontalAlignment(WebLabel.RIGHT);
        repetitionsLabel.setPadding(Settings.paddingInDialogs * 2, Settings.paddingInDialogs, Settings.paddingInDialogs, Settings.paddingInDialogs);
        String repetitionsTip = "How often the repetition region is repeated. -1: as often as fits into the principal note's length.";
        repetitionsLabel.setToolTip(repetitionsTip);
        this.addToContentPanel(repetitionsLabel, 0, 5, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH);

        this.repetitions = new WebSpinner(new SpinnerNumberModel(1, -1, 999, 1));
        this.repetitions.setToolTip(repetitionsTip);
        this.repetitions.addChangeListener(e -> this.noteMatrix.setRepetitions((Integer) this.repetitions.getValue()));
        int repWidth = getFontMetrics(this.repetitions.getFont()).stringWidth("99999");
        this.repetitions.setMinimumWidth(repWidth);
        this.repetitions.setMaximumWidth(repWidth);
        this.addToContentPanel(this.repetitions, 1, 5, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH);
        GridBagConstraints repetitionsConstraints = this.contentPanelLayout.getConstraints(this.repetitions);
        repetitionsConstraints.insets = new Insets(Settings.paddingInDialogs, 0, 0, 0);
        this.contentPanelLayout.setConstraints(this.repetitions, repetitionsConstraints);

        this.ascendingPitchToggle = new WebToggleButton("Ascending Pitch");
        this.ascendingPitchToggle.setPadding(Settings.paddingInDialogs);
        this.ascendingPitchToggle.setToolTip("All the notes displayed are processed ordered by ascending pitch.");
        this.descendingPitchToggle = new WebToggleButton("Descending Pitch");
        this.descendingPitchToggle.setPadding(Settings.paddingInDialogs);
        this.descendingPitchToggle.setToolTip("All the notes displayed are processed ordered by descending pitch.");
        this.noteOrderToggle = new WebToggleButton("Specify Note Order");
        this.noteOrderToggle.setPadding(Settings.paddingInDialogs);
        this.noteOrderToggle.setToolTip("Activate the note(s) that take part in the ornament and adjust their order.");
        this.noteOrderToggle.addChangeListener(e -> {
            this.noteMatrix.setMatrixEnabled(this.noteOrderToggle.isSelected());
            this.repetitions.setEnabled(this.noteOrderToggle.isSelected());
        });
        GroupPane noteOrderMode = new GroupPane(this.ascendingPitchToggle, this.descendingPitchToggle, this.noteOrderToggle);
        this.addToContentPanel(noteOrderMode, 1, 3, 2, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH);

        // scale
        WebLabel scaleLabel = new WebLabel("Scale Dynamics Gradient:");
        scaleLabel.setHorizontalAlignment(WebLabel.RIGHT);
        scaleLabel.setPadding(Settings.paddingInDialogs);
        scaleLabel.setToolTip("The dynamics gradient is defined in [-1, 1] as part of the ornament's definition.\nHere it is scaled to actual loudness or MIDI velocity values.");
        this.addToContentPanel(scaleLabel, 0, 6, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH);

        this.scale = new WebSpinner(new SpinnerNumberModel(0.0, -99999999999999999.9, 99999999999999999.9, 1.0));
        int width = getFontMetrics(this.scale.getFont()).stringWidth("999.999.999.999.999");
        JSpinner.NumberEditor scaleEditor = (JSpinner.NumberEditor) this.scale.getEditor();
        scaleEditor.getFormat().setMaximumFractionDigits(10);
        scaleEditor.getFormat().setRoundingMode(RoundingMode.HALF_UP);
        this.scale.setMinimumWidth(width);
        this.scale.setMaximumWidth(width);
        this.addToContentPanel(this.scale, 1, 6, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH);

        WebLabel scaleComment = new WebLabel("for MIDI compatibility stay in [-127, 127]");
        scaleComment.setHorizontalAlignment(WebLabel.LEFT);
        scaleComment.setPadding(Settings.paddingInDialogs);
        this.addToContentPanel(scaleComment, 2, 6, 1, 1, 1.0, 1.0, 0, 0, GridBagConstraints.BOTH);

        /////////////

        this.addIdInput(7);
    }

    private ArrayList<Element> msmNotesAtDate = new ArrayList<>();   // MSM notes at the current date, candidates for the principal note

    /**
     * execute the editor dialog
     * @param input the object to be edited via the dialog or null to create a new one
     * @return the ornament data or null
     */
    @Override
    public OrnamentData edit(OrnamentData input) {
        this.ascendingPitchToggle.setSelected(true);    // the default state of note.order may be changed when parsing input
        boolean initNoteOrder = false;                  // this is set true when the note.order attribute of the input object contains a sequence of IDs, so we can later initialize the note matrix
        this.currentOrnamentData = input;
        this.noteMatrix.clear();
        this.repetitions.setValue(1);
        int repetitions = 0;
        ArrayList<Element> inputNotes = new ArrayList<>();  // the additional notes of the ornament

        if (input != null) {
            this.date.setValue(input.date);
            this.nameRef.setText(input.ornamentDefName);
            this.scale.setValue(input.scale);
            this.id.setText(input.xmlId);
            String noteId = input.correspondence;
            this.noteId.setText((noteId != null && noteId.startsWith("#")) ? noteId.substring(1) : noteId);
            repetitions = input.repetitions;

            if (input.xml != null) {
                for (Element child : input.xml.getChildElements())
                    if (child.getLocalName().equals("note"))
                        inputNotes.add(child);
                String rep = Helper.getAttributeValue("repetitions", input.xml);
                if (rep != null) {
                    try {
                        repetitions = Integer.parseInt(rep.trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else if (input.notes != null) {
                inputNotes.addAll(input.notes);
            }

            // parse note.order
            if ((input.noteOrder != null) && (input.noteOrder.size() > 0)) {
                switch (input.noteOrder.get(0)) {
                    case "ascending pitch":
                        this.ascendingPitchToggle.setSelected(true);    // already set by default
                        break;
                    case "descending pitch":
                        this.descendingPitchToggle.setSelected(true);
                        break;
                    default:
                        this.noteOrderToggle.setSelected(true);
                        initNoteOrder = true;                           // signal to a later point in this method to read and set the order of notes in the note matrix after it is initialized
                }
            }
        } else {
            this.noteId.setText("");
        }

        this.updateMsmDate();
        this.updateNoteList();
        this.noteMatrix.setExtraNotes(inputNotes);
        if (initNoteOrder) {                                            // if note.order is a sequence of IDs
            this.noteMatrix.setNoteOrder(input.noteOrder, repetitions); // communicate the sequence to the note matrix
            this.repetitions.setValue(Math.max(-1, Math.min(999, repetitions)));
        }
        this.noteMatrix.setMatrixEnabled(this.noteOrderToggle.isSelected());    // if ascending or descending pitch is selected, deactivate the matrix
        this.repetitions.setEnabled(this.noteOrderToggle.isSelected());

        if (this.nameRef.isEmpty())                                     // the user MUST choose an ornament; so, if the nameRef field is empty, write something in it that will be marked red
            this.nameRef.setText("Choose an ornament!");
        this.fullNameRefUpdate(Mpm.ORNAMENTATION_STYLE);
        this.pack();                                                    // resize the dialog so the matrix is visible
//        this.nameRef.selectAll();

        this.setVisible(true);  // start the dialog

        // after the dialog closed do the following

        if (!this.isOk())       // if dialog was canceled
            return input;       // return the input unchanged

        String id = this.id.getText();
        if (id.isEmpty())
            id = null;

        OrnamentData output = new OrnamentData();

        output.date = Tools.round((double) this.date.getValue(), 10);

        if (!this.nameRef.getText().isEmpty())
            output.ornamentDefName = this.nameRef.getText();

        output.scale = Tools.round((double) this.scale.getValue(), 10);
        output.xmlId = id;
        String noteId = this.noteId.getText().trim();
        if (!noteId.isEmpty())
            output.correspondence = noteId.startsWith("#") ? noteId : "#" + noteId;
        if ((output.correspondence == null) && !this.msmNotesAtDate.isEmpty()) {    // the renderer needs a principal note reference (noteid) to create the ornament notes
            String firstNoteId = this.msmNotesAtDate.get(0).getAttributeValue("id", "http://www.w3.org/XML/1998/namespace");
            if (firstNoteId != null)
                output.correspondence = "#" + firstNoteId;
        }

        // note.order, note pool, and repetitions
        if (this.noteOrderToggle.isSelected()) {
            output.noteOrder = this.noteMatrix.getNoteOrder();
            output.notes = this.noteMatrix.getExtraNotes(true);
            output.repetitions = this.noteMatrix.getRepetitions();
        } else {
            output.notes = this.noteMatrix.getExtraNotes(false);
            if (this.descendingPitchToggle.isSelected()) {
                output.noteOrder = new ArrayList<>();
                output.noteOrder.add("descending pitch");
            }
        }

        return output;
    }

    /**
     * play a single note via MIDI
     * @param pitch the MIDI pitch
     */
    private void playPitch(double pitch) {
        try {
            Sequence sequence = new Sequence(Sequence.PPQ, 720);
            Track track = sequence.createTrack();
            int p = Math.max(Math.min((int) Math.round(pitch), 127), 0);
            track.add(EventMaker.createNoteOn(0, 0, p, 100));
            track.add(EventMaker.createNoteOff(0, 720, p, 0));
            this.projectPane.getParentMpmToolbox().getMidiPlayerForSingleNotes().play(sequence);
        } catch (InvalidMidiDataException e) {
            e.printStackTrace();
        }
    }

    /**
     * make sure that the correct value is in the editor's msmDate, so the note pool is filled correctly
     */
    private void updateMsmDate() {
        double date = Tools.round((double) this.date.getValue(), 10);
        if (this.performance.getPPQ() != this.msm.getPPQ())
            this.setMsmDate((date * this.msm.getPPQ()) / this.performance.getPPQ());
        else
            this.setMsmDate(date);
    }

    /**
     * This method finds the parts that this map applies to and collects their score elements. Be sure that this.map != null.
     * @return a list of MSM score elements
     */
    private ArrayList<Element> getMsmScores() {
        if (this.msm == null)
            return new ArrayList<>();

        ArrayList<Element> result = new ArrayList<>();

        if (this.map.getLocalHeader() != null) {    // if we are in a local map we need to find the corresponding MSM part
            Element mpmPart = (Element) this.map.getLocalHeader().getXml().getParent();

            String number = Helper.getAttributeValue("number", mpmPart);
            for (Element part : this.msm.getParts()) {
                if (Helper.getAttributeValue("number", part).equals(number)
                        && (part.getFirstChildElement("dated") != null)
                        && (part.getFirstChildElement("dated").getFirstChildElement("score") != null)){
                    result.add(part.getFirstChildElement("dated").getFirstChildElement("score"));
                    return result;
                }
            }
            String name = Helper.getAttributeValue("name", mpmPart);
            for (Element part : this.msm.getParts()) {
                if (Helper.getAttributeValue("name", part).equals(name)
                        && (part.getFirstChildElement("dated") != null)
                        && (part.getFirstChildElement("dated").getFirstChildElement("score") != null)){
                    result.add(part.getFirstChildElement("dated").getFirstChildElement("score"));
                    return result;
                }
            }
            String midiChannel = Helper.getAttributeValue("midi.channel", mpmPart);
            String midiPort = Helper.getAttributeValue("midi.port", mpmPart);
            for (Element part : this.msm.getParts()) {
                if (Helper.getAttributeValue("midi.channel", part).equals(midiChannel)
                        && Helper.getAttributeValue("midi.port", part).equals(midiPort)
                        && (part.getFirstChildElement("dated") != null)
                        && (part.getFirstChildElement("dated").getFirstChildElement("score") != null)){
                    result.add(part.getFirstChildElement("dated").getFirstChildElement("score"));
                    return result;
                }
            }
            return result;
        }

        // we are in a global map, so we return all parts
        for (Element part : this.msm.getParts()) {
            if ((part.getFirstChildElement("dated") != null) && (part.getFirstChildElement("dated").getFirstChildElement("score") != null)) // in this context we are interested only in parts with scores
                result.add(part.getFirstChildElement("dated").getFirstChildElement("score"));
        }
        return result;
    }

    /**
     * This method finds the MSM notes at the current date and updates the list of notes.
     */
    private void updateNoteList() {
        if ((this.map == null) || (this.date == null))
            return;

        ArrayList<Element> scores = this.getMsmScores();
        ArrayList<Element> msmNotes = new ArrayList<>();

        for (Element score : scores) {      // collect all notes at the specified date
            for (Element noteElement : score.getChildElements("note")) {
                double noteDate = Double.parseDouble(noteElement.getAttributeValue("date"));
                if (noteDate < this.msmDate)
                    continue;
                if (noteDate > this.msmDate)
                    break;
                if ((noteDate == this.msmDate) && (noteElement.getAttribute("id", "http://www.w3.org/XML/1998/namespace") != null))    // if the note has no ID we cannot refer it
                    msmNotes.add(noteElement);
            }
        }
        this.msmNotesAtDate = msmNotes;
        this.fillNoteIdChooser();
        this.noteMatrix.setMsmNotes(msmNotes);
        this.pack();                         // resize the dialog so all notes are visible
    }

    private void checkNoteId() {
        if (this.noteMatrix != null)
            this.noteMatrix.setPrincipalNote(this.noteId.getText());
        this.noteId.setForeground((this.noteIds.contains(this.noteId.getText().trim().replaceFirst("^#", ""))) ? Settings.foregroundColor : Settings.errorColor);
        this.noteId.revalidate();
        this.noteId.repaint();
    }

    private void fillNoteIdChooser() {
        this.noteIds.clear();
        for (Element note : this.msmNotesAtDate)
            this.noteIds.add(note.getAttributeValue("id", "http://www.w3.org/XML/1998/namespace"));

        if (this.noteIds.isEmpty()) {
            this.noteIdChooser.setPopupMenu(null);
            this.noteIdChooser.setEnabled(false);
            this.noteIdChooser.setToolTip("No notes (with IDs) at the specified date.");
            this.checkNoteId();
            return;
        }

        WebPopupMenu noteIdsPopup = new WebPopupMenu();
        for (String id : this.noteIds) {
            WebMenuItem item = new WebMenuItem(id);
            item.addActionListener(event -> this.noteId.setText(item.getText()));
            noteIdsPopup.add(item);
        }
        this.noteIdChooser.setPopupMenu(noteIdsPopup);
        this.noteIdChooser.setEnabled(true);
        this.noteIdChooser.removeToolTips();
        this.checkNoteId();
    }
}
