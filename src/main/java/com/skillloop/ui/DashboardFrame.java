package com.skillloop.ui;

import com.skillloop.model.Student;
import com.skillloop.service.AuthService;
import com.skillloop.service.CreditService;
import com.skillloop.util.SessionContext;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Main application window: a navy sidebar for navigation and a card layout that hosts the
 * individual screens. Every screen implements {@link Refreshable} and is refreshed whenever it
 * becomes visible, so the data shown always comes from the database.
 */
public class DashboardFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final Student student;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private final JLabel creditLabel = new JLabel("Credits: -");
    private final JLabel pageTitle = new JLabel("Dashboard");
    private final JButton adminButton = UiComponents.button("Administrator console",
            UiComponents.ButtonStyle.SECONDARY);
    private AdminDashboardFrame adminFrame;

    /** A screen = a panel plus its navigation label. */
    private record Screen(String key, String title, JPanel panel) {
    }

    public DashboardFrame(Student student) {
        super("SkillLoop - " + student.getFullName());
        this.student = student;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1360, 860);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(1180, 720));
        registerScreens();
        buildUi();
        showScreen("dashboard");
        refreshWalletBadge();
    }

    private void registerScreens() {
        long id = student.getStudentId();
        addScreen("dashboard", "Dashboard", new HomePanel(id, this::refreshWalletBadge));
        addScreen("profile", "My Profile", new ProfilePanel(id, this::refreshWalletBadge));
        addScreen("skills", "My Skills", new SkillsPanel(id));
        addScreen("explore", "Explore Skills", new ExplorePanel(id));
        addScreen("matching", "Find Learning Partners", new MatchingPanel(id, this::openBooking));
        addScreen("booking", "Book a Session", new BookingPanel(id, this::refreshWalletBadge));
        addScreen("sessions", "My Sessions", new SessionsPanel(id, this::refreshWalletBadge));
        addScreen("wallet", "Credit Wallet", new WalletPanel(id, this::refreshWalletBadge));
        addScreen("reviews", "Ratings & Reviews", new ReviewsPanel(id));
        addScreen("resources", "Learning Resources", new ResourcesPanel(id));
        addScreen("notifications", "Notifications", new NotificationsPanel(id, this::refreshWalletBadge));
        addScreen("leaderboard", "Leaderboard", new LeaderboardPanel(id));
        addScreen("groups", "Group Sessions", new GroupPanel(id, this::refreshWalletBadge));
    }

    private void addScreen(String key, String title, JPanel panel) {
        screens.put(key, new Screen(key, title, panel));
        contentPanel.add(panel, key);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.BACKGROUND);
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildMainArea(), BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(AppTheme.NAVY_DARK);
        sidebar.setBorder(BorderFactory.createEmptyBorder(20, 16, 20, 16));
        sidebar.setPreferredSize(new Dimension(260, 100));

        JLabel appName = new JLabel("SkillLoop");
        appName.setFont(new Font(AppTheme.FONT_FAMILY, Font.BOLD, 24));
        appName.setForeground(Color.WHITE);
        appName.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(appName);
        sidebar.add(Box.createVerticalStrut(4));
        JLabel tagline = new JLabel("Peer Learning Platform");
        tagline.setFont(AppTheme.FONT_SMALL);
        tagline.setForeground(new Color(0x9F, 0xB6, 0xCF));
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(tagline);
        sidebar.add(Box.createVerticalStrut(18));

        sidebar.add(buildUserCard());
        sidebar.add(Box.createVerticalStrut(16));

        for (Screen screen : screens.values()) {
            sidebar.add(navButton(screen.title(), screen.key()));
            sidebar.add(Box.createVerticalStrut(6));
        }

        if (student.isAdmin()) {
            sidebar.add(Box.createVerticalStrut(10));
            adminButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            adminButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            adminButton.addActionListener(event -> openAdminConsole());
            sidebar.add(adminButton);
        }

        sidebar.add(Box.createVerticalGlue());
        JButton logoutButton = UiComponents.button("Sign out", UiComponents.ButtonStyle.LINK);
        logoutButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoutButton.addActionListener(event -> logout());
        sidebar.add(logoutButton);
        return sidebar;
    }

    private JPanel buildUserCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppTheme.NAVY);
        card.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        JLabel name = new JLabel(student.getFullName());
        name.setFont(AppTheme.FONT_BOLD);
        name.setForeground(Color.WHITE);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel email = new JLabel(student.getEmail());
        email.setFont(AppTheme.FONT_SMALL);
        email.setForeground(new Color(0xB9, 0xCB, 0xE0));
        email.setAlignmentX(Component.LEFT_ALIGNMENT);

        creditLabel.setFont(AppTheme.FONT_SMALL_BOLD);
        creditLabel.setForeground(new Color(0x8F, 0xE3, 0xBE));
        creditLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel department = new JLabel(student.getDepartment() + " - Semester " + student.getSemester());
        department.setFont(AppTheme.FONT_SMALL);
        department.setForeground(new Color(0x9F, 0xB6, 0xCF));
        department.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(name);
        card.add(Box.createVerticalStrut(2));
        card.add(email);
        card.add(Box.createVerticalStrut(2));
        card.add(department);
        card.add(Box.createVerticalStrut(6));
        card.add(creditLabel);
        return card;
    }

    private JButton navButton(String title, String key) {
        JButton button = UiComponents.button(title, UiComponents.ButtonStyle.SECONDARY);
        button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        button.addActionListener(event -> showScreen(key));
        return button;
    }

    private JPanel buildMainArea() {
        JPanel main = new JPanel(new BorderLayout(0, 12));
        main.setBackground(AppTheme.BACKGROUND);
        main.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        pageTitle.setFont(AppTheme.FONT_TITLE);
        pageTitle.setForeground(AppTheme.NAVY);
        header.add(pageTitle, BorderLayout.WEST);

        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerActions.setOpaque(false);
        JButton refreshButton = UiComponents.button("Refresh", UiComponents.ButtonStyle.PRIMARY);
        refreshButton.addActionListener(event -> refreshCurrent());
        headerActions.add(refreshButton);
        header.add(headerActions, BorderLayout.EAST);

        contentPanel.setBackground(AppTheme.BACKGROUND);
        main.add(header, BorderLayout.NORTH);
        main.add(contentPanel, BorderLayout.CENTER);
        return main;
    }

    /** Switches to the given screen and reloads its data. */
    public void showScreen(String key) {
        Screen screen = screens.get(key);
        if (screen == null) {
            return;
        }
        pageTitle.setText(screen.title());
        cardLayout.show(contentPanel, key);
        if (screen.panel() instanceof Refreshable refreshable) {
            refreshable.refresh();
        }
    }

    private void refreshCurrent() {
        for (Component component : contentPanel.getComponents()) {
            if (component.isVisible() && component instanceof Refreshable refreshable) {
                refreshable.refresh();
                refreshWalletBadge();
                return;
            }
        }
    }

    /** Opens the booking screen pre-filled for a chosen teacher and skill. */
    private void openBooking(long teacherId, long skillId) {
        BookingPanel booking = (BookingPanel) screens.get("booking").panel();
        booking.preselect(teacherId, skillId);
        showScreen("booking");
    }

    private void openAdminConsole() {
        if (adminFrame == null || !adminFrame.isDisplayable()) {
            adminFrame = new AdminDashboardFrame(student);
        }
        adminFrame.setVisible(true);
        adminFrame.toFront();
    }

    private void refreshWalletBadge() {
        AsyncRunner.run(this, () -> new CreditService().availableBalance(student.getStudentId()),
                balance -> creditLabel.setText("Credits: " + balance),
                () -> creditLabel.setText("Credits: n/a"));
    }

    private void logout() {
        boolean confirmed = JOptionPane.showConfirmDialog(this,
                "Do you want to sign out?", "SkillLoop",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
        if (!confirmed) {
            return;
        }
        new AuthService().logout();
        SessionContext.logout();
        if (adminFrame != null) {
            adminFrame.dispose();
        }
        LoginFrame loginFrame = new LoginFrame();
        loginFrame.setVisible(true);
        dispose();
    }
}
