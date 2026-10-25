package com.skillloop.ui;

import com.skillloop.model.CreditTransaction;
import com.skillloop.model.LearningSession;
import com.skillloop.model.MatchResult;
import com.skillloop.model.Notification;
import com.skillloop.model.StudentProgress;
import com.skillloop.service.BookingService;
import com.skillloop.service.CreditService;
import com.skillloop.service.MatchingService;
import com.skillloop.service.NotificationService;
import com.skillloop.service.StudentService;
import com.skillloop.util.DateTimeUtil;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.Box;

/** Student dashboard: live numbers from the database, recommendations, activity and inbox. */
public class HomePanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long studentId;
    private final Runnable walletListener;
    private final JPanel statsRow = new JPanel(new GridLayout(1, 5, 12, 12));
    private final DefaultTableModel recommendationModel =
            UiComponents.tableModel(new String[]{"Recommended peer", "Skill", "Score", "Why"});
    private final DefaultTableModel sessionModel =
            UiComponents.tableModel(new String[]{"When", "Skill", "With", "Status"});
    private final DefaultTableModel transactionModel =
            UiComponents.tableModel(new String[]{"When", "Description", "Amount"});
    private final DefaultTableModel notificationModel =
            UiComponents.tableModel(new String[]{"When", "Title", "Message"});

    private final StudentService studentService = new StudentService();
    private final CreditService creditService = new CreditService();
    private final BookingService bookingService = new BookingService();
    private final MatchingService matchingService = new MatchingService();
    private final NotificationService notificationService = new NotificationService();

    public HomePanel(long studentId, Runnable walletListener) {
        this.studentId = studentId;
        this.walletListener = walletListener;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    private void buildUi() {
        statsRow.setOpaque(false);
        statsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
        add(statsRow, BorderLayout.NORTH);

        JPanel middle = new JPanel(new GridLayout(1, 2, 12, 12));
        middle.setOpaque(false);

        JPanel upcomingCard = UiComponents.card("Upcoming sessions");
        JTable sessionTable = UiComponents.table(sessionModel);
        upcomingCard.add(UiComponents.scroll(sessionTable), BorderLayout.CENTER);
        middle.add(upcomingCard);

        JPanel recommendationCard = UiComponents.card("Recommended peers");
        JTable recommendationTable = UiComponents.table(recommendationModel);
        recommendationCard.add(UiComponents.scroll(recommendationTable), BorderLayout.CENTER);
        middle.add(recommendationCard);

        JPanel bottom = new JPanel(new GridLayout(1, 2, 12, 12));
        bottom.setOpaque(false);
        bottom.setPreferredSize(new Dimension(100, 220));

        JPanel transactionCard = UiComponents.card("Recent credit transactions");
        transactionCard.add(UiComponents.scroll(UiComponents.table(transactionModel)), BorderLayout.CENTER);
        bottom.add(transactionCard);

        JPanel notificationCard = UiComponents.card("Notifications");
        JPanel notificationWrapper = new JPanel(new BorderLayout(0, 6));
        notificationWrapper.setOpaque(false);
        JButton markReadButton = UiComponents.button("Mark all as read", UiComponents.ButtonStyle.LINK);
        JPanel notificationActions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
        notificationActions.setOpaque(false);
        notificationActions.add(markReadButton);
        notificationWrapper.add(UiComponents.scroll(UiComponents.table(notificationModel)), BorderLayout.CENTER);
        notificationWrapper.add(notificationActions, BorderLayout.SOUTH);
        notificationCard.add(notificationWrapper, BorderLayout.CENTER);
        markReadButton.addActionListener(event -> AsyncRunner.runVoid(this,
                () -> notificationService.markAllRead(studentId), this::refresh));
        bottom.add(notificationCard);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(middle);
        content.add(Box.createVerticalStrut(12));
        content.add(bottom);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            DashboardData data = new DashboardData();
            data.progress = studentService.getProgress(studentId);
            data.balance = creditService.availableBalance(studentId);
            data.upcoming = bookingService.upcoming(studentId, 6);
            data.recommendations = matchingService.findReciprocalPartners(studentId, 8);
            if (data.recommendations.isEmpty()) {
                data.recommendations = matchingService.recommendTeachers(studentId, null, null, 8);
            }
            data.transactions = creditService.history(studentId, 6);
            data.notifications = notificationService.listFor(studentId, 8);
            return data;
        }, this::render, () -> renderEmpty());
        if (walletListener != null) {
            walletListener.run();
        }
    }

    private void render(DashboardData data) {
        statsRow.removeAll();
        statsRow.add(UiComponents.statCard("Available credits", String.valueOf(data.balance), AppTheme.NAVY));
        statsRow.add(UiComponents.statCard("Teaching hours", DateTimeUtil.formatHours(data.progress.getTeachingHours()),
                AppTheme.ACCENT_DARK));
        statsRow.add(UiComponents.statCard("Learning hours", DateTimeUtil.formatHours(data.progress.getLearningHours()),
                AppTheme.INFO));
        statsRow.add(UiComponents.statCard("Completed sessions",
                String.valueOf(data.progress.getTeachingSessions() + data.progress.getLearningSessions()),
                AppTheme.SUCCESS));
        statsRow.add(UiComponents.statCard("Average rating",
                data.progress.getAvgRating() + " (" + data.progress.getReviewCount() + ")",
                data.progress.getAvgRating() >= 4.0 ? AppTheme.SUCCESS : AppTheme.WARNING));
        statsRow.revalidate();
        statsRow.repaint();

        fillSessions(data.upcoming);
        fillRecommendations(data.recommendations);
        fillTransactions(data.transactions);
        fillNotifications(data.notifications);
    }

    private void renderEmpty() {
        statsRow.removeAll();
        statsRow.add(UiComponents.statCard("Available credits", "-", AppTheme.NAVY));
        statsRow.add(UiComponents.statCard("Teaching hours", "-", AppTheme.ACCENT_DARK));
        statsRow.add(UiComponents.statCard("Learning hours", "-", AppTheme.INFO));
        statsRow.add(UiComponents.statCard("Completed sessions", "-", AppTheme.SUCCESS));
        statsRow.add(UiComponents.statCard("Average rating", "-", AppTheme.WARNING));
        statsRow.revalidate();
        statsRow.repaint();
    }

    private void fillSessions(List<LearningSession> sessions) {
        sessionModel.setRowCount(0);
        for (LearningSession session : sessions) {
            boolean teaching = session.getTeacherId() == studentId;
            sessionModel.addRow(new Object[]{
                    DateTimeUtil.format(session.getStartTime()),
                    session.getSkillName(),
                    (teaching ? "Learner: " : "Teacher: ") + (teaching ? session.getLearnerName() : session.getTeacherName()),
                    session.getStatus().name()});
        }
    }

    private void fillRecommendations(List<MatchResult> matches) {
        recommendationModel.setRowCount(0);
        for (MatchResult match : matches) {
            recommendationModel.addRow(new Object[]{
                    match.getTeacherName() + " (" + match.getDepartment() + ")",
                    match.getSkillName() + " - " + match.getProficiency().name().toLowerCase(),
                    match.scorePercent() + " / 100" + (match.isReciprocal() ? "  [swap]" : ""),
                    match.explain()});
        }
    }

    private void fillTransactions(List<CreditTransaction> transactions) {
        transactionModel.setRowCount(0);
        for (CreditTransaction transaction : transactions) {
            transactionModel.addRow(new Object[]{
                    DateTimeUtil.format(transaction.getCreatedAt()),
                    transaction.getDescription(),
                    (transaction.getAmount() > 0 ? "+" : "") + transaction.getAmount()});
        }
    }

    private void fillNotifications(List<Notification> notifications) {
        notificationModel.setRowCount(0);
        for (Notification notification : notifications) {
            notificationModel.addRow(new Object[]{
                    DateTimeUtil.format(notification.getCreatedAt()),
                    (notification.isRead() ? "" : "* ") + notification.getTitle(),
                    notification.getMessage()});
        }
    }

    /** Snapshot of everything the dashboard shows (loaded in one background task). */
    private static final class DashboardData {
        private StudentProgress progress = new StudentProgress();
        private int balance;
        private List<LearningSession> upcoming = List.of();
        private List<MatchResult> recommendations = List.of();
        private List<CreditTransaction> transactions = List.of();
        private List<Notification> notifications = List.of();
    }

    /** Used by the dashboard to give the tables a sensible minimum size. */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1000, 700);
    }

    /** Alignment helper so the cards start at the left edge. */
    @Override
    public void setAlignmentX(float alignmentX) {
        super.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
}
