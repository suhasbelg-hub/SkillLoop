package com.skillloop.ui;

import com.skillloop.model.AccountStatus;
import com.skillloop.model.Student;
import com.skillloop.service.AdminService;
import com.skillloop.util.DateTimeUtil;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/** Administrator: search students, suspend/reactivate accounts and promote administrators. */
public class AdminStudentsPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long adminId;
    private final DefaultTableModel model = UiComponents.tableModel(
            new String[]{"#", "Name", "Email", "Department", "Sem", "Role", "Status", "Joined"});
    private final JTable table = UiComponents.table(model);
    private final JTextField searchField = UiComponents.textField(20);
    private final JComboBox<String> statusCombo =
            UiComponents.comboBox(new String[]{"ALL", "ACTIVE", "SUSPENDED", "INACTIVE"});
    private final List<Student> students = new ArrayList<>();
    private final AdminService adminService = new AdminService();

    public AdminStudentsPanel(long adminId) {
        this.adminId = adminId;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    private void buildUi() {
        JPanel filters = UiComponents.card("Student accounts");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        row.setOpaque(false);
        JButton searchButton = UiComponents.button("Search", UiComponents.ButtonStyle.PRIMARY);
        JButton suspendButton = UiComponents.button("Suspend", UiComponents.ButtonStyle.DANGER);
        JButton activateButton = UiComponents.button("Activate", UiComponents.ButtonStyle.SUCCESS);
        JButton promoteButton = UiComponents.button("Promote to admin", UiComponents.ButtonStyle.SECONDARY);
        row.add(searchField);
        row.add(statusCombo);
        row.add(searchButton);
        row.add(suspendButton);
        row.add(activateButton);
        row.add(promoteButton);
        filters.add(row, BorderLayout.NORTH);
        filters.add(UiComponents.scroll(table), BorderLayout.CENTER);
        add(filters, BorderLayout.CENTER);

        searchButton.addActionListener(event -> refresh());
        searchField.addActionListener(event -> refresh());
        statusCombo.addActionListener(event -> refresh());
        suspendButton.addActionListener(event -> changeStatus(AccountStatus.SUSPENDED));
        activateButton.addActionListener(event -> changeStatus(AccountStatus.ACTIVE));
        promoteButton.addActionListener(event -> {
            Student student = selected();
            if (student == null) {
                return;
            }
            AsyncRunner.run(this, () -> {
                adminService.promote(adminId, student.getStudentId());
                return Boolean.TRUE;
            }, result -> {
                Dialogs.info(this, student.getFullName() + " is now an administrator.");
                refresh();
            });
        });
    }

    private void changeStatus(AccountStatus status) {
        Student student = selected();
        if (student == null) {
            return;
        }
        String reason = Dialogs.input(this, "Reason", "Reason for '" + status.name().toLowerCase() + "':", "");
        AsyncRunner.run(this, () -> {
            adminService.setAccountStatus(adminId, student.getStudentId(), status, reason);
            return Boolean.TRUE;
        }, result -> {
            Dialogs.info(this, "Account updated.");
            refresh();
        });
    }

    private Student selected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            Dialogs.warning(this, "Please select a student first.");
            return null;
        }
        long id = (Long) model.getValueAt(row, 0);
        return students.stream().filter(s -> s.getStudentId() == id).findFirst().orElse(null);
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            String statusText = (String) statusCombo.getSelectedItem();
            AccountStatus status = "ALL".equals(statusText) ? null : AccountStatus.valueOf(statusText);
            return adminService.listStudents(adminId, searchField.getText(), status, 200);
        }, this::render);
    }

    private void render(List<Student> loaded) {
        students.clear();
        students.addAll(loaded);
        model.setRowCount(0);
        for (Student student : loaded) {
            model.addRow(new Object[]{
                    student.getStudentId(),
                    student.getFullName(),
                    student.getEmail(),
                    student.getDepartment(),
                    student.getSemester(),
                    student.getRole().name(),
                    student.getAccountStatus().name(),
                    DateTimeUtil.format(student.getCreatedAt())});
        }
    }
}
