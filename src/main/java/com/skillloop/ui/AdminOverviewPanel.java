package com.skillloop.ui;

import com.skillloop.model.AdminStats;
import com.skillloop.model.CreditTransaction;
import com.skillloop.model.LeaderboardEntry;
import com.skillloop.model.MonthlyVolume;
import com.skillloop.model.Skill;
import com.skillloop.service.AdminService;
import com.skillloop.util.DateTimeUtil;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;

/** Administrator overview: platform counters, popular skills, active teachers and volume. */
public class AdminOverviewPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long adminId;
    private final JPanel statsRow = new JPanel(new GridLayout(2, 5, 10, 10));
    private final DefaultTableModel skillModel =
            UiComponents.tableModel(new String[]{"Skill", "Category", "Teachers", "Learners", "Sessions"});
    private final DefaultTableModel teacherModel =
            UiComponents.tableModel(new String[]{"Teacher", "Department", "Sessions", "Hours", "Learners", "Rating", "Credits"});
    private final DefaultTableModel volumeModel =
            UiComponents.tableModel(new String[]{"Period", "Type", "Transactions", "Credited", "Debited"});
    private final DefaultTableModel transactionModel =
            UiComponents.tableModel(new String[]{"When", "Student", "Type", "Amount", "Reference", "Details"});
    private final AdminService adminService = new AdminService();

    public AdminOverviewPanel(long adminId) {
        this.adminId = adminId;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    private void buildUi() {
        statsRow.setOpaque(false);
        statsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        JPanel middle = new JPanel(new GridLayout(1, 2, 12, 12));
        middle.setOpaque(false);
        JPanel skillCard = UiComponents.card("Popular skills");
        skillCard.add(UiComponents.scroll(UiComponents.table(skillModel)), BorderLayout.CENTER);
        JPanel teacherCard = UiComponents.card("Most active teachers");
        teacherCard.add(UiComponents.scroll(UiComponents.table(teacherModel)), BorderLayout.CENTER);
        middle.add(skillCard);
        middle.add(teacherCard);

        JPanel bottom = new JPanel(new GridLayout(1, 2, 12, 12));
        bottom.setOpaque(false);
        JPanel volumeCard = UiComponents.card("Monthly credit volume");
        volumeCard.add(UiComponents.scroll(UiComponents.table(volumeModel)), BorderLayout.CENTER);
        JPanel transactionCard = UiComponents.card("Latest credit transactions");
        transactionCard.add(UiComponents.scroll(UiComponents.table(transactionModel)), BorderLayout.CENTER);
        bottom.add(volumeCard);
        bottom.add(transactionCard);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(statsRow);
        content.add(Box.createVerticalStrut(12));
        content.add(middle);
        content.add(Box.createVerticalStrut(12));
        content.add(bottom);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            Data data = new Data();
            data.stats = adminService.stats(adminId);
            data.skills = adminService.popularSkills(adminId, 20);
            data.teachers = adminService.mostActiveTeachers(adminId, 20);
            data.volumes = adminService.monthlyVolume(adminId, 12);
            data.transactions = adminService.recentTransactions(adminId, 25);
            return data;
        }, this::render);
    }

    private void render(Data data) {
        AdminStats stats = data.stats;
        statsRow.removeAll();
        statsRow.add(UiComponents.statCard("Students", String.valueOf(stats.getTotalStudents()), AppTheme.NAVY));
        statsRow.add(UiComponents.statCard("Active students", String.valueOf(stats.getActiveStudents()), AppTheme.SUCCESS));
        statsRow.add(UiComponents.statCard("Suspended", String.valueOf(stats.getSuspendedStudents()), AppTheme.WARNING));
        statsRow.add(UiComponents.statCard("Administrators", String.valueOf(stats.getAdministrators()), AppTheme.NAVY_LIGHT));
        statsRow.add(UiComponents.statCard("Skills", String.valueOf(stats.getTotalSkills()), AppTheme.ACCENT_DARK));
        statsRow.add(UiComponents.statCard("Active listings", String.valueOf(stats.getTotalListings()), AppTheme.ACCENT));
        statsRow.add(UiComponents.statCard("Completed sessions", String.valueOf(stats.getCompletedSessions()), AppTheme.SUCCESS));
        statsRow.add(UiComponents.statCard("Pending bookings", String.valueOf(stats.getPendingBookings()), AppTheme.WARNING));
        statsRow.add(UiComponents.statCard("Disputed sessions", String.valueOf(stats.getDisputedSessions()), AppTheme.DANGER));
        statsRow.add(UiComponents.statCard("Credits transferred", String.valueOf(stats.getCreditsTransferred()), AppTheme.NAVY));
        statsRow.revalidate();
        statsRow.repaint();

        skillModel.setRowCount(0);
        for (Skill skill : data.skills) {
            skillModel.addRow(new Object[]{skill.getSkillName(), skill.getCategory(),
                    skill.getTeachersOffering(), skill.getLearnersWanting(), skill.getCompletedSessions()});
        }
        teacherModel.setRowCount(0);
        for (LeaderboardEntry entry : data.teachers) {
            teacherModel.addRow(new Object[]{entry.getFullName(), entry.getDepartment(),
                    entry.getTeachingSessions(), String.format("%.1f", entry.getTeachingHours()),
                    entry.getDistinctLearners(),
                    entry.getAvgRating() > 0 ? String.format("%.2f", entry.getAvgRating()) : "-",
                    entry.getCreditsEarned()});
        }
        volumeModel.setRowCount(0);
        for (MonthlyVolume volume : data.volumes) {
            volumeModel.addRow(new Object[]{volume.getPeriod(), volume.getTransactionType(),
                    volume.getTransactionCount(), volume.getCreditsCredited(), volume.getCreditsDebited()});
        }
        transactionModel.setRowCount(0);
        for (CreditTransaction transaction : data.transactions) {
            transactionModel.addRow(new Object[]{DateTimeUtil.format(transaction.getCreatedAt()),
                    transaction.getStudentName(), transaction.getType().name(), transaction.getAmount(),
                    transaction.getReference(), transaction.getDescription()});
        }
    }

    /** Snapshot loaded in one background task. */
    private static final class Data {
        private AdminStats stats = new AdminStats();
        private List<Skill> skills = List.of();
        private List<LeaderboardEntry> teachers = List.of();
        private List<MonthlyVolume> volumes = List.of();
        private List<CreditTransaction> transactions = List.of();
    }
}
