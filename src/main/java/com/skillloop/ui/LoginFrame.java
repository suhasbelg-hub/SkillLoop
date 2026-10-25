package com.skillloop.ui;

import com.skillloop.model.Student;
import com.skillloop.service.AuthService;
import com.skillloop.util.AppConfig;
import com.skillloop.util.DBConnection;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;

/**
 * Login screen.
 *
 * <p>Layout: a navy branding panel on the left, a white form on the right. The login runs in a
 * background thread through {@link AsyncRunner}, so the window never freezes.</p>
 */
public class LoginFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final JTextField emailField = UiComponents.textField(22);
    private final JPasswordField passwordField = UiComponents.passwordField(22);
    private final JButton loginButton = UiComponents.button("Sign in", UiComponents.ButtonStyle.PRIMARY);
    private final JButton registerButton = UiComponents.button("Create an account",
            UiComponents.ButtonStyle.SECONDARY);
    private final JButton testButton = UiComponents.button("Test database connection",
            UiComponents.ButtonStyle.LINK);
    private final JLabel statusLabel = UiComponents.muted(" ");
    private final AuthService authService = new AuthService();

    public LoginFrame() {
        super("SkillLoop - Sign in");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 620));
        setLocationRelativeTo(null);
        buildUi();
        wireEvents();
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.BACKGROUND);
        root.add(buildBrandingPanel(), BorderLayout.WEST);
        root.add(buildFormPanel(), BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel buildBrandingPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(AppTheme.NAVY_DARK);
        panel.setBorder(BorderFactory.createEmptyBorder(50, 45, 50, 45));
        panel.setPreferredSize(new Dimension(400, 100));

        JLabel appName = new JLabel("SkillLoop");
        appName.setFont(new Font(AppTheme.FONT_FAMILY, Font.BOLD, 40));
        appName.setForeground(Color.WHITE);
        appName.setAlignmentX(Component_LEFT);

        JLabel tagline = new JLabel("A Credit-Based Peer Learning Platform");
        tagline.setFont(AppTheme.FONT_REGULAR);
        tagline.setForeground(new Color(0xB9, 0xCB, 0xE0));
        tagline.setAlignmentX(Component_LEFT);

        panel.add(appName);
        panel.add(Box.createVerticalStrut(10));
        panel.add(tagline);
        panel.add(Box.createVerticalStrut(35));
        panel.add(bullet("Teach what you know and earn credits"));
        panel.add(Box.createVerticalStrut(10));
        panel.add(bullet("Spend credits to learn from other students"));
        panel.add(Box.createVerticalStrut(10));
        panel.add(bullet("Verified sessions, transparent credit ledger"));
        panel.add(Box.createVerticalStrut(10));
        panel.add(bullet("Reciprocal and one-to-one learning matches"));
        panel.add(Box.createVerticalGlue());

        JLabel lookAndFeel = new JLabel("Look and feel: " + AppTheme.activeLookAndFeel());
        lookAndFeel.setFont(AppTheme.FONT_SMALL);
        lookAndFeel.setForeground(new Color(0x8F, 0xA8, 0xC2));
        lookAndFeel.setAlignmentX(Component_LEFT);
        panel.add(lookAndFeel);
        return panel;
    }

    private static final float Component_LEFT = 0f;

    private JLabel bullet(String text) {
        JLabel label = new JLabel("-  " + text);
        label.setFont(AppTheme.FONT_REGULAR);
        label.setForeground(new Color(0xD6, 0xE2, 0xF0));
        label.setAlignmentX(Component_LEFT);
        return label;
    }

    private JPanel buildFormPanel() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(AppTheme.BACKGROUND);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        JPanel form = UiComponents.card(null);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER),
                BorderFactory.createEmptyBorder(28, 28, 28, 28)));
        form.setPreferredSize(new Dimension(430, 420));
        form.setMaximumSize(new Dimension(430, 460));
        form.setBackground(Color.WHITE);

        JLabel welcome = UiComponents.title("Welcome back");
        welcome.setAlignmentX(Component_LEFT);
        JLabel hint = UiComponents.muted("Sign in with your college email address.");
        hint.setAlignmentX(Component_LEFT);

        form.add(welcome);
        form.add(Box.createVerticalStrut(6));
        form.add(hint);
        form.add(Box.createVerticalStrut(24));
        form.add(UiComponents.formRow("College email", emailField));
        form.add(Box.createVerticalStrut(12));
        form.add(UiComponents.formRow("Password", passwordField));
        form.add(Box.createVerticalStrut(22));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.add(loginButton);
        actions.add(Box.createHorizontalStrut(10));
        actions.add(registerButton);
        actions.setAlignmentX(Component_LEFT);
        form.add(actions);
        form.add(Box.createVerticalStrut(14));
        statusLabel.setAlignmentX(Component_LEFT);
        form.add(statusLabel);
        form.add(Box.createVerticalGlue());
        form.add(testButton);

        wrapper.add(form, gbc);
        return wrapper;
    }

    private void wireEvents() {
        getRootPane().setDefaultButton(loginButton);
        loginButton.addActionListener(event -> doLogin());
        passwordField.addActionListener(event -> doLogin());
        registerButton.addActionListener(event -> {
            RegisterFrame registerFrame = new RegisterFrame(this);
            registerFrame.setVisible(true);
            setVisible(false);
        });
        testButton.addActionListener(event -> testConnection());
    }

    private void testConnection() {
        statusLabel.setForeground(AppTheme.TEXT_MUTED);
        statusLabel.setText("Testing connection...");
        AsyncRunner.run(this, DBConnection::testConnection, result -> {
            if (result.isSuccess()) {
                statusLabel.setForeground(AppTheme.SUCCESS);
                statusLabel.setText("Connected: MySQL " + result.getServerVersion()
                        + " (" + result.getLatencyMillis() + " ms)");
            } else {
                statusLabel.setForeground(AppTheme.DANGER);
                statusLabel.setText("Connection failed - see the dialog for details.");
                Dialogs.databaseUnavailable(this, result.getMessage());
            }
        });
    }

    private void doLogin() {
        String email = emailField.getText();
        String password = new String(passwordField.getPassword());
        if (email.isBlank() || password.isEmpty()) {
            Dialogs.warning(this, "Please enter your email address and password.");
            return;
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        loginButton.setEnabled(false);
        AsyncRunner.run(this, () -> authService.login(email, password), this::onLoginSuccess,
                () -> {
                    setCursor(Cursor.getDefaultCursor());
                    loginButton.setEnabled(true);
                });
    }

    private void onLoginSuccess(Student student) {
        setCursor(Cursor.getDefaultCursor());
        loginButton.setEnabled(true);
        passwordField.setText("");
        openMainScreen(student);
    }

    private void openMainScreen(Student student) {
        DashboardFrame dashboard = new DashboardFrame(student);
        dashboard.setVisible(true);
        dispose();
    }

    /** Called by {@link Main} before the frame is shown, so the user sees problems early. */
    public void checkDatabase() {
        DBConnection.TestResult result = DBConnection.testConnection();
        if (!result.isSuccess()) {
            statusLabel.setForeground(AppTheme.DANGER);
            statusLabel.setText("Database unreachable - " + AppConfig.get().maskedDescription());
        } else {
            statusLabel.setForeground(AppTheme.SUCCESS);
            statusLabel.setText("Connected to " + result.getDatabaseName()
                    + " (MySQL " + result.getServerVersion() + ")");
        }
    }

    /** Closes every SkillLoop window (used by the logout action). */
    public static void closeAllAndExit(Window current) {
        if (current != null) {
            current.dispose();
        }
        for (Window window : Window.getWindows()) {
            if (window.isShowing()) {
                window.dispose();
            }
        }
        Toolkit.getDefaultToolkit().getSystemEventQueue();
    }
}
