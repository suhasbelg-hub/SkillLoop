package com.skillloop.dao;

import com.skillloop.model.LearningSession;
import com.skillloop.model.SessionStatus;
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

/** Persistence for the {@code learning_sessions} table. */
public class SessionDAO {

    private static final String BASE =
            "SELECT ls.session_id, ls.teacher_id, ls.learner_id, ls.skill_id, ls.slot_id, ls.start_time, ls.end_time, "
            + "ls.duration_minutes, ls.credit_cost, ls.session_status, ls.teacher_confirmation, ls.learner_confirmation, "
            + "ls.learner_note, ls.resolution_note, ls.resolved_by, ls.settled_at, ls.created_at, ls.updated_at, "
            + "t.full_name AS teacher_name, l.full_name AS learner_name, s.skill_name "
            + "FROM learning_sessions ls "
            + "JOIN students t ON t.student_id = ls.teacher_id "
            + "JOIN students l ON l.student_id = ls.learner_id "
            + "JOIN skills s   ON s.skill_id  = ls.skill_id ";

    public long insert(Connection connection, LearningSession session) throws SQLException {
        String sql = "INSERT INTO learning_sessions (teacher_id, learner_id, skill_id, slot_id, start_time, end_time, "
                + "duration_minutes, credit_cost, session_status, learner_note) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, session.getTeacherId());
            ps.setLong(2, session.getLearnerId());
            ps.setLong(3, session.getSkillId());
            DaoUtils.setNullableLong(ps, 4, session.getSlotId());
            DaoUtils.setDateTime(ps, 5, session.getStartTime());
            DaoUtils.setDateTime(ps, 6, session.getEndTime());
            ps.setInt(7, session.getDurationMinutes());
            ps.setInt(8, session.getCreditCost());
            ps.setString(9, session.getStatus().name());
            DaoUtils.setNullableString(ps, 10, session.getLearnerNote());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("Inserting the session did not return a generated key.");
        }
    }

    /** Persists status, confirmation flags, resolution data and settlement timestamp. */
    public void update(Connection connection, LearningSession session) throws SQLException {
        String sql = "UPDATE learning_sessions SET session_status = ?, teacher_confirmation = ?, learner_confirmation = ?, "
                + "resolution_note = ?, resolved_by = ?, settled_at = ? WHERE session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, session.getStatus().name());
            ps.setBoolean(2, session.isTeacherConfirmation());
            ps.setBoolean(3, session.isLearnerConfirmation());
            DaoUtils.setNullableString(ps, 4, session.getResolutionNote());
            DaoUtils.setNullableLong(ps, 5, session.getResolvedBy());
            DaoUtils.setDateTime(ps, 6, session.getSettledAt());
            ps.setLong(7, session.getSessionId());
            ps.executeUpdate();
        }
    }

    public void updateStatus(Connection connection, long sessionId, SessionStatus status) throws SQLException {
        String sql = "UPDATE learning_sessions SET session_status = ? WHERE session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, sessionId);
            ps.executeUpdate();
        }
    }

    public Optional<LearningSession> findById(Connection connection, long sessionId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(BASE + "WHERE ls.session_id = ?")) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<LearningSession> findById(long sessionId) {
        try (Connection connection = DBConnection.getConnection()) {
            return findById(connection, sessionId);
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the session", e);
        }
    }

    /**
     * Loads a session and locks its row (SELECT ... FOR UPDATE). Serialising on the session
     * row is what makes the credit transfer idempotent under concurrency.
     */
    public Optional<LearningSession> findByIdForUpdate(Connection connection, long sessionId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(BASE + "WHERE ls.session_id = ? FOR UPDATE")) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Sessions where the student is the teacher, the learner, or either of them. */
    public List<LearningSession> listForStudent(long studentId, String role, SessionStatus status, int limit) {
        StringBuilder sql = new StringBuilder(BASE);
        List<Object> params = new ArrayList<>();
        if ("TEACHING".equalsIgnoreCase(role)) {
            sql.append("WHERE ls.teacher_id = ? ");
            params.add(studentId);
        } else if ("LEARNING".equalsIgnoreCase(role)) {
            sql.append("WHERE ls.learner_id = ? ");
            params.add(studentId);
        } else {
            sql.append("WHERE (ls.teacher_id = ? OR ls.learner_id = ?) ");
            params.add(studentId);
            params.add(studentId);
        }
        if (status != null) {
            sql.append("AND ls.session_status = ? ");
            params.add(status.name());
        }
        sql.append("ORDER BY ls.start_time DESC LIMIT ?");
        params.add(limit);
        return query(sql.toString(), params);
    }

    /** Next confirmed sessions of a student (used by the dashboard "upcoming" card). */
    public List<LearningSession> listUpcoming(long studentId, int limit) {
        String sql = BASE + "WHERE (ls.teacher_id = ? OR ls.learner_id = ?) AND ls.session_status IN ('ACCEPTED','REQUESTED') "
                + "AND ls.start_time >= NOW() ORDER BY ls.start_time ASC LIMIT ?";
        return query(sql, List.of(studentId, studentId, limit));
    }

    public List<LearningSession> listByStatus(SessionStatus status, int limit) {
        String sql = BASE + "WHERE ls.session_status = ? ORDER BY ls.start_time DESC LIMIT ?";
        return query(sql, List.of(status.name(), limit));
    }

    /** True when the student already has a blocking session overlapping the given window. */
    public boolean hasOverlap(Connection connection, long studentId, LocalDateTime start, LocalDateTime end,
                              Long excludeSessionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM learning_sessions "
                + "WHERE (teacher_id = ? OR learner_id = ?) "
                + "  AND session_status IN ('REQUESTED','ACCEPTED','DISPUTED') "
                + "  AND start_time < ? AND end_time > ? "
                + (excludeSessionId == null ? "" : "  AND session_id <> ? ");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, studentId);
            DaoUtils.setDateTime(ps, 3, end);
            DaoUtils.setDateTime(ps, 4, start);
            if (excludeSessionId != null) {
                ps.setLong(5, excludeSessionId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Prevents duplicate open requests for the same teacher, learner and slot. */
    public boolean hasOpenRequestForSlot(Connection connection, long slotId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM learning_sessions "
                + "WHERE slot_id = ? AND session_status IN ('REQUESTED','ACCEPTED','DISPUTED')";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public long countByStatus(SessionStatus status) {
        String sql = "SELECT COUNT(*) FROM learning_sessions WHERE session_status = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("counting sessions", e);
        }
    }

    public long countCompletedForTeacher(long teacherId) {
        return countFor("teacher_id", teacherId, SessionStatus.COMPLETED);
    }

    public long countCompletedForLearner(long learnerId) {
        return countFor("learner_id", learnerId, SessionStatus.COMPLETED);
    }

    private long countFor(String column, long studentId, SessionStatus status) {
        String sql = "SELECT COUNT(*) FROM learning_sessions WHERE " + column + " = ? AND session_status = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("counting completed sessions", e);
        }
    }

    private List<LearningSession> query(String sql, List<Object> params) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<LearningSession> sessions = new ArrayList<>();
                while (rs.next()) {
                    sessions.add(map(rs));
                }
                return sessions;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading sessions", e);
        }
    }

    /** Maps the current row to a {@link LearningSession} (join columns included). */
    public static LearningSession map(ResultSet rs) throws SQLException {
        LearningSession session = new LearningSession();
        session.setSessionId(rs.getLong("session_id"));
        session.setTeacherId(rs.getLong("teacher_id"));
        session.setLearnerId(rs.getLong("learner_id"));
        session.setSkillId(rs.getLong("skill_id"));
        session.setSlotId(DaoUtils.getNullableLong(rs, "slot_id"));
        session.setStartTime(DaoUtils.getDateTime(rs, "start_time"));
        session.setEndTime(DaoUtils.getDateTime(rs, "end_time"));
        session.setDurationMinutes(rs.getInt("duration_minutes"));
        session.setCreditCost(rs.getInt("credit_cost"));
        session.setStatus(SessionStatus.fromDb(rs.getString("session_status")));
        session.setTeacherConfirmation(rs.getBoolean("teacher_confirmation"));
        session.setLearnerConfirmation(rs.getBoolean("learner_confirmation"));
        session.setLearnerNote(rs.getString("learner_note"));
        session.setResolutionNote(rs.getString("resolution_note"));
        session.setResolvedBy(DaoUtils.getNullableLong(rs, "resolved_by"));
        session.setSettledAt(DaoUtils.getDateTime(rs, "settled_at"));
        session.setCreatedAt(DaoUtils.getDateTime(rs, "created_at"));
        session.setUpdatedAt(DaoUtils.getDateTime(rs, "updated_at"));
        session.setTeacherName(rs.getString("teacher_name"));
        session.setLearnerName(rs.getString("learner_name"));
        session.setSkillName(rs.getString("skill_name"));
        return session;
    }
}
