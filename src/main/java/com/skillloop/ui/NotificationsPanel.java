package com.skillloop.ui;

import com.skillloop.model.Notification;
import com.skillloop.service.NotificationService;
import com.skillloop.util.DateTimeUtil;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;

/** Notification centre: booking updates, completions, credit transfers and achievements. */
public class NotificationsPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long studentId;
    private final Runnable badgeListener;
    private final DefaultTableModel model =
            UiComponents.tableModel(new String[]{"When", "Type", "Title", "Message", "Read"});
    private final JTable table = UiComponents.table(model);
    private final NotificationService notificationService = new NotificationService();

    public NotificationsPanel(long studentId, Runnable badgeListener) {
        this.studentId = studentId;
        this.badgeListener = badgeListener;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    private void buildUi() {
        JPanel card = UiComponents.card("Notifications");
        card.add(UiComponents.scroll(table), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        actions.setOpaque(false);
        JButton markReadButton = UiComponents.button("Mark selected as read", UiComponents.ButtonStyle.SECONDARY);
        JButton markAllButton = UiComponents.button("Mark all as read", UiComponents.ButtonStyle.PRIMARY);
        JButton reloadButton = UiComponents.button("Reload", UiComponents.ButtonStyle.LINK);
        actions.add(markReadButton);
        actions.add(markAllButton);
        actions.add(reloadButton);
        card.add(actions, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);

        markReadButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                Dialogs.warning(this, "Please select a notification.");
                return;
            }
            long id = (Long) model.getValueAt(row, 5);
            AsyncRunner.run(this, () -> {
                notificationService.markRead(id, studentId);
                return Boolean.TRUE;
            }, result -> refresh());
        });
        markAllButton.addActionListener(event -> AsyncRunner.run(this, () -> {
            notificationService.markAllRead(studentId);
            return Boolean.TRUE;
        }, result -> {
            if (badgeListener != null) {
                badgeListener.run();
            }
            refresh();
        }));
        reloadButton.addActionListener(event -> refresh());
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> notificationService.listFor(studentId, 200), this::render);
    }

    private void render(List<Notification> notifications) {
        model.setRowCount(0);
        for (Notification notification : notifications) {
            model.addRow(new Object[]{
                    DateTimeUtil.format(notification.getCreatedAt()),
                    notification.getType().name(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.isRead() ? "read" : "NEW",
                    notification.getNotificationId()});
        }
        if (badgeListener != null) {
            badgeListener.run();
        }
    }
}
