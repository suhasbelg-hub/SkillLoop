package com.skillloop.ui;

import com.skillloop.model.StudentSkill;
import com.skillloop.service.AdminService;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/** Administrator: moderate skill listings. */
public class AdminListingsPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long adminId;
    private final DefaultTableModel model = UiComponents.tableModel(
            new String[]{"#", "Student", "Skill", "Type", "Proficiency", "Active", "Description"});
    private final JTable table = UiComponents.table(model);
    private final JTextField searchField = UiComponents.textField(20);
    private final List<StudentSkill> listings = new ArrayList<>();
    private final AdminService adminService = new AdminService();

    public AdminListingsPanel(long adminId) {
        this.adminId = adminId;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    private void buildUi() {
        JPanel card = UiComponents.card("Skill listings");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        row.setOpaque(false);
        JButton searchButton = UiComponents.button("Search", UiComponents.ButtonStyle.PRIMARY);
        JButton disableButton = UiComponents.button("Deactivate", UiComponents.ButtonStyle.DANGER);
        JButton enableButton = UiComponents.button("Activate", UiComponents.ButtonStyle.SUCCESS);
        row.add(searchField);
        row.add(searchButton);
        row.add(disableButton);
        row.add(enableButton);
        card.add(row, BorderLayout.NORTH);
        card.add(UiComponents.scroll(table), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        searchButton.addActionListener(event -> refresh());
        searchField.addActionListener(event -> refresh());
        disableButton.addActionListener(event -> moderate(false));
        enableButton.addActionListener(event -> moderate(true));
    }

    private void moderate(boolean active) {
        int row = table.getSelectedRow();
        if (row < 0) {
            Dialogs.warning(this, "Please select a listing first.");
            return;
        }
        long id = (Long) model.getValueAt(row, 0);
        String reason = Dialogs.input(this, "Moderation", "Reason:", active ? "re-enabled" : "inappropriate content");
        AsyncRunner.run(this, () -> {
            adminService.moderateListing(adminId, id, active, reason);
            return Boolean.TRUE;
        }, result -> {
            Dialogs.info(this, "Listing updated.");
            refresh();
        });
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> adminService.listListings(adminId, searchField.getText(), 200), this::render);
    }

    private void render(List<StudentSkill> loaded) {
        listings.clear();
        listings.addAll(loaded);
        model.setRowCount(0);
        for (StudentSkill listing : loaded) {
            model.addRow(new Object[]{
                    listing.getStudentSkillId(),
                    listing.getStudentName(),
                    listing.getSkillName(),
                    listing.getSkillType().name(),
                    listing.getProficiency().name(),
                    listing.isActive() ? "yes" : "no",
                    listing.getDescription()});
        }
    }
}
