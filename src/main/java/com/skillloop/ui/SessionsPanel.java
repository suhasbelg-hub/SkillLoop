package com.skillloop.ui;

import com.skillloop.model.LearningSession;
import com.skillloop.model.SessionStatus;
import com.skillloop.service.BookingService;
import com.skillloop.util.DateTimeUtil;

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
 * My Sessions: accept, reject, cancel, confirm completion and dispute bookings.
 *
 * <p>Every action is executed in the background and validated by {@link BookingService}; the
 * buttons are only a way to call the service, never the place where rules are enforced.</p>
 */
public class SessionsPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long studentId;
    private final Runnable walletListener;
    private final DefaultTableModel model = UiComponents.tableModel(
            new String[]{"#", "When", "Skill", "Teacher", "Learner", "Min", "Credits", "Status", "Confirmed"});
    private final JTable table = UiComponents.table(model);
    private final JComboBox<String> roleCombo =
            UiComponents.comboBox(new String[]{"All", "Teaching", "Learning"});
    private final JComboBox<String> statusCombo = UiComponents.comboBox(new String[]{
            "ALL", "REQUESTED", "ACCEPTED", "COMPLETED", "CANCELLED", "REJECTED", "DISPUTED"});
    private final List<LearningSession> sessions = new ArrayList<>();
    private final BookingService bookingService = new BookingService();

    public SessionsPanel(long studentId, Runnable walletListener) {
        this.studentId = studentId;
        this.walletListener = walletListener;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    private void buildUi() {
        JPanel filters = UiComponents.card("Filters");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        row.setOpaque(false);
        row.add(UiComponents.muted("Show"));
        row.add(roleCombo);
        row.add(UiComponents.muted("Status"));
        row.add(statusCombo);
        filters.add(row, BorderLayout.CENTER);

        JPanel tableCard = UiComponents.card("Sessions");
        tableCard.add(UiComponents.scroll(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        actions.setOpaque(false);
        JButton acceptButton = UiComponents.button("Accept request", UiComponents.ButtonStyle.SUCCESS);
        JButton rejectButton = UiComponents.button("Reject request", UiComponents.ButtonStyle.DANGER);
        JButton cancelButton = UiComponents.button("Cancel session", UiComponents.ButtonStyle.SECONDARY);
        JButton confirmButton = UiComponents.button("Confirm completion", UiComponents.ButtonStyle.PRIMARY);
        JButton disputeButton = UiComponents.button("Raise dispute", UiComponents.ButtonStyle.DANGER);
        JButton refreshButton = UiComponents.button("Reload", UiComponents.ButtonStyle.LINK);
        actions.add(acceptButton);
        actions.add(rejectButton);
        actions.add(confirmButton);
        actions.add(cancelButton);
        actions.add(disputeButton);
        actions.add(refreshButton);
        tableCard.add(actions, BorderLayout.SOUTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        filters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        content.add(filters);
        content.add(Box.createVerticalStrut(10));
        content.add(tableCard);
        add(content, BorderLayout.CENTER);

        roleCombo.addActionListener(event -> refresh());
        statusCombo.addActionListener(event -> refresh());
        refreshButton.addActionListener(event -> refresh());
        acceptButton.addActionListener(event -> withSession(session -> {
            bookingService.accept(session.getSessionId(), studentId);
            return session;
        }, "Session accepted. The credits stay reserved until completion."));
        rejectButton.addActionListener(event -> withSession(session -> {
            bookingService.reject(session.getSessionId(), studentId, reason("rejection"));
            return session;
        }, "Booking rejected. The reserved credits were released."));
        cancelButton.addActionListener(event -> withSession(session -> {
            bookingService.cancel(session.getSessionId(), studentId, reason("cancellation"));
            return session;
        }, "Session cancelled. The reserved credits were released."));
        confirmButton.addActionListener(event -> withSession(session -> {
            bookingService.confirmCompletion(session.getSessionId(), studentId);
            return session;
        }, "Completion recorded. When both sides confirm, the credits are transferred."));
        disputeButton.addActionListener(event -> withSession(session -> {
            bookingService.raiseDispute(session.getSessionId(), studentId, reason("dispute"));
            return session;
        }, "Dispute raised. An administrator will review the session."));
    }

    private String reason(String what) {
        String value = Dialogs.input(this, "Reason for " + what,
                "Please describe the reason (stored with the session):", "");
        return value == null || value.isBlank() ? "No reason provided" : value.trim();
    }

    /** Runs an action on the selected session and refreshes afterwards. */
    private void withSession(java.util.function.Function<LearningSession, LearningSession> action, String message) {
        LearningSession session = selected();
        if (session == null) {
            return;
        }
        AsyncRunner.run(this, () -> action.apply(session), result -> {
            Dialogs.info(this, message);
            if (walletListener != null) {
                walletListener.run();
            }
            refresh();
        });
    }

    private LearningSession selected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            Dialogs.warning(this, "Please select a session first.");
            return null;
        }
        long sessionId = (Long) model.getValueAt(row, 0);
        return sessions.stream().filter(s -> s.getSessionId() == sessionId).findFirst().orElse(null);
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            String statusText = (String) statusCombo.getSelectedItem();
            SessionStatus status = "ALL".equals(statusText) ? null : SessionStatus.valueOf(statusText);
            String role = "All".equals(roleCombo.getSelectedItem()) ? "ALL"
                    : "Teaching".equals(roleCombo.getSelectedItem()) ? "TEACHING" : "LEARNING";
            return bookingService.sessionsFor(studentId, role, status, 100);
        }, this::render);
    }

    private void render(List<LearningSession> loaded) {
        sessions.clear();
        sessions.addAll(loaded);
        model.setRowCount(0);
        for (LearningSession session : loaded) {
            String confirmed = (session.isTeacherConfirmation() ? "T" : "-")
                    + (session.isLearnerConfirmation() ? "L" : "-");
            model.addRow(new Object[]{
                    session.getSessionId(),
                    DateTimeUtil.format(session.getStartTime()),
                    session.getSkillName(),
                    session.getTeacherName(),
                    session.getLearnerName(),
                    session.getDurationMinutes(),
                    session.getCreditCost(),
                    session.getStatus().name(),
                    confirmed});
        }
    }
}
