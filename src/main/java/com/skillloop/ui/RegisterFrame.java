package com.skillloop.ui;

import com.skillloop.model.Student;
import com.skillloop.service.AuthService;
import com.skillloop.util.InputValidator;
import com.skillloop.util.PasswordUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;

/** Registration screen: validated input, BCrypt hashing, wallet creation. */
public class RegisterFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final JTextField nameField = UiComponents.textField(20);
    private final JTextField emailField = UiComponents.textField(20);
    private final JPasswordField passwordField = UiComponents.passwordField(20);
    private final JPasswordField confirmField = UiComponents.passwordField(20);
    private final JTextField departmentField = UiComponents.textField(20);
    private final JSpinner semesterSpinner = UiComponents.spinner(1, 1, 12, 1);
    private final JLabel requirementLabel = UiComponents.muted(
            "Password: at least 8 characters, with letters and digits.");
    private final AuthService authService = new AuthService();
    private final LoginFrame loginFrame;

    public RegisterFrame(LoginFrame loginFrame) {
        super("SkillLoop - Create your account");
        this.loginFrame = loginFrame;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(560, 720));
        setLocationRelativeTo(null);
        buildUi();
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.BACKGROUND);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppTheme.NAVY_DARK);
        header.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JLabel appName = new JLabel("SkillLoop");
        appName.setFont(new java.awt.Font(AppTheme.FONT_FAMILY, java.awt.Font.BOLD, 26));
        appName.setForeground(Color.WHITE);
        JLabel subtitle = new JLabel("Create your student account");
        subtitle.setFont(AppTheme.FONT_REGULAR);
        subtitle.setForeground(new Color(0xB9, 0xCB, 0xE0));
        header.add(appName, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);

        JPanel form = UiComponents.card(null);
        form.setBackground(Color.WHITE);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        form.add(UiComponents.formRow("Full name", nameField));
        form.add(Box.createVerticalStrut(12));
        form.add(UiComponents.formRow("College email", emailField));
        form.add(Box.createVerticalStrut(12));
        form.add(UiComponents.formRow("Password", passwordField));
        form.add(Box.createVerticalStrut(4));
        JPanel requirementPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        requirementPanel.setOpaque(false);
        requirementPanel.add(requirementLabel);
        requirementPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(requirementPanel);
        form.add(Box.createVerticalStrut(8));
        form.add(UiComponents.formRow("Confirm password", confirmField));
        form.add(Box.createVerticalStrut(12));
        form.add(UiComponents.formRow("Department", departmentField));
        form.add(Box.createVerticalStrut(12));
        form.add(UiComponents.formRow("Semester", semesterSpinner));
        form.add(Box.createVerticalStrut(22));

        JButton registerButton = UiComponents.button("Create account", UiComponents.ButtonStyle.PRIMARY);
        JButton backButton = UiComponents.button("Back to sign in", UiComponents.ButtonStyle.LINK);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.add(registerButton);
        actions.add(backButton);
        form.add(actions);
        form.add(Box.createVerticalGlue());

        registerButton.addActionListener(event -> doRegister());
        backButton.addActionListener(event -> {
            loginFrame.setVisible(true);
            dispose();
        });
        getRootPane().setDefaultButton(registerButton);

        root.add(header, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        setContentPane(root);
    }

    private void doRegister() {
        String name = nameField.getText();
        String email = emailField.getText();
        String password = new String(passwordField.getPassword());
        String confirmation = new String(confirmField.getPassword());
        String department = departmentField.getText();
        int semester = (Integer) semesterSpinner.getValue();

        List<String> problems = InputValidator.registrationProblems(name, email, password, confirmation,
                department, semester);
        if (!problems.isEmpty()) {
            Dialogs.error(this, AsyncRunner.bulletList(problems));
            return;
        }
        if (!PasswordUtil.isStrongEnough(password)) {
            Dialogs.error(this, "Please choose a stronger password.");
            return;
        }

        AsyncRunner.run(this, () -> authService.register(name, email, password, confirmation, department, semester),
                this::onRegistered);
    }

    private void onRegistered(Student student) {
        Dialogs.info(this, "Account created",
                "Welcome, " + student.getFullName() + "!\n\n"
                        + "Your wallet was created and the one-time starter credit was added, so you can book "
                        + "your first session right away.\n\nYou can now sign in.");
        loginFrame.setVisible(true);
        dispose();
    }
}
