package com.skillloop.ui;

import com.skillloop.model.AvailabilitySlot;
import com.skillloop.model.LearningSession;
import com.skillloop.model.Skill;
import com.skillloop.model.StudentSkill;
import com.skillloop.service.BookingService;
import com.skillloop.service.SkillService;
import com.skillloop.util.CreditsPolicy;
import com.skillloop.util.DateTimeUtil;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Booking screen: pick a teacher, a skill, a slot and a duration, then reserve the credits. */
public class BookingPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long studentId;
    private final Runnable walletListener;
    private final JComboBox<Skill> skillCombo = UiComponents.comboBox(new Skill[0]);
    private final JComboBox<TeacherItem> teacherCombo = UiComponents.comboBox(new TeacherItem[0]);
    private final JComboBox<SlotItem> slotCombo = UiComponents.comboBox(new SlotItem[0]);
    private final JComboBox<Integer> durationCombo =
            UiComponents.comboBox(CreditsPolicy.ALLOWED_DURATIONS.toArray(new Integer[0]));
    private final JTextField startField = UiComponents.textField(16);
    private final JTextArea noteArea = UiComponents.textArea(2, 18);
    private final JLabel costLabel = UiComponents.label("Cost: -");
    private final DefaultTableModel upcomingModel =
            UiComponents.tableModel(new String[]{"When", "Skill", "Partner", "Duration", "Credits", "Status"});
    private final DefaultTableModel slotModel =
            UiComponents.tableModel(new String[]{"Start", "End", "Status"});
    private final JTextField slotStartField = UiComponents.textField(14);
    private final JTextField slotEndField = UiComponents.textField(14);

    private final SkillService skillService = new SkillService();
    private final BookingService bookingService = new BookingService();
    private final List<Skill> skills = new ArrayList<>();

    /** Combo entry that carries the id of a teacher. */
    public static final class TeacherItem {
        private final long studentId;
        private final String name;

        public TeacherItem(long studentId, String name) {
            this.studentId = studentId;
            this.name = name;
        }

        public long getStudentId() {
            return studentId;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** Combo entry that carries the id of an availability slot. */
    public static final class SlotItem {
        private final AvailabilitySlot slot;

        public SlotItem(AvailabilitySlot slot) {
            this.slot = slot;
        }

        public AvailabilitySlot getSlot() {
            return slot;
        }

        @Override
        public String toString() {
            return DateTimeUtil.format(slot.getStartTime()) + "  (" + slot.lengthMinutes() + " min)";
        }
    }

    public BookingPanel(long studentId, Runnable walletListener) {
        this.studentId = studentId;
        this.walletListener = walletListener;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        startField.setToolTipText("Format: yyyy-MM-dd HH:mm (for example 2026-11-02 15:30)");
        slotStartField.setToolTipText("Format: yyyy-MM-dd HH:mm");
        slotEndField.setToolTipText("Format: yyyy-MM-dd HH:mm");
        buildUi();
    }

    private void buildUi() {
        JPanel formCard = UiComponents.card("Request a session");
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        form.add(UiComponents.formRow("Skill", skillCombo));
        form.add(Box.createVerticalStrut(10));
        form.add(UiComponents.formRow("Teacher", teacherCombo));
        form.add(Box.createVerticalStrut(10));
        form.add(UiComponents.formRow("Availability slot", slotCombo));
        form.add(Box.createVerticalStrut(10));
        form.add(UiComponents.formRow("Start (yyyy-MM-dd HH:mm)", startField));
        form.add(Box.createVerticalStrut(10));
        form.add(UiComponents.formRow("Duration (minutes)", durationCombo));
        form.add(Box.createVerticalStrut(10));
        form.add(UiComponents.formRow("Note for the teacher", UiComponents.scroll(noteArea)));
        form.add(Box.createVerticalStrut(12));

        costLabel.setFont(AppTheme.FONT_BOLD);
        costLabel.setForeground(AppTheme.NAVY);
        costLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(costLabel);
        form.add(Box.createVerticalStrut(12));

        JButton bookButton = UiComponents.button("Request booking", UiComponents.ButtonStyle.PRIMARY);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.add(bookButton);
        form.add(actions);
        formCard.add(form, BorderLayout.CENTER);

        JPanel availabilityCard = UiComponents.card("My teaching availability");
        JPanel availabilityForm = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        availabilityForm.setOpaque(false);
        JButton addSlotButton = UiComponents.button("Add slot", UiComponents.ButtonStyle.SECONDARY);
        JButton removeSlotButton = UiComponents.button("Remove selected slot", UiComponents.ButtonStyle.DANGER);
        availabilityForm.add(UiComponents.muted("Start"));
        availabilityForm.add(slotStartField);
        availabilityForm.add(UiComponents.muted("End"));
        availabilityForm.add(slotEndField);
        availabilityForm.add(addSlotButton);
        availabilityForm.add(removeSlotButton);
        availabilityCard.add(availabilityForm, BorderLayout.NORTH);
        JTable slotTable = UiComponents.table(slotModel);
        availabilityCard.add(UiComponents.scroll(slotTable), BorderLayout.CENTER);

        JPanel tables = new JPanel(new GridLayout(1, 2, 12, 12));
        tables.setOpaque(false);
        JPanel upcomingCard = UiComponents.card("Your upcoming sessions");
        upcomingCard.add(UiComponents.scroll(UiComponents.table(upcomingModel)), BorderLayout.CENTER);
        tables.add(upcomingCard);
        tables.add(availabilityCard);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        JPanel topRow = new JPanel(new GridLayout(1, 2, 12, 12));
        topRow.setOpaque(false);
        topRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 420));
        topRow.add(formCard);
        topRow.add(tables);
        content.add(topRow);
        add(content, BorderLayout.CENTER);

        // --- events -------------------------------------------------------------
        skillCombo.addActionListener(event -> loadTeachers());
        teacherCombo.addActionListener(event -> loadSlots());
        durationCombo.addActionListener(event -> updateCost());
        bookButton.addActionListener(event -> book());
        addSlotButton.addActionListener(event -> addSlot());
        removeSlotButton.addActionListener(event -> {
            int row = slotTable.getSelectedRow();
            if (row < 0) {
                Dialogs.warning(this, "Please select one of your slots.");
                return;
            }
            long slotId = (Long) slotModel.getValueAt(row, 3);
            AsyncRunner.run(this, () -> {
                bookingService.removeAvailability(studentId, slotId);
                return Boolean.TRUE;
            }, result -> {
                Dialogs.info(this, "Slot removed.");
                refresh();
            });
        });
        updateCost();
    }

    private void updateCost() {
        Integer duration = (Integer) durationCombo.getSelectedItem();
        if (duration == null) {
            costLabel.setText("Cost: -");
            return;
        }
        costLabel.setText("Cost: " + CreditsPolicy.costFor(duration) + " credits (reserved until completion)");
    }

    private void loadTeachers() {
        Skill skill = (Skill) skillCombo.getSelectedItem();
        teacherCombo.removeAllItems();
        slotCombo.removeAllItems();
        if (skill == null) {
            return;
        }
        AsyncRunner.run(this, () -> skillService.teachersFor(skill.getSkillId(), null), teachers -> {
            teacherCombo.removeAllItems();
            for (StudentSkill listing : teachers) {
                if (listing.getStudentId() != studentId) {
                    teacherCombo.addItem(new TeacherItem(listing.getStudentId(),
                            listing.getStudentName() + " - " + listing.getProficiency().name().toLowerCase()
                                    + " (" + listing.getDepartment() + ")"));
                }
            }
            if (teacherCombo.getItemCount() > 0) {
                teacherCombo.setSelectedIndex(0);
                loadSlots();
            }
        });
    }

    private void loadSlots() {
        TeacherItem teacher = (TeacherItem) teacherCombo.getSelectedItem();
        slotCombo.removeAllItems();
        if (teacher == null) {
            return;
        }
        AsyncRunner.run(this, () -> bookingService.bookableSlotsOf(teacher.getStudentId()), slots -> {
            slotCombo.removeAllItems();
            for (AvailabilitySlot slot : slots) {
                slotCombo.addItem(new SlotItem(slot));
            }
            if (slotCombo.getItemCount() > 0) {
                startField.setText(format(slotCombo.getItemAt(0).getSlot().getStartTime()));
            }
        });
    }

    private static String format(LocalDateTime dateTime) {
        return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private void book() {
        TeacherItem teacher = (TeacherItem) teacherCombo.getSelectedItem();
        Skill skill = (Skill) skillCombo.getSelectedItem();
        SlotItem slot = (SlotItem) slotCombo.getSelectedItem();
        Integer duration = (Integer) durationCombo.getSelectedItem();
        LocalDateTime start = DateTimeUtil.parse(startField.getText());
        if (teacher == null || skill == null) {
            Dialogs.warning(this, "Please choose a skill and a teacher.");
            return;
        }
        if (slot == null) {
            Dialogs.warning(this, "The selected teacher has no open slot. Please choose another teacher.");
            return;
        }
        if (start == null) {
            Dialogs.warning(this, "Please enter the start time as yyyy-MM-dd HH:mm.");
            return;
        }
        String note = noteArea.getText();
        AsyncRunner.run(this, () -> bookingService.requestBooking(studentId, teacher.getStudentId(),
                skill.getSkillId(), slot.getSlot().getSlotId(), start, duration, note),
                session -> {
                    Dialogs.info(this, "Booking requested",
                            "Session #" + session.getSessionId() + " was requested.\n"
                                    + session.getCreditCost() + " credits are reserved until the session is "
                                    + "completed or cancelled.");
                    noteArea.setText("");
                    if (walletListener != null) {
                        walletListener.run();
                    }
                    refresh();
                });
    }

    private void addSlot() {
        LocalDateTime start = DateTimeUtil.parse(slotStartField.getText());
        LocalDateTime end = DateTimeUtil.parse(slotEndField.getText());
        if (start == null || end == null) {
            Dialogs.warning(this, "Please enter start and end as yyyy-MM-dd HH:mm.");
            return;
        }
        AsyncRunner.run(this, () -> bookingService.addAvailability(studentId, start, end), slot -> {
            slotStartField.setText("");
            slotEndField.setText("");
            Dialogs.info(this, "Availability slot added.");
            refresh();
        });
    }

    /** Pre-selects a teacher and skill (called from the matching screen). */
    public void preselect(long teacherId, long skillId) {
        for (int i = 0; i < skills.size(); i++) {
            if (skills.get(i).getSkillId() == skillId) {
                skillCombo.setSelectedIndex(i + 1);
                break;
            }
        }
        AsyncRunner.run(this, () -> skillService.teachersFor(skillId, null), teachers -> {
            teacherCombo.removeAllItems();
            for (StudentSkill listing : teachers) {
                teacherCombo.addItem(new TeacherItem(listing.getStudentId(),
                        listing.getStudentName() + " - " + listing.getProficiency().name().toLowerCase()));
            }
            for (int i = 0; i < teacherCombo.getItemCount(); i++) {
                if (teacherCombo.getItemAt(i).getStudentId() == teacherId) {
                    teacherCombo.setSelectedIndex(i);
                    break;
                }
            }
            loadSlots();
        });
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            PanelData data = new PanelData();
            data.skills = skillService.catalogue();
            data.upcoming = bookingService.upcoming(studentId, 10);
            data.slots = bookingService.myAvailability(studentId);
            return data;
        }, data -> {
            skills.clear();
            skills.addAll(data.skills);
            Skill previous = (Skill) skillCombo.getSelectedItem();
            skillCombo.removeAllItems();
            skillCombo.addItem(null);
            for (Skill skill : data.skills) {
                skillCombo.addItem(skill);
            }
            if (previous != null) {
                for (int i = 0; i < skillCombo.getItemCount(); i++) {
                    if (skillCombo.getItemAt(i) != null
                            && skillCombo.getItemAt(i).getSkillId() == previous.getSkillId()) {
                        skillCombo.setSelectedIndex(i);
                        break;
                    }
                }
            }
            upcomingModel.setRowCount(0);
            for (LearningSession session : data.upcoming) {
                boolean teaching = session.getTeacherId() == studentId;
                upcomingModel.addRow(new Object[]{
                        DateTimeUtil.format(session.getStartTime()),
                        session.getSkillName(),
                        (teaching ? "Learner: " : "Teacher: ")
                                + (teaching ? session.getLearnerName() : session.getTeacherName()),
                        session.getDurationMinutes() + " min",
                        session.getCreditCost(),
                        session.getStatus().name()});
            }
            slotModel.setRowCount(0);
            for (AvailabilitySlot slot : data.slots) {
                slotModel.addRow(new Object[]{
                        DateTimeUtil.format(slot.getStartTime()),
                        DateTimeUtil.format(slot.getEndTime()),
                        slot.getStatus().name(),
                        slot.getSlotId()});
            }
        });
    }

    /** Snapshot loaded in one background task. */
    private static final class PanelData {
        private List<Skill> skills = List.of();
        private List<LearningSession> upcoming = List.of();
        private List<AvailabilitySlot> slots = List.of();
    }
}
