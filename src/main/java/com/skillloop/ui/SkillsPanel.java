package com.skillloop.ui;

import com.skillloop.model.Proficiency;
import com.skillloop.model.Skill;
import com.skillloop.model.SkillType;
import com.skillloop.model.StudentSkill;
import com.skillloop.service.SkillService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;

/** My Skills: add, edit and deactivate the skills a student offers or wants to learn. */
public class SkillsPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long studentId;
    private final DefaultTableModel model =
            UiComponents.tableModel(new String[]{"#", "Skill", "Category", "Type", "Proficiency", "Active", "Description"});
    private final JTable table = UiComponents.table(model);

    private final JComboBox<Skill> skillCombo = UiComponents.comboBox(new Skill[0]);
    private final JComboBox<SkillType> typeCombo = UiComponents.comboBox(SkillType.values());
    private final JComboBox<Proficiency> proficiencyCombo = UiComponents.comboBox(Proficiency.values());
    private final JTextArea descriptionArea = UiComponents.textArea(2, 18);

    private final JButton editButton = UiComponents.button("Update selected", UiComponents.ButtonStyle.SECONDARY);
    private final JButton deactivateButton = UiComponents.button("Deactivate", UiComponents.ButtonStyle.DANGER);
    private final JButton activateButton = UiComponents.button("Activate", UiComponents.ButtonStyle.SUCCESS);

    private final SkillService skillService = new SkillService();

    public SkillsPanel(long studentId) {
        this.studentId = studentId;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    private void buildUi() {
        JPanel top = new JPanel(new GridLayout(1, 2, 12, 12));
        top.setOpaque(false);

        JPanel tableCard = UiComponents.card("Your skill listings");
        tableCard.add(UiComponents.scroll(table), BorderLayout.CENTER);
        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tableActions.setOpaque(false);
        JButton refreshButton = UiComponents.button("Reload", UiComponents.ButtonStyle.LINK);
        refreshButton.addActionListener(event -> refresh());
        tableActions.add(editButton);
        tableActions.add(activateButton);
        tableActions.add(deactivateButton);
        tableActions.add(refreshButton);
        tableCard.add(tableActions, BorderLayout.SOUTH);
        top.add(tableCard);

        JPanel formCard = UiComponents.card("Add a skill");
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        form.add(UiComponents.formRow("Skill", skillCombo));
        form.add(Box.createVerticalStrut(10));
        form.add(UiComponents.formRow("I want to", typeCombo));
        form.add(Box.createVerticalStrut(10));
        form.add(UiComponents.formRow("Proficiency", proficiencyCombo));
        form.add(Box.createVerticalStrut(10));
        form.add(UiComponents.formRow("Description", UiComponents.scroll(descriptionArea)));
        form.add(Box.createVerticalStrut(16));
        JButton addButton = UiComponents.button("Add to my profile", UiComponents.ButtonStyle.PRIMARY);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.add(addButton);
        form.add(actions);
        formCard.add(form, BorderLayout.CENTER);
        top.add(formCard);

        JPanel bottom = UiComponents.card("Add a new skill to the catalogue");
        JPanel bottomForm = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bottomForm.setOpaque(false);
        javax.swing.JTextField nameField = UiComponents.textField(16);
        javax.swing.JTextField categoryField = UiComponents.textField(16);
        javax.swing.JTextField skillDescription = UiComponents.textField(24);
        JButton createSkillButton = UiComponents.button("Create skill", UiComponents.ButtonStyle.SECONDARY);
        bottomForm.add(UiComponents.label("Name"));
        bottomForm.add(nameField);
        bottomForm.add(UiComponents.label("Category"));
        bottomForm.add(categoryField);
        bottomForm.add(UiComponents.label("Description"));
        bottomForm.add(skillDescription);
        bottomForm.add(createSkillButton);
        bottom.add(bottomForm, BorderLayout.CENTER);

        addButton.addActionListener(event -> {
            Skill skill = (Skill) skillCombo.getSelectedItem();
            if (skill == null) {
                Dialogs.warning(this, "Please choose a skill.");
                return;
            }
            SkillType type = (SkillType) typeCombo.getSelectedItem();
            Proficiency proficiency = (Proficiency) proficiencyCombo.getSelectedItem();
            String description = descriptionArea.getText();
            AsyncRunner.run(this, () -> skillService.addListing(studentId, skill.getSkillId(), type, proficiency,
                    description), listing -> {
                descriptionArea.setText("");
                Dialogs.info(this, "Added to your profile.");
                refresh();
            });
        });

        editButton.addActionListener(event -> {
            StudentSkill listing = selected();
            if (listing == null) {
                return;
            }
            Proficiency proficiency = (Proficiency) proficiencyCombo.getSelectedItem();
            String description = descriptionArea.getText();
            AsyncRunner.run(this, () -> {
                skillService.updateListing(studentId, listing.getStudentSkillId(), proficiency, description, true);
                return Boolean.TRUE;
            }, result -> {
                Dialogs.info(this, "Listing updated.");
                refresh();
            });
        });

        deactivateButton.addActionListener(event -> {
            StudentSkill listing = selected();
            if (listing == null) {
                return;
            }
            AsyncRunner.run(this, () -> {
                skillService.deactivateListing(studentId, listing.getStudentSkillId());
                return Boolean.TRUE;
            }, result -> refresh());
        });

        activateButton.addActionListener(event -> {
            StudentSkill listing = selected();
            if (listing == null) {
                return;
            }
            AsyncRunner.run(this, () -> {
                skillService.updateListing(studentId, listing.getStudentSkillId(), listing.getProficiency(),
                        listing.getDescription(), true);
                return Boolean.TRUE;
            }, result -> refresh());
        });

        createSkillButton.addActionListener(event -> AsyncRunner.run(this,
                () -> skillService.createSkill(nameField.getText(), categoryField.getText(),
                        skillDescription.getText()),
                skill -> {
                    nameField.setText("");
                    categoryField.setText("");
                    skillDescription.setText("");
                    Dialogs.info(this, "Skill \"" + skill.getSkillName() + "\" was added to the catalogue.");
                    refresh();
                }));

        table.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            StudentSkill listing = selected();
            if (listing != null) {
                descriptionArea.setText(listing.getDescription() == null ? "" : listing.getDescription());
                proficiencyCombo.setSelectedItem(listing.getProficiency());
                typeCombo.setSelectedItem(listing.getSkillType());
            }
        });

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 380));
        content.add(top);
        content.add(Box.createVerticalStrut(12));
        content.add(bottom);
        add(content, BorderLayout.CENTER);
    }

    private StudentSkill selected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            Dialogs.warning(this, "Please select a listing first.");
            return null;
        }
        long id = (Long) model.getValueAt(row, 0);
        return skillService.myListings(studentId, null).stream()
                .filter(listing -> listing.getStudentSkillId() == id)
                .findFirst().orElse(null);
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            PanelData data = new PanelData();
            data.skills = skillService.catalogue();
            data.listings = skillService.myListings(studentId, null);
            return data;
        }, data -> {
            skillCombo.removeAllItems();
            for (Skill skill : data.skills) {
                skillCombo.addItem(skill);
            }
            model.setRowCount(0);
            for (StudentSkill listing : data.listings) {
                model.addRow(new Object[]{
                        listing.getStudentSkillId(),
                        listing.getSkillName(),
                        listing.getCategory(),
                        listing.getSkillType().name(),
                        listing.getProficiency().name(),
                        listing.isActive() ? "yes" : "no",
                        listing.getDescription()});
            }
        });
    }

    /** Snapshot loaded in one background task. */
    private static final class PanelData {
        private List<Skill> skills = List.of();
        private List<StudentSkill> listings = List.of();
    }
}
