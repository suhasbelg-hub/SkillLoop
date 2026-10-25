package com.skillloop.ui;

import com.skillloop.model.Achievement;
import com.skillloop.model.Student;
import com.skillloop.model.StudentProgress;
import com.skillloop.service.AchievementService;
import com.skillloop.service.AuthService;
import com.skillloop.service.StudentService;
import com.skillloop.util.SessionContext;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.List;

/** My Profile: edit personal data, change the password and review earned achievements. */
public class ProfilePanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long studentId;
    private final Runnable profileListener;
    private final JTextField nameField = UiComponents.textField(22);
    private final JTextField emailField = UiComponents.textField(22);
    private final JTextField departmentField = UiComponents.textField(22);
    private final JSpinner semesterSpinner = UiComponents.spinner(1, 1, 12, 1);
    private final JTextArea bioArea = UiComponents.textArea(4, 22);
    private final JPanel achievementsPanel = new JPanel(new GridLayout(0, 2, 10, 10));
    private final JPanel analyticsPanel = new JPanel(new GridLayout(2, 4, 10, 10));

    private final StudentService studentService = new StudentService();
    private final AuthService authService = new AuthService();
    private final AchievementService achievementService = new AchievementService();

    public ProfilePanel(long studentId, Runnable profileListener) {
        this.studentId = studentId;
        this.profileListener = profileListener;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    private void buildUi() {
        JPanel left = UiComponents.card("Profile details");
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);

        emailField.setEditable(false);
        emailField.setToolTipText("The college email cannot be changed.");

        form.add(UiComponents.formRow("Full name", nameField));
        form.add(Box.createVerticalStrut(10));
        form.add(UiComponents.formRow("Email", emailField));
        form.add(Box.createVerticalStrut(10));
        form.add(UiComponents.formRow("Department", departmentField));
        form.add(Box.createVerticalStrut(10));
        form.add(UiComponents.formRow("Semester", semesterSpinner));
        form.add(Box.createVerticalStrut(10));
        form.add(UiComponents.formRow("Short bio", UiComponents.scroll(bioArea)));
        form.add(Box.createVerticalStrut(16));

        JButton saveButton = UiComponents.button("Save profile", UiComponents.ButtonStyle.PRIMARY);
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.add(saveButton);
        form.add(actions);
        saveButton.addActionListener(event -> save());
        left.add(form, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setOpaque(false);

        JPanel analyticsCard = UiComponents.card("Progress analytics");
        analyticsPanel.setOpaque(false);
        analyticsCard.add(analyticsPanel, BorderLayout.CENTER);
        right.add(analyticsCard);
        right.add(Box.createVerticalStrut(12));

        JPanel achievementsCard = UiComponents.card("Achievements");
        achievementsPanel.setOpaque(false);
        achievementsPanel.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        achievementsCard.add(achievementsPanel, BorderLayout.CENTER);
        right.add(achievementsCard);
        right.add(Box.createVerticalStrut(12));

        JPanel passwordCard = UiComponents.card("Change password");
        JPasswordField currentPassword = UiComponents.passwordField(18);
        JPasswordField newPassword = UiComponents.passwordField(18);
        JPasswordField confirmPassword = UiComponents.passwordField(18);
        JPanel passwordForm = new JPanel();
        passwordForm.setLayout(new BoxLayout(passwordForm, BoxLayout.Y_AXIS));
        passwordForm.setOpaque(false);
        passwordForm.add(UiComponents.formRow("Current", currentPassword));
        passwordForm.add(Box.createVerticalStrut(8));
        passwordForm.add(UiComponents.formRow("New", newPassword));
        passwordForm.add(Box.createVerticalStrut(8));
        passwordForm.add(UiComponents.formRow("Confirm", confirmPassword));
        passwordForm.add(Box.createVerticalStrut(12));
        JButton changeButton = UiComponents.button("Update password", UiComponents.ButtonStyle.PRIMARY);
        JPanel passwordActions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        passwordActions.setOpaque(false);
        passwordActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordActions.add(changeButton);
        passwordForm.add(passwordActions);
        passwordCard.add(passwordForm, BorderLayout.CENTER);
        changeButton.addActionListener(event -> {
            String oldPassword = new String(currentPassword.getPassword());
            String fresh = new String(newPassword.getPassword());
            String confirmation = new String(confirmPassword.getPassword());
            AsyncRunner.run(this, () -> {
                authService.changePassword(studentId, oldPassword, fresh, confirmation);
                return Boolean.TRUE;
            }, result -> {
                currentPassword.setText("");
                newPassword.setText("");
                confirmPassword.setText("");
                Dialogs.info(this, "Your password was updated.");
            });
        });
        right.add(passwordCard);

        JPanel split = new JPanel(new GridLayout(1, 2, 12, 12));
        split.setOpaque(false);
        split.add(left);
        split.add(right);
        add(split, BorderLayout.CENTER);
    }

    private void save() {
        String name = nameField.getText();
        String department = departmentField.getText();
        int semester = (Integer) semesterSpinner.getValue();
        String bio = bioArea.getText();
        AsyncRunner.run(this, () -> studentService.updateProfile(studentId, name, department, semester, bio),
                student -> {
                    Dialogs.info(this, "Profile updated.");
                    if (profileListener != null) {
                        profileListener.run();
                    }
                    SessionContext.refresh(student);
                });
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            ProfileData data = new ProfileData();
            data.student = studentService.findById(studentId).orElse(null);
            data.progress = studentService.getProgress(studentId);
            data.achievements = achievementService.listForStudent(studentId);
            data.available = achievementService.listAll();
            return data;
        }, this::render);
    }

    private void render(ProfileData data) {
        if (data.student != null) {
            Student student = data.student;
            nameField.setText(student.getFullName());
            emailField.setText(student.getEmail());
            departmentField.setText(student.getDepartment());
            semesterSpinner.setValue(student.getSemester());
            bioArea.setText(student.getBio() == null ? "" : student.getBio());
        }
        StudentProgress progress = data.progress;
        analyticsPanel.removeAll();
        analyticsPanel.add(UiComponents.statCard("Teaching sessions",
                String.valueOf(progress.getTeachingSessions()), AppTheme.NAVY));
        analyticsPanel.add(UiComponents.statCard("Learning sessions",
                String.valueOf(progress.getLearningSessions()), AppTheme.ACCENT_DARK));
        analyticsPanel.add(UiComponents.statCard("Teaching hours",
                String.format("%.1f h", progress.getTeachingHours()), AppTheme.INFO));
        analyticsPanel.add(UiComponents.statCard("Learning hours",
                String.format("%.1f h", progress.getLearningHours()), AppTheme.SUCCESS));
        analyticsPanel.add(UiComponents.statCard("Credits earned",
                String.valueOf(progress.getCreditsEarned()), AppTheme.SUCCESS));
        analyticsPanel.add(UiComponents.statCard("Credits spent",
                String.valueOf(progress.getCreditsSpent()), AppTheme.WARNING));
        analyticsPanel.add(UiComponents.statCard("Skills offered",
                String.valueOf(progress.getOfferedSkills()), AppTheme.NAVY_LIGHT));
        analyticsPanel.add(UiComponents.statCard("Skills wanted",
                String.valueOf(progress.getWantedSkills()), AppTheme.ACCENT));
        analyticsPanel.revalidate();
        analyticsPanel.repaint();

        achievementsPanel.removeAll();
        for (Achievement achievement : data.available) {
            boolean earned = data.achievements.stream()
                    .anyMatch(a -> a.getAchievementId() == achievement.getAchievementId());
            JPanel badge = new JPanel(new BorderLayout(6, 2));
            badge.setBackground(earned ? new java.awt.Color(0xEC, 0xF7, 0xF1) : AppTheme.BACKGROUND);
            badge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(earned ? AppTheme.SUCCESS : AppTheme.BORDER),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            JLabel title = new JLabel(achievement.getIcon() + "  " + achievement.getName()
                    + (earned ? "  [earned]" : ""));
            title.setFont(AppTheme.FONT_SMALL_BOLD);
            title.setForeground(earned ? AppTheme.SUCCESS : AppTheme.TEXT_MUTED);
            JLabel description = UiComponents.muted(achievement.getDescription());
            badge.add(title, BorderLayout.NORTH);
            badge.add(description, BorderLayout.CENTER);
            achievementsPanel.add(badge);
        }
        achievementsPanel.revalidate();
        achievementsPanel.repaint();
    }

    /** Snapshot loaded in one background task. */
    private static final class ProfileData {
        private Student student;
        private StudentProgress progress = new StudentProgress();
        private List<Achievement> achievements = List.of();
        private List<Achievement> available = List.of();
    }
}
