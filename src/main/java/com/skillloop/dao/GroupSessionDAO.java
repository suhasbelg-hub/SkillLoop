package com.skillloop.dao;

import com.skillloop.exception.BookingException;
import com.skillloop.model.GroupParticipant;
import com.skillloop.model.GroupSession;
import com.skillloop.model.GroupStatus;
import com.skillloop.model.ParticipantStatus;
import com.skillloop.util.DBConnection;
import com.skillloop.util.DaoUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persistence for group sessions (one teacher, several learners). */
public class GroupSessionDAO {

    private static final String BASE =
            "SELECT g.group_id, g.teacher_id, g.skill_id, g.title, g.start_time, g.end_time, g.capacity, "
            + "g.credit_cost_per_learner, g.status, g.created_at, t.full_name AS teacher_name, s.skill_name, "
            + "(SELECT COUNT(*) FROM group_participants p WHERE p.group_id = g.group_id "
            + "  AND p.status IN ('JOINED','COMPLETED')) AS enrolled "
            + "FROM group_sessions g "
            + "JOIN students t ON t.student_id = g.teacher_id "
            + "JOIN skills s   ON s.skill_id = g.skill_id ";

    public long insert(Connection connection, GroupSession group) throws SQLException {
        String sql = "INSERT INTO group_sessions (teacher_id, skill_id, title, start_time, end_time, capacity, "
                + "credit_cost_per_learner, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, group.getTeacherId());
            ps.setLong(2, group.getSkillId());
            ps.setString(3, group.getTitle());
            DaoUtils.setDateTime(ps, 4, group.getStartTime());
            DaoUtils.setDateTime(ps, 5, group.getEndTime());
            ps.setInt(6, group.getCapacity());
            ps.setInt(7, group.getCreditCostPerLearner());
            ps.setString(8, group.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("Inserting the group session did not return a generated key.");
        }
    }

    public long insert(GroupSession group) {
        try (Connection connection = DBConnection.getConnection()) {
            return insert(connection, group);
        } catch (SQLException e) {
            throw DaoUtils.wrap("creating the group session", e);
        }
    }

    public Optional<GroupSession> findById(Connection connection, long groupId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(BASE + "WHERE g.group_id = ?")) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<GroupSession> findByIdForUpdate(Connection connection, long groupId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT group_id, teacher_id, skill_id, title, start_time, end_time, capacity, "
                + "credit_cost_per_learner, status, created_at FROM group_sessions WHERE group_id = ? FOR UPDATE")) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapPlain(rs)) : Optional.empty();
            }
        }
    }

    public List<GroupSession> listOpen(int limit) {
        String sql = BASE + "WHERE g.status = 'OPEN' AND g.start_time > NOW() ORDER BY g.start_time LIMIT ?";
        return query(sql, List.of(limit));
    }

    public List<GroupSession> listForStudent(long studentId, int limit) {
        String sql = BASE + "WHERE (g.teacher_id = ? OR EXISTS (SELECT 1 FROM group_participants p "
                + "WHERE p.group_id = g.group_id AND p.student_id = ?)) ORDER BY g.start_time DESC LIMIT ?";
        return query(sql, List.of(studentId, studentId, limit));
    }

    /** Registers a learner; the capacity check runs inside the same transaction as the payment. */
    public void join(Connection connection, long groupId, long studentId) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM group_participants WHERE group_id = ? AND status IN ('JOINED','COMPLETED')";
        int enrolled;
        try (PreparedStatement ps = connection.prepareStatement(countSql)) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                enrolled = rs.next() ? rs.getInt(1) : 0;
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT capacity FROM group_sessions WHERE group_id = ?")) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && enrolled >= rs.getInt(1)) {
                    throw new BookingException("This group session is already full.");
                }
            }
        }
        String sql = "INSERT INTO group_participants (group_id, student_id, status) VALUES (?, ?, 'JOINED')";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            ps.setLong(2, studentId);
            ps.executeUpdate();
        }
    }

    public List<GroupParticipant> listParticipants(Connection connection, long groupId) throws SQLException {
        String sql = "SELECT p.participant_id, p.group_id, p.student_id, p.status, p.joined_at, s.full_name "
                + "FROM group_participants p JOIN students s ON s.student_id = p.student_id "
                + "WHERE p.group_id = ? ORDER BY p.joined_at";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                List<GroupParticipant> participants = new ArrayList<>();
                while (rs.next()) {
                    GroupParticipant participant = new GroupParticipant();
                    participant.setParticipantId(rs.getLong("participant_id"));
                    participant.setGroupId(rs.getLong("group_id"));
                    participant.setStudentId(rs.getLong("student_id"));
                    participant.setStatus(ParticipantStatus.fromDb(rs.getString("status")));
                    participant.setJoinedAt(DaoUtils.getDateTime(rs, "joined_at"));
                    participant.setStudentName(rs.getString("full_name"));
                    participants.add(participant);
                }
                return participants;
            }
        }
    }

    public void updateParticipantStatus(Connection connection, long groupId, long studentId, ParticipantStatus status)
            throws SQLException {
        String sql = "UPDATE group_participants SET status = ? WHERE group_id = ? AND student_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, groupId);
            ps.setLong(3, studentId);
            ps.executeUpdate();
        }
    }

    public void updateStatus(Connection connection, long groupId, GroupStatus status) throws SQLException {
        String sql = "UPDATE group_sessions SET status = ? WHERE group_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, groupId);
            ps.executeUpdate();
        }
    }

    private List<GroupSession> query(String sql, List<Object> params) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<GroupSession> groups = new ArrayList<>();
                while (rs.next()) {
                    groups.add(map(rs));
                }
                return groups;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading group sessions", e);
        }
    }

    private static GroupSession map(ResultSet rs) throws SQLException {
        GroupSession group = mapPlain(rs);
        group.setTeacherName(rs.getString("teacher_name"));
        group.setSkillName(rs.getString("skill_name"));
        group.setEnrolled(rs.getInt("enrolled"));
        return group;
    }

    private static GroupSession mapPlain(ResultSet rs) throws SQLException {
        GroupSession group = new GroupSession();
        group.setGroupId(rs.getLong("group_id"));
        group.setTeacherId(rs.getLong("teacher_id"));
        group.setSkillId(rs.getLong("skill_id"));
        group.setTitle(rs.getString("title"));
        group.setStartTime(DaoUtils.getDateTime(rs, "start_time"));
        group.setEndTime(DaoUtils.getDateTime(rs, "end_time"));
        group.setCapacity(rs.getInt("capacity"));
        group.setCreditCostPerLearner(rs.getInt("credit_cost_per_learner"));
        group.setStatus(GroupStatus.fromDb(rs.getString("status")));
        group.setCreatedAt(DaoUtils.getDateTime(rs, "created_at"));
        return group;
    }
}
