package com.skillloop.ui;

import com.skillloop.model.LeaderboardEntry;
import com.skillloop.service.StudentService;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;

/**
 * Leaderboard, ranked by verified learning contribution (never by wallet balance).
 */
public class LeaderboardPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long studentId;
    private final DefaultTableModel model = UiComponents.tableModel(
            new String[]{"Rank", "Student", "Department", "Sessions taught", "Hours", "Learners",
                    "Avg rating", "Credits earned", "Score"});
    private final StudentService studentService = new StudentService();

    public LeaderboardPanel(long studentId) {
        this.studentId = studentId;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    public long getStudentId() {
        return studentId;
    }

    private void buildUi() {
        JPanel card = UiComponents.card("Top contributors");
        JTable table = UiComponents.table(model);
        card.add(UiComponents.scroll(table), BorderLayout.CENTER);
        card.add(UiComponents.muted("Score = sessions x8 (max 40) + hours x2 (max 20) "
                + "+ distinct learners x3 (max 15) + rating x3 + credits earned x0.05 (max 10)."),
                BorderLayout.SOUTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 700));
        content.add(card);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> studentService.leaderboard(50), this::render);
    }

    private void render(List<LeaderboardEntry> entries) {
        model.setRowCount(0);
        for (LeaderboardEntry entry : entries) {
            model.addRow(new Object[]{
                    entry.getRank(),
                    entry.getFullName() + (entry.getStudentId() == studentId ? "  (you)" : ""),
                    entry.getDepartment(),
                    entry.getTeachingSessions(),
                    String.format("%.1f", entry.getTeachingHours()),
                    entry.getDistinctLearners(),
                    entry.getAvgRating() > 0 ? String.format("%.2f", entry.getAvgRating()) : "-",
                    entry.getCreditsEarned(),
                    String.format("%.1f", entry.getScore())});
        }
    }
}
