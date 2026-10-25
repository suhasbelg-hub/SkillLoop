package com.skillloop.dao;

import com.skillloop.model.AvailabilitySlot;
import com.skillloop.model.SlotStatus;
import com.skillloop.util.DBConnection;
import com.skillloop.util.DaoUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persistence for the {@code availability_slots} table. */
public class AvailabilityDAO {

    private static final String BASE =
            "SELECT slot_id, student_id, start_time, end_time, status, session_id, created_at FROM availability_slots ";

    public long insert(Connection connection, AvailabilitySlot slot) throws SQLException {
        String sql = "INSERT INTO availability_slots (student_id, start_time, end_time, status) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, slot.getStudentId());
            DaoUtils.setDateTime(ps, 2, slot.getStartTime());
            DaoUtils.setDateTime(ps, 3, slot.getEndTime());
            ps.setString(4, slot.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("Inserting the availability slot did not return a generated key.");
        }
    }

    public long insert(AvailabilitySlot slot) {
        try (Connection connection = DBConnection.getConnection()) {
            return insert(connection, slot);
        } catch (SQLException e) {
            throw DaoUtils.wrap("creating the availability slot", e);
        }
    }

    public Optional<AvailabilitySlot> findById(long slotId) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(BASE + "WHERE slot_id = ?")) {
            ps.setLong(1, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the availability slot", e);
        }
    }

    /** Locks a slot inside the booking transaction so two learners cannot take the same slot. */
    public Optional<AvailabilitySlot> findByIdForUpdate(Connection connection, long slotId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(BASE + "WHERE slot_id = ? FOR UPDATE")) {
            ps.setLong(1, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<AvailabilitySlot> listForStudent(long studentId, boolean futureOnly) {
        String sql = BASE + "WHERE student_id = ? " + (futureOnly ? "AND end_time >= NOW() " : "")
                + "ORDER BY start_time";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<AvailabilitySlot> slots = new ArrayList<>();
                while (rs.next()) {
                    slots.add(map(rs));
                }
                return slots;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading your availability", e);
        }
    }

    /** Free future slots of a teacher - the list the booking screen offers. */
    public List<AvailabilitySlot> listBookableForTeacher(long teacherId) {
        String sql = BASE + "WHERE student_id = ? AND status = 'AVAILABLE' AND start_time > NOW() ORDER BY start_time";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, teacherId);
            try (ResultSet rs = ps.executeQuery()) {
                List<AvailabilitySlot> slots = new ArrayList<>();
                while (rs.next()) {
                    slots.add(map(rs));
                }
                return slots;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the teacher's availability", e);
        }
    }

    public void updateStatus(Connection connection, long slotId, SlotStatus status, Long sessionId) throws SQLException {
        String sql = "UPDATE availability_slots SET status = ?, session_id = ? WHERE slot_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            DaoUtils.setNullableLong(ps, 2, sessionId);
            ps.setLong(3, slotId);
            ps.executeUpdate();
        }
    }

    /** Releases a slot when a booking is rejected, cancelled or refunded. */
    public void releaseSlot(Connection connection, Long slotId) throws SQLException {
        if (slotId == null) {
            return;
        }
        String sql = "UPDATE availability_slots SET status = 'AVAILABLE', session_id = NULL WHERE slot_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, slotId);
            ps.executeUpdate();
        }
    }

    /** True when the window overlaps a slot the student already published. */
    public boolean hasOverlap(Connection connection, long studentId, LocalDateTime start, LocalDateTime end)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM availability_slots WHERE student_id = ? AND start_time < ? AND end_time > ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            DaoUtils.setDateTime(ps, 2, end);
            DaoUtils.setDateTime(ps, 3, start);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public int deletePast(long studentId) {
        String sql = "DELETE FROM availability_slots WHERE student_id = ? AND end_time < NOW() AND status = 'AVAILABLE'";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw DaoUtils.wrap("removing expired slots", e);
        }
    }

    private static AvailabilitySlot map(ResultSet rs) throws SQLException {
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setSlotId(rs.getLong("slot_id"));
        slot.setStudentId(rs.getLong("student_id"));
        slot.setStartTime(DaoUtils.getDateTime(rs, "start_time"));
        slot.setEndTime(DaoUtils.getDateTime(rs, "end_time"));
        slot.setStatus(SlotStatus.fromDb(rs.getString("status")));
        slot.setSessionId(DaoUtils.getNullableLong(rs, "session_id"));
        slot.setCreatedAt(DaoUtils.getDateTime(rs, "created_at"));
        return slot;
    }
}
