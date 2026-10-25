package com.skillloop.ui;

import com.skillloop.model.LearningSession;
import com.skillloop.model.SessionStatus;
import com.skillloop.service.AdminService;
import com.skillloop.util.DateTimeUtil;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/** Administrator: dispute resolution and session monitoring. */
public class AdminDisputesPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long adminId;
    private final DefaultTableModel model = UiComponents.tableModel(
            new String[]{"#", "When", "Skill", "Teacher", "Learner", "Credits", "Status", "Note"});
    private final JTable table = UiComponents.table(model);
    private final JComboBox<String> statusCombo = UiComponents.comboBox(new String[]{
            "DISPUTED", "REQUESTED", "ACCEPTED", "COMPLETED", "CANCELLED", "REJECTED"});
    private final JComboBox<AdminService.DisputeResolution> resolutionCombo =
            UiComponents.comboBox(AdminService.DisputeResolution.values());
    private final List<LearningSession> sessions = new ArrayList<>();
    private final AdminService adminService = new AdminService();

    public AdminDisputesPanel(long adminId) {
        this.adminId = adminId;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    private void buildUi() {
        JPanel card = UiComponents.card("Sessions");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        row.setOpaque(false);
        JButton loadButton = UiComponents.button("Load", UiComponents.ButtonStyle.PRIMARY);
        JButton resolveButton = UiComponents.button("Resolve dispute", UiComponents.ButtonStyle.SUCCESS);
        JButton cancelButton = UiComponents.button("Cancel session", UiComponents.ButtonStyle.DANGER);
        row.add(UiComponents.muted("Status"));
        row.add(statusCombo);
        row.add(loadButton);
        row.add(UiComponents.muted("Resolution"));
        row.add(resolutionCombo);
        row.add(resolveButton);
        row.add(cancelButton);
        card.add(row, BorderLayout.NORTH);
        card.add(UiComponents.scroll(table), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        loadButton.addActionListener(event -> refresh());
        statusCombo.addActionListener(event -> refresh());
        resolveButton.addActionListener(event -> {
            LearningSession session = selected();
            if (session == null) {
                return;
            }
            AdminService.DisputeResolution resolution = (AdminService.DisputeResolution) resolutionCombo.getSelectedItem();
            String note = Dialogs.input(this, "Resolution note",
                    "Note stored with the session (visible to both students):", "");
            AsyncRunner.run(this, () -> adminService.resolveDispute(adminId, session.getSessionId(), resolution, note),
                    resolved -> {
                        Dialogs.info(this, "Dispute resolved: " + resolution.name());
                        refresh();
                    });
        });
        cancelButton.addActionListener(event -> {
            LearningSession session = selected();
            if (session == null) {
                return;
            }
            String reason = Dialogs.input(this, "Cancellation", "Reason for the cancellation:", "");
            AsyncRunner.run(this, () -> {
                adminService.cancelSession(adminId, session.getSessionId(), reason);
                return Boolean.TRUE;
            }, result -> {
                Dialogs.info(this, "Session cancelled and reserved credits released.");
                refresh();
            });
        });
    }

    private LearningSession selected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            Dialogs.warning(this, "Please select a session first.");
            return null;
        }
        long id = (Long) model.getValueAt(row, 0);
        return sessions.stream().filter(s -> s.getSessionId() == id).findFirst().orElse(null);
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            SessionStatus status = SessionStatus.valueOf((String) statusCombo.getSelectedItem());
            if (status == SessionStatus.DISPUTED) {
                return adminService.disputes(adminId);
            }
            return new com.skillloop.dao.SessionDAO().listByStatus(status, 200);
        }, this::render);
    }

    private void render(List<LearningSession> loaded) {
        sessions.clear();
        sessions.addAll(loaded);
        model.setRowCount(0);
        for (LearningSession session : loaded) {
            model.addRow(new Object[]{
                    session.getSessionId(),
                    DateTimeUtil.format(session.getStartTime()),
                    session.getSkillName(),
                    session.getTeacherName(),
                    session.getLearnerName(),
                    session.getCreditCost(),
                    session.getStatus().name(),
                    session.getResolutionNote()});
        }
    }
}
