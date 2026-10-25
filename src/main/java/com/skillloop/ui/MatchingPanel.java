package com.skillloop.ui;

import com.skillloop.model.MatchResult;
import com.skillloop.model.Proficiency;
import com.skillloop.model.Skill;
import com.skillloop.service.MatchingService;
import com.skillloop.service.SkillService;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;

/**
 * Find Learning Partners - the smart matching screen.
 *
 * <p>The ranking is computed by {@link MatchingService} with a transparent, documented weight
 * model; this screen shows the resulting score together with the individual reasons.</p>
 */
public class MatchingPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long studentId;
    private final BookingRequestHandler bookingHandler;
    private final DefaultTableModel model =
            UiComponents.tableModel(new String[]{"Peer", "Department", "Skill", "Level", "Score", "Why recommended"});
    private final JTable table = UiComponents.table(model);
    private final JComboBox<Skill> skillCombo = UiComponents.comboBox(new Skill[0]);
    private final JComboBox<Proficiency> proficiencyCombo =
            UiComponents.comboBox(new Proficiency[]{null, Proficiency.BEGINNER, Proficiency.INTERMEDIATE, Proficiency.ADVANCED});
    private final JComboBox<String> modeCombo =
            UiComponents.comboBox(new String[]{"All teachers", "Reciprocal partners only"});
    private final List<MatchResult> results = new ArrayList<>();

    private final MatchingService matchingService = new MatchingService();
    private final SkillService skillService = new SkillService();

    public MatchingPanel(long studentId, BookingRequestHandler bookingHandler) {
        this.studentId = studentId;
        this.bookingHandler = bookingHandler;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    private void buildUi() {
        JPanel filters = UiComponents.card("Matching filters");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        row.setOpaque(false);
        JButton searchButton = UiComponents.button("Find partners", UiComponents.ButtonStyle.PRIMARY);
        JButton bookButton = UiComponents.button("Book with selected peer", UiComponents.ButtonStyle.SUCCESS);
        row.add(UiComponents.muted("Skill"));
        row.add(skillCombo);
        row.add(UiComponents.muted("Minimum level"));
        row.add(proficiencyCombo);
        row.add(UiComponents.muted("Mode"));
        row.add(modeCombo);
        row.add(searchButton);
        row.add(bookButton);
        filters.add(row, BorderLayout.CENTER);

        JPanel resultCard = UiComponents.card("Recommendations");
        resultCard.add(UiComponents.scroll(table), BorderLayout.CENTER);
        resultCard.add(UiComponents.muted(
                "Score = reciprocity (30) + proficiency (20) + availability (15) + rating (15) "
                        + "+ experience (10) + wish-list match (10)."), BorderLayout.SOUTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        filters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        content.add(filters);
        content.add(Box.createVerticalStrut(12));
        content.add(resultCard);
        add(content, BorderLayout.CENTER);

        searchButton.addActionListener(event -> refresh());
        skillCombo.addActionListener(event -> refresh());
        proficiencyCombo.addActionListener(event -> refresh());
        modeCombo.addActionListener(event -> refresh());
        bookButton.addActionListener(event -> {
            int rowIndex = table.getSelectedRow();
            if (rowIndex < 0) {
                Dialogs.warning(this, "Please select a peer first.");
                return;
            }
            MatchResult match = results.get(rowIndex);
            if (bookingHandler != null) {
                bookingHandler.openBooking(match.getTeacherId(), match.getSkillId());
            }
        });
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            PanelData data = new PanelData();
            data.skills = skillService.catalogue();
            Skill selected = (Skill) skillCombo.getSelectedItem();
            boolean reciprocalOnly = "Reciprocal partners only".equals(modeCombo.getSelectedItem());
            Proficiency minimum = (Proficiency) proficiencyCombo.getSelectedItem();
            if (reciprocalOnly) {
                data.matches = matchingService.findReciprocalPartners(studentId, 50);
            } else {
                data.matches = matchingService.recommendTeachers(studentId,
                        selected == null ? null : selected.getSkillId(), minimum, 50);
            }
            return data;
        }, this::render);
    }

    private void render(PanelData data) {
        Skill previous = (Skill) skillCombo.getSelectedItem();
        skillCombo.removeAllItems();
        skillCombo.addItem(null);
        for (Skill skill : data.skills) {
            skillCombo.addItem(skill);
        }
        skillCombo.setSelectedItem(previous);

        results.clear();
        results.addAll(data.matches);
        model.setRowCount(0);
        for (MatchResult match : data.matches) {
            model.addRow(new Object[]{
                    match.getTeacherName(),
                    match.getDepartment(),
                    match.getSkillName(),
                    match.getProficiency().name().toLowerCase(),
                    match.scorePercent() + "/100" + (match.isReciprocal() ? " (swap)" : ""),
                    match.explain()});
        }
    }

    /** Snapshot loaded in one background task. */
    private static final class PanelData {
        private List<Skill> skills = List.of();
        private List<MatchResult> matches = List.of();
    }
}
