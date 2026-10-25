package com.skillloop.ui;

import com.skillloop.model.LearningResource;
import com.skillloop.model.Skill;
import com.skillloop.service.ResourceService;
import com.skillloop.service.SkillService;
import com.skillloop.util.DateTimeUtil;

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

/** Learning resources: share notes, links and practice material with other students. */
public class ResourcesPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long studentId;
    private final DefaultTableModel model = UiComponents.tableModel(
            new String[]{"Title", "Skill", "Shared by", "Description", "Link", "When"});
    private final JTable table = UiComponents.table(model);
    private final JComboBox<Skill> skillCombo = UiComponents.comboBox(new Skill[0]);
    private final JTextField titleField = UiComponents.textField(18);
    private final JTextField descriptionField = UiComponents.textField(24);
    private final JTextField urlField = UiComponents.textField(24);
    private final ResourceService resourceService = new ResourceService();
    private final SkillService skillService = new SkillService();

    public ResourcesPanel(long studentId) {
        this.studentId = studentId;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    private void buildUi() {
        JPanel shareCard = UiComponents.card("Share a resource");
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        form.setOpaque(false);
        JButton shareButton = UiComponents.button("Share", UiComponents.ButtonStyle.PRIMARY);
        form.add(UiComponents.muted("Skill"));
        form.add(skillCombo);
        form.add(UiComponents.muted("Title"));
        form.add(titleField);
        form.add(UiComponents.muted("Description"));
        form.add(descriptionField);
        form.add(UiComponents.muted("Link"));
        form.add(urlField);
        form.add(shareButton);
        shareCard.add(form, BorderLayout.CENTER);

        JPanel tableCard = UiComponents.card("Shared learning resources");
        tableCard.add(UiComponents.scroll(table), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        actions.setOpaque(false);
        JButton deleteButton = UiComponents.button("Delete my selection", UiComponents.ButtonStyle.DANGER);
        JButton reloadButton = UiComponents.button("Reload", UiComponents.ButtonStyle.LINK);
        actions.add(deleteButton);
        actions.add(reloadButton);
        tableCard.add(actions, BorderLayout.SOUTH);

        shareButton.addActionListener(event -> {
            Skill skill = (Skill) skillCombo.getSelectedItem();
            if (skill == null) {
                Dialogs.warning(this, "Please choose a skill.");
                return;
            }
            AsyncRunner.run(this, () -> resourceService.share(studentId, skill.getSkillId(),
                    titleField.getText(), descriptionField.getText(), urlField.getText()),
                    resource -> {
                        titleField.setText("");
                        descriptionField.setText("");
                        urlField.setText("");
                        Dialogs.info(this, "Resource shared with the community.");
                        refresh();
                    });
        });
        deleteButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                Dialogs.warning(this, "Please select one of your resources.");
                return;
            }
            long resourceId = (Long) model.getValueAt(row, 6);
            AsyncRunner.run(this, () -> {
                resourceService.delete(studentId, resourceId);
                return Boolean.TRUE;
            }, result -> {
                Dialogs.info(this, "Resource deleted.");
                refresh();
            });
        });
        reloadButton.addActionListener(event -> refresh());

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        shareCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        content.add(shareCard);
        content.add(Box.createVerticalStrut(12));
        content.add(tableCard);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            Data data = new Data();
            data.skills = skillService.catalogue();
            data.resources = resourceService.all(200);
            return data;
        }, this::render);
    }

    private void render(Data data) {
        Skill previous = (Skill) skillCombo.getSelectedItem();
        skillCombo.removeAllItems();
        for (Skill skill : data.skills) {
            skillCombo.addItem(skill);
        }
        if (previous != null) {
            skillCombo.setSelectedItem(previous);
        }
        model.setRowCount(0);
        for (LearningResource resource : data.resources) {
            model.addRow(new Object[]{
                    resource.getTitle(),
                    resource.getSkillName(),
                    resource.getStudentName(),
                    resource.getDescription(),
                    resource.getResourceUrl(),
                    DateTimeUtil.format(resource.getCreatedAt()),
                    resource.getResourceId()});
        }
    }

    /** Snapshot loaded in one background task. */
    private static final class Data {
        private List<Skill> skills = List.of();
        private List<LearningResource> resources = List.of();
    }
}
