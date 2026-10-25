package com.skillloop.ui;

import com.skillloop.model.LearningSession;
import com.skillloop.model.Review;
import com.skillloop.service.ReviewService;
import com.skillloop.util.DateTimeUtil;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/** Ratings and reviews: review completed sessions and read the feedback received. */
public class ReviewsPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long studentId;
    private final DefaultTableModel pendingModel =
            UiComponents.tableModel(new String[]{"#", "When", "Skill", "Partner", "Role"});
    private final DefaultTableModel receivedModel =
            UiComponents.tableModel(new String[]{"When", "From", "Session", "Rating", "Comment"});
    private final JTable pendingTable = UiComponents.table(pendingModel);
    private final JComboBox<Integer> ratingCombo = UiComponents.comboBox(new Integer[]{5, 4, 3, 2, 1});
    private final JTextArea commentArea = UiComponents.textArea(3, 30);
    private final List<LearningSession> pending = new ArrayList<>();

    private final ReviewService reviewService = new ReviewService();

    public ReviewsPanel(long studentId) {
        this.studentId = studentId;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    private void buildUi() {
        JPanel left = UiComponents.card("Sessions waiting for your review");
        left.add(UiComponents.scroll(pendingTable), BorderLayout.CENTER);
        JPanel formPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        formPanel.setOpaque(false);
        JButton submitButton = UiComponents.button("Submit review", UiComponents.ButtonStyle.PRIMARY);
        formPanel.add(UiComponents.muted("Rating"));
        formPanel.add(ratingCombo);
        formPanel.add(submitButton);
        left.add(formPanel, BorderLayout.SOUTH);
        left.add(UiComponents.scroll(commentArea), BorderLayout.EAST);

        JPanel right = UiComponents.card("Reviews you received");
        right.add(UiComponents.scroll(UiComponents.table(receivedModel)), BorderLayout.CENTER);

        submitButton.addActionListener(event -> {
            int row = pendingTable.getSelectedRow();
            if (row < 0) {
                Dialogs.warning(this, "Please select a session to review.");
                return;
            }
            long sessionId = (Long) pendingModel.getValueAt(row, 0);
            int rating = (Integer) ratingCombo.getSelectedItem();
            String comment = commentArea.getText();
            AsyncRunner.run(this, () -> reviewService.submit(studentId, sessionId, rating, comment), review -> {
                commentArea.setText("");
                Dialogs.info(this, "Thank you - your review was saved.");
                refresh();
            });
        });

        JPanel split = new JPanel(new GridLayout(1, 2, 12, 12));
        split.setOpaque(false);
        split.add(left);
        split.add(right);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        split.setMaximumSize(new Dimension(Integer.MAX_VALUE, 640));
        content.add(split);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            ReviewsData data = new ReviewsData();
            data.pending = reviewService.reviewableSessions(studentId);
            data.received = reviewService.reviewsFor(studentId, 50);
            return data;
        }, this::render);
    }

    private void render(ReviewsData data) {
        pending.clear();
        pending.addAll(data.pending);
        pendingModel.setRowCount(0);
        for (LearningSession session : data.pending) {
            boolean teacher = session.getTeacherId() == studentId;
            pendingModel.addRow(new Object[]{
                    session.getSessionId(),
                    DateTimeUtil.format(session.getStartTime()),
                    session.getSkillName(),
                    teacher ? session.getLearnerName() : session.getTeacherName(),
                    teacher ? "you taught" : "you learned"});
        }
        receivedModel.setRowCount(0);
        for (Review review : data.received) {
            receivedModel.addRow(new Object[]{
                    DateTimeUtil.format(review.getCreatedAt()),
                    review.getReviewerName(),
                    "#" + review.getSessionId(),
                    review.stars(),
                    review.getComment()});
        }
    }

    /** Snapshot loaded in one background task. */
    private static final class ReviewsData {
        private List<LearningSession> pending = List.of();
        private List<Review> received = List.of();
    }
}
