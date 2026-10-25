package com.skillloop.ui;

import com.skillloop.model.Student;
import com.skillloop.service.AdminService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

/**
 * Administrator console.
 *
 * <p>The frame can only be constructed with a student that carries the ADMIN role; the check is
 * repeated in {@link AdminService} for every single operation, so a hidden button would never
 * be enough to protect the data.</p>
 */
public class AdminDashboardFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final long adminId;
    private final JTabbedPane tabs = new JTabbedPane();

    public AdminDashboardFrame(Student admin) {
        super("SkillLoop - Administrator console");
        this.adminId = admin.getStudentId();
        if (!admin.isAdmin()) {
            throw new com.skillloop.exception.AuthorizationException(
                    "The administrator console requires an administrator account.");
        }
        setSize(1400, 880);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(1200, 740));
        buildUi();
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.BACKGROUND);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppTheme.NAVY_DARK);
        header.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        JLabel title = new JLabel("Administrator console");
        title.setFont(AppTheme.FONT_TITLE);
        title.setForeground(Color.WHITE);
        JLabel subtitle = new JLabel("Signed in as " + adminId + " - every action is written to the audit log");
        subtitle.setFont(AppTheme.FONT_SMALL);
        subtitle.setForeground(new Color(0xB9, 0xCB, 0xE0));
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);

        JPanel headerActions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        headerActions.setOpaque(false);
        JButton reloadButton = UiComponents.button("Reload current tab", UiComponents.ButtonStyle.PRIMARY);
        reloadButton.addActionListener(event -> {
            java.awt.Component selected = tabs.getSelectedComponent();
            if (selected instanceof Refreshable refreshable) {
                refreshable.refresh();
            }
        });
        headerActions.add(reloadButton);
        header.add(headerActions, BorderLayout.EAST);

        tabs.setFont(AppTheme.FONT_BOLD);
        addTab("Overview", new AdminOverviewPanel(adminId));
        addTab("Students", new AdminStudentsPanel(adminId));
        addTab("Sessions & disputes", new AdminDisputesPanel(adminId));
        addTab("Skill listings", new AdminListingsPanel(adminId));
        addTab("Credits & audit", new AdminCreditsPanel(adminId));

        root.add(header, BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        setContentPane(root);

        if (tabs.getComponentCount() > 0 && tabs.getComponentAt(0) instanceof Refreshable refreshable) {
            refreshable.refresh();
        }
    }

    private void addTab(String title, JPanel panel) {
        tabs.addTab(title, panel);
    }

    /** Verifies that the signed-in account is still an administrator. */
    public boolean checkPermission() {
        return new AdminService().canOpenConsole();
    }
}
