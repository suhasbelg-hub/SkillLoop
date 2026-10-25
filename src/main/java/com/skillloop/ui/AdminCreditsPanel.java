package com.skillloop.ui;

import com.skillloop.model.AdminAuditEvent;
import com.skillloop.model.CreditTransaction;
import com.skillloop.service.AdminService;
import com.skillloop.service.StudentService;
import com.skillloop.util.CreditsPolicy;
import com.skillloop.util.DateTimeUtil;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;

/** Administrator: credit adjustments, transaction monitoring and the audit trail. */
public class AdminCreditsPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long adminId;
    private final DefaultTableModel transactionModel = UiComponents.tableModel(
            new String[]{"When", "Student", "Type", "Amount", "Balance after", "Reference", "Details"});
    private final DefaultTableModel auditModel = UiComponents.tableModel(
            new String[]{"When", "Administrator", "Action", "Target", "Details"});
    private final JTextField studentField = UiComponents.textField(14);
    private final JSpinner amountSpinner = UiComponents.spinner(10, -CreditsPolicy.maxAdminAdjustment(),
            CreditsPolicy.maxAdminAdjustment(), 5);
    private final JTextField reasonField = UiComponents.textField(28);
    private final AdminService adminService = new AdminService();
    private final StudentService studentService = new StudentService();

    public AdminCreditsPanel(long adminId) {
        this.adminId = adminId;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    private void buildUi() {
        JPanel adjustCard = UiComponents.card("Authorised credit adjustment");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        row.setOpaque(false);
        JButton adjustButton = UiComponents.button("Apply adjustment", UiComponents.ButtonStyle.PRIMARY);
        JButton reloadButton = UiComponents.button("Reload", UiComponents.ButtonStyle.LINK);
        row.add(UiComponents.muted("Student # or email"));
        row.add(studentField);
        row.add(UiComponents.muted("Credits"));
        row.add(amountSpinner);
        row.add(UiComponents.muted("Reason"));
        row.add(reasonField);
        row.add(adjustButton);
        row.add(reloadButton);
        adjustCard.add(row, BorderLayout.CENTER);

        JPanel tables = new JPanel(new GridLayout(1, 2, 12, 12));
        tables.setOpaque(false);
        JPanel transactionCard = UiComponents.card("Credit transactions");
        transactionCard.add(UiComponents.scroll(UiComponents.table(transactionModel)), BorderLayout.CENTER);
        JPanel auditCard = UiComponents.card("Administrator audit log");
        auditCard.add(UiComponents.scroll(UiComponents.table(auditModel)), BorderLayout.CENTER);
        tables.add(transactionCard);
        tables.add(auditCard);

        adjustButton.addActionListener(event -> adjust());
        reloadButton.addActionListener(event -> refresh());

        add(adjustCard, BorderLayout.NORTH);
        add(tables, BorderLayout.CENTER);
    }

    private void adjust() {
        String target = studentField.getText().trim();
        if (target.isEmpty()) {
            Dialogs.warning(this, "Please enter the student id or email.");
            return;
        }
        int amount = (Integer) amountSpinner.getValue();
        String reason = reasonField.getText().trim();
        if (reason.isEmpty()) {
            Dialogs.warning(this, "Please document the reason - it is stored in the audit log.");
            return;
        }
        AsyncRunner.run(this, () -> {
            long studentId = resolveStudent(target);
            adminService.adjustCredits(adminId, studentId, amount, reason);
            return studentId;
        }, studentId -> {
            reasonField.setText("");
            Dialogs.info(this, "Adjustment of " + amount + " credits was applied to student #" + studentId + ".");
            refresh();
        });
    }

    /** Accepts either a numeric student id or an email address. */
    private long resolveStudent(String target) {
        try {
            long id = Long.parseLong(target);
            if (studentService.findById(id).isPresent()) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // not a numeric id - try the email address below
        }
        return new com.skillloop.dao.StudentDAO().findByEmail(target)
                .orElseThrow(() -> new com.skillloop.exception.NotFoundException(
                        "No student found for '" + target + "'."))
                .getStudentId();
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            Data data = new Data();
            data.transactions = adminService.recentTransactions(adminId, 100);
            data.audit = adminService.auditLog(adminId, 100);
            return data;
        }, this::render);
    }

    private void render(Data data) {
        transactionModel.setRowCount(0);
        for (CreditTransaction transaction : data.transactions) {
            transactionModel.addRow(new Object[]{
                    DateTimeUtil.format(transaction.getCreatedAt()),
                    transaction.getStudentName(),
                    transaction.getType().name(),
                    transaction.getAmount(),
                    transaction.getBalanceAfter(),
                    transaction.getReference(),
                    transaction.getDescription()});
        }
        auditModel.setRowCount(0);
        for (AdminAuditEvent event : data.audit) {
            auditModel.addRow(new Object[]{
                    DateTimeUtil.format(event.getCreatedAt()),
                    event.getAdminName(),
                    event.getAction(),
                    event.getTargetType() + " #" + event.getTargetId(),
                    event.getDetails()});
        }
    }

    /** Snapshot loaded in one background task. */
    private static final class Data {
        private List<CreditTransaction> transactions = List.of();
        private List<AdminAuditEvent> audit = List.of();
    }
}
