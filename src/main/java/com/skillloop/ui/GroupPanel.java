package com.skillloop.ui;

import com.skillloop.model.GroupSession;
import com.skillloop.model.Skill;
import com.skillloop.service.GroupSessionService;
import com.skillloop.service.SkillService;
import com.skillloop.util.DateTimeUtil;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;

/** Group learning sessions: create, join and complete sessions with several learners. */
public class GroupPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long studentId;
    private final Runnable walletListener;
    private final DefaultTableModel openModel = UiComponents.tableModel(
            new String[]{"#", "Title", "Skill", "Teacher", "Start", "Seats", "Credits", "Status"});
    private final DefaultTableModel myModel = UiComponents.tableModel(
            new String[]{"#", "Title", "Skill", "Start", "Role", "Status"});
    private final JTable openTable = UiComponents.table(openModel);
    private final JTable myTable = UiComponents.table(myModel);
    private final JComboBox<Skill> skillCombo = UiComponents.comboBox(new Skill[0]);
    private final JTextField titleField = UiComponents.textField(18);
    private final JTextField startField = UiComponents.textField(14);
    private final JTextField endField = UiComponents.textField(14);
    private final JSpinner capacitySpinner = UiComponents.spinner(5, 2, 25, 1);
    private final JSpinner costSpinner = UiComponents.spinner(10, 0, 40, 5);
    private final List<GroupSession> openSessions = new ArrayList<>();
    private final List<GroupSession> mySessions = new ArrayList<>();

    private final GroupSessionService groupService = new GroupSessionService();
    private final SkillService skillService = new SkillService();

    public GroupPanel(long studentId, Runnable walletListener) {
        this.studentId = studentId;
        this.walletListener = walletListener;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        startField.setToolTipText("yyyy-MM-dd HH:mm");
        endField.setToolTipText("yyyy-MM-dd HH:mm");
        buildUi();
    }

    private void buildUi() {
        JPanel hostCard = UiComponents.card("Host a group session");
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        form.setOpaque(false);
        JButton createButton = UiComponents.button("Create session", UiComponents.ButtonStyle.PRIMARY);
        form.add(UiComponents.muted("Skill"));
        form.add(skillCombo);
        form.add(UiComponents.muted("Title"));
        form.add(titleField);
        form.add(UiComponents.muted("Start"));
        form.add(startField);
        form.add(UiComponents.muted("End"));
        form.add(endField);
        form.add(UiComponents.muted("Capacity"));
        form.add(capacitySpinner);
        form.add(UiComponents.muted("Credits/learner"));
        form.add(costSpinner);
        form.add(createButton);
        hostCard.add(form, BorderLayout.CENTER);

        JPanel openCard = UiComponents.card("Open group sessions");
        openCard.add(UiComponents.scroll(openTable), BorderLayout.CENTER);
        JPanel openActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        openActions.setOpaque(false);
        JButton joinButton = UiComponents.button("Join selected", UiComponents.ButtonStyle.SUCCESS);
        openActions.add(joinButton);
        openCard.add(openActions, BorderLayout.SOUTH);

        JPanel myCard = UiComponents.card("My group sessions");
        myCard.add(UiComponents.scroll(myTable), BorderLayout.CENTER);
        JPanel myActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        myActions.setOpaque(false);
        JButton completeButton = UiComponents.button("Complete (host only)", UiComponents.ButtonStyle.PRIMARY);
        JButton leaveButton = UiComponents.button("Leave", UiComponents.ButtonStyle.DANGER);
        myActions.add(completeButton);
        myActions.add(leaveButton);
        myCard.add(myActions, BorderLayout.SOUTH);

        createButton.addActionListener(event -> {
            Skill skill = (Skill) skillCombo.getSelectedItem();
            if (skill == null) {
                Dialogs.warning(this, "Please choose a skill.");
                return;
            }
            LocalDateTime start = DateTimeUtil.parse(startField.getText());
            LocalDateTime end = DateTimeUtil.parse(endField.getText());
            int capacity = (Integer) capacitySpinner.getValue();
            int cost = (Integer) costSpinner.getValue();
            AsyncRunner.run(this, () -> groupService.create(studentId, skill.getSkillId(), titleField.getText(),
                    start, end, capacity, cost), group -> {
                titleField.setText("");
                startField.setText("");
                endField.setText("");
                Dialogs.info(this, "Group session \"" + group.getTitle() + "\" was created.");
                refresh();
            });
        });

        joinButton.addActionListener(event -> {
            int row = openTable.getSelectedRow();
            if (row < 0) {
                Dialogs.warning(this, "Please select an open group session.");
                return;
            }
            long groupId = (Long) openModel.getValueAt(row, 0);
            AsyncRunner.run(this, () -> {
                groupService.join(groupId, studentId);
                return Boolean.TRUE;
            }, result -> {
                Dialogs.info(this, "You joined the group session. The enrolment fee was charged.");
                if (walletListener != null) {
                    walletListener.run();
                }
                refresh();
            });
        });

        completeButton.addActionListener(event -> {
            int row = myTable.getSelectedRow();
            if (row < 0) {
                Dialogs.warning(this, "Please select one of your group sessions.");
                return;
            }
            long groupId = (Long) myModel.getValueAt(row, 0);
            AsyncRunner.run(this, () -> groupService.complete(groupId, studentId), group -> {
                Dialogs.info(this, "Group session completed. The teaching reward was distributed.");
                if (walletListener != null) {
                    walletListener.run();
                }
                refresh();
            });
        });

        leaveButton.addActionListener(event -> {
            int row = myTable.getSelectedRow();
            if (row < 0) {
                Dialogs.warning(this, "Please select one of your group sessions.");
                return;
            }
            long groupId = (Long) myModel.getValueAt(row, 0);
            AsyncRunner.run(this, () -> {
                groupService.leave(groupId, studentId);
                return Boolean.TRUE;
            }, result -> refresh());
        });

        JPanel split = new JPanel(new java.awt.GridLayout(1, 2, 12, 12));
        split.setOpaque(false);
        split.add(openCard);
        split.add(myCard);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        hostCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        content.add(hostCard);
        content.add(Box.createVerticalStrut(12));
        content.add(split);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            Data data = new Data();
            data.skills = skillService.catalogue();
            data.open = groupService.openSessions(50);
            data.mine = groupService.mySessions(studentId, 50);
            return data;
        }, this::render);
    }

    private void render(Data data) {
        Skill previous = (Skill) skillCombo.getSelectedItem();
        skillCombo.removeAllItems();
        for (Skill skill : data.skills) {
            skillCombo.addItem(skill);
        }
        if (previous != null) {
            skillCombo.setSelectedItem(previous);
        }
        openSessions.clear();
        openModel.setRowCount(0);
        for (GroupSession group : data.open) {
            openModel.addRow(new Object[]{
                    group.getGroupId(),
                    group.getTitle(),
                    group.getSkillName(),
                    group.getTeacherName(),
                    DateTimeUtil.format(group.getStartTime()),
                    group.getEnrolled() + "/" + group.getCapacity(),
                    group.getCreditCostPerLearner(),
                    group.getStatus().name()});
        }
        mySessions.clear();
        mySessions.addAll(data.mine);
        myModel.setRowCount(0);
        for (GroupSession group : data.mine) {
            myModel.addRow(new Object[]{
                    group.getGroupId(),
                    group.getTitle(),
                    group.getSkillName(),
                    DateTimeUtil.format(group.getStartTime()),
                    group.getTeacherId() == studentId ? "host" : "participant",
                    group.getStatus().name()});
        }
    }

    /** Snapshot loaded in one background task. */
    private static final class Data {
        private List<Skill> skills = List.of();
        private List<GroupSession> open = List.of();
        private List<GroupSession> mine = List.of();
    }
}
