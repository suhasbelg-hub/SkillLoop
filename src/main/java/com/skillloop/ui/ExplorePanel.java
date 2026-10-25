package com.skillloop.ui;

import com.skillloop.model.Proficiency;
import com.skillloop.model.Skill;
import com.skillloop.model.StudentSkill;
import com.skillloop.service.SkillService;
import com.skillloop.service.StudentService;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.Box;

/** Explore Skills: search and filter the catalogue and inspect who teaches a skill. */
public class ExplorePanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long studentId;
    private final DefaultTableModel skillModel =
            UiComponents.tableModel(new String[]{"Skill", "Category", "Teachers", "Learners", "Completed", "Description"});
    private final DefaultTableModel teacherModel =
            UiComponents.tableModel(new String[]{"Teacher", "Department", "Proficiency", "Rating", "Sessions", "About"});
    private final JTable skillTable = UiComponents.table(skillModel);
    private final JTextField searchField = UiComponents.textField(18);
    private final JComboBox<String> categoryCombo = UiComponents.comboBox(new String[]{"ALL"});
    private final JComboBox<Proficiency> proficiencyCombo =
            UiComponents.comboBox(new Proficiency[]{null, Proficiency.BEGINNER, Proficiency.INTERMEDIATE, Proficiency.ADVANCED});
    private final List<Skill> loadedSkills = new java.util.ArrayList<>();

    private final SkillService skillService = new SkillService();
    private final StudentService studentService = new StudentService();

    public ExplorePanel(long studentId) {
        this.studentId = studentId;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    public long getStudentId() {
        return studentId;
    }

    private void buildUi() {
        JPanel filters = UiComponents.card("Search the catalogue");
        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        filterRow.setOpaque(false);
        JButton searchButton = UiComponents.button("Search", UiComponents.ButtonStyle.PRIMARY);
        JButton clearButton = UiComponents.button("Clear", UiComponents.ButtonStyle.LINK);
        filterRow.add(UiComponents.muted("Search"));
        filterRow.add(searchField);
        filterRow.add(UiComponents.muted("Category"));
        filterRow.add(categoryCombo);
        filterRow.add(UiComponents.muted("Minimum level"));
        filterRow.add(proficiencyCombo);
        filterRow.add(searchButton);
        filterRow.add(clearButton);
        filters.add(filterRow, BorderLayout.CENTER);

        JPanel tables = new JPanel();
        tables.setLayout(new BoxLayout(tables, BoxLayout.Y_AXIS));
        tables.setOpaque(false);

        JPanel skillCard = UiComponents.card("Skills");
        skillCard.add(UiComponents.scroll(skillTable), BorderLayout.CENTER);
        skillCard.setPreferredSize(new Dimension(900, 260));
        skillCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        tables.add(skillCard);
        tables.add(Box.createVerticalStrut(12));

        JPanel teacherCard = UiComponents.card("Teachers of the selected skill");
        teacherCard.add(UiComponents.scroll(UiComponents.table(teacherModel)), BorderLayout.CENTER);
        tables.add(teacherCard);

        searchButton.addActionListener(event -> refresh());
        searchField.addActionListener(event -> refresh());
        categoryCombo.addActionListener(event -> refresh());
        proficiencyCombo.addActionListener(event -> loadTeachers());
        clearButton.addActionListener(event -> {
            searchField.setText("");
            categoryCombo.setSelectedIndex(0);
            proficiencyCombo.setSelectedIndex(0);
            refresh();
        });
        skillTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                loadTeachers();
            }
        });

        add(filters, BorderLayout.NORTH);
        add(tables, BorderLayout.CENTER);
    }

    /** Loads the teachers of the skill that is currently selected in the upper table. */
    private void loadTeachers() {
        int row = skillTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        if (row >= loadedSkills.size()) {
            return;
        }
        Skill skill = loadedSkills.get(row);
        Proficiency minimum = (Proficiency) proficiencyCombo.getSelectedItem();
        AsyncRunner.run(this, () -> skillService.teachersFor(skill.getSkillId(), minimum),
                teachers -> fillTeachers(teachers));
    }

    private void fillTeachers(List<StudentSkill> teachers) {
        teacherModel.setRowCount(0);
        for (StudentSkill listing : teachers) {
            double rating = studentService.getProgress(listing.getStudentId()).getAvgRating();
            teacherModel.addRow(new Object[]{
                    listing.getStudentName(),
                    listing.getDepartment(),
                    listing.getProficiency().name(),
                    rating > 0 ? String.format("%.2f", rating) : "not rated",
                    "-",
                    listing.getDescription()});
        }
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            PanelData data = new PanelData();
            data.categories = skillService.categories();
            data.skills = skillService.search(searchField.getText(), (String) categoryCombo.getSelectedItem());
            return data;
        }, data -> {
            String previous = (String) categoryCombo.getSelectedItem();
            categoryCombo.removeAllItems();
            categoryCombo.addItem("ALL");
            for (String category : data.categories) {
                categoryCombo.addItem(category);
            }
            categoryCombo.setSelectedItem(previous == null ? "ALL" : previous);

            loadedSkills.clear();
            loadedSkills.addAll(data.skills);
            skillModel.setRowCount(0);
            for (Skill skill : data.skills) {
                skillModel.addRow(new Object[]{
                        skill.getSkillName(),
                        skill.getCategory(),
                        skill.getTeachersOffering(),
                        skill.getLearnersWanting(),
                        skill.getCompletedSessions(),
                        skill.getDescription()});
            }
            if (skillModel.getRowCount() > 0) {
                skillTable.setRowSelectionInterval(0, 0);
                loadTeachers();
            }
        });
    }

    /** Snapshot loaded in one background task. */
    private static final class PanelData {
        private List<String> categories = List.of();
        private List<Skill> skills = List.of();
    }
}
