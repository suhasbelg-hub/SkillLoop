package com.skillloop.dao;

import com.skillloop.model.AccountStatus;
import com.skillloop.model.AdminAuditEvent;
import com.skillloop.model.AdminStats;
import com.skillloop.model.CreditTransaction;
import com.skillloop.model.LeaderboardEntry;
import com.skillloop.model.LearningSession;
import com.skillloop.model.MonthlyVolume;
import com.skillloop.model.Skill;
import com.skillloop.model.SkillType;
import com.skillloop.model.StudentSkill;
import com.skillloop.util.DBConnection;
import com.skillloop.util.DaoUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only reporting queries plus the audit log used by the administrator console.
 *
 * <p>These queries exercise the SQL features required by the project brief: multi-table
 * JOINs, GROUP BY, HAVING, aggregate functions, derived tables and correlated subqueries.</p>
 */
public class AdminDAO {

    /** Dashboard counters, aggregated in a single round-trip with scalar subqueries. */
    public AdminStats loadStats() {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM students)                                          AS total_students, "
                + "(SELECT COUNT(*) FROM students WHERE account_status = 'ACTIVE')          AS active_students, "
                + "(SELECT COUNT(*) FROM students WHERE account_status = 'SUSPENDED')       AS suspended_students, "
                + "(SELECT COUNT(*) FROM students WHERE role = 'ADMIN')                     AS administrators, "
                + "(SELECT COUNT(*) FROM skills)                                            AS total_skills, "
                + "(SELECT COUNT(*) FROM student_skills WHERE is_active = TRUE)             AS total_listings, "
                + "(SELECT COUNT(*) FROM learning_sessions WHERE session_status = 'COMPLETED') AS completed_sessions, "
                + "(SELECT COUNT(*) FROM learning_sessions WHERE session_status = 'REQUESTED') AS pending_bookings, "
                + "(SELECT COUNT(*) FROM learning_sessions WHERE session_status = 'DISPUTED')  AS disputed_sessions, "
                + "(SELECT COUNT(*) FROM learning_sessions WHERE session_status = 'CANCELLED') AS cancelled_sessions, "
                + "(SELECT COALESCE(SUM(credit_cost), 0) FROM learning_sessions WHERE session_status = 'COMPLETED') AS credits_transferred, "
                + "(SELECT COALESCE(SUM(amount), 0) FROM credit_transactions WHERE transaction_type = 'STARTER_GRANT') AS starter_grants, "
                + "(SELECT COUNT(*) FROM group_sessions WHERE status = 'OPEN')              AS open_group_sessions, "
                + "(SELECT COALESCE(ROUND(AVG(rating), 2), 0) FROM reviews)                 AS avg_rating";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            AdminStats stats = new AdminStats();
            if (rs.next()) {
                stats.setTotalStudents(rs.getLong("total_students"));
                stats.setActiveStudents(rs.getLong("active_students"));
                stats.setSuspendedStudents(rs.getLong("suspended_students"));
                stats.setAdministrators(rs.getLong("administrators"));
                stats.setTotalSkills(rs.getLong("total_skills"));
                stats.setTotalListings(rs.getLong("total_listings"));
                stats.setCompletedSessions(rs.getLong("completed_sessions"));
                stats.setPendingBookings(rs.getLong("pending_bookings"));
                stats.setDisputedSessions(rs.getLong("disputed_sessions"));
                stats.setCancelledSessions(rs.getLong("cancelled_sessions"));
                stats.setCreditsTransferred(rs.getLong("credits_transferred"));
                stats.setStarterGrants(rs.getLong("starter_grants"));
                stats.setOpenGroupSessions(rs.getLong("open_group_sessions"));
                stats.setAvgRating(rs.getDouble("avg_rating"));
            }
            return stats;
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the administrator dashboard", e);
        }
    }

    /** Skills ordered by total demand (offers + requests), read from the demand view. */
    public List<Skill> popularSkills(int limit) {
        String sql = "SELECT skill_id, skill_name, category, teachers_offering, learners_wanting, completed_sessions "
                + "FROM v_skill_demand "
                + "WHERE (teachers_offering + learners_wanting) > 0 "
                + "ORDER BY (teachers_offering + learners_wanting) DESC, completed_sessions DESC "
                + "LIMIT ?";
        List<Skill> skills = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Skill skill = new Skill();
                    skill.setSkillId(rs.getLong("skill_id"));
                    skill.setSkillName(rs.getString("skill_name"));
                    skill.setCategory(rs.getString("category"));
                    skill.setTeachersOffering(rs.getInt("teachers_offering"));
                    skill.setLearnersWanting(rs.getInt("learners_wanting"));
                    skill.setCompletedSessions(rs.getInt("completed_sessions"));
                    skills.add(skill);
                }
            }
            return skills;
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the popular skills report", e);
        }
    }

    /**
     * Most active teachers: JOIN + derived tables + GROUP BY + HAVING.
     * Only verified (COMPLETED) sessions count as contribution.
     */
    public List<LeaderboardEntry> topTeachers(int limit) {
        String sql = "SELECT s.student_id, s.full_name, s.department, "
                + "       t.teaching_sessions, t.distinct_learners, t.teaching_hours, t.credits_earned, "
                + "       COALESCE(r.avg_rating, 0)  AS avg_rating, "
                + "       COALESCE(r.review_count, 0) AS review_count "
                + "FROM students s "
                + "JOIN (SELECT ls.teacher_id, "
                + "             COUNT(*)                        AS teaching_sessions, "
                + "             COUNT(DISTINCT ls.learner_id)   AS distinct_learners, "
                + "             ROUND(SUM(ls.duration_minutes) / 60.0, 2) AS teaching_hours, "
                + "             SUM(ls.credit_cost)             AS credits_earned "
                + "      FROM learning_sessions ls "
                + "      WHERE ls.session_status = 'COMPLETED' "
                + "      GROUP BY ls.teacher_id "
                + "      HAVING COUNT(*) >= 1) t ON t.teacher_id = s.student_id "
                + "LEFT JOIN (SELECT rev.reviewee_id, ROUND(AVG(rev.rating), 2) AS avg_rating, COUNT(*) AS review_count "
                + "           FROM reviews rev GROUP BY rev.reviewee_id) r ON r.reviewee_id = s.student_id "
                + "ORDER BY t.teaching_sessions DESC, avg_rating DESC "
                + "LIMIT ?";
        List<LeaderboardEntry> entries = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LeaderboardEntry entry = new LeaderboardEntry();
                    entry.setStudentId(rs.getLong("student_id"));
                    entry.setFullName(rs.getString("full_name"));
                    entry.setDepartment(rs.getString("department"));
                    entry.setTeachingSessions(rs.getInt("teaching_sessions"));
                    entry.setDistinctLearners(rs.getInt("distinct_learners"));
                    entry.setTeachingHours(rs.getDouble("teaching_hours"));
                    entry.setCreditsEarned(rs.getInt("credits_earned"));
                    entry.setAvgRating(rs.getDouble("avg_rating"));
                    entry.setReviewCount(rs.getInt("review_count"));
                    entries.add(entry);
                }
            }
            return entries;
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the teacher activity report", e);
        }
    }

    /** Raw leaderboard metrics (the score itself is computed by the service layer). */
    public List<LeaderboardEntry> leaderboardData(int limit) {
        String sql = "SELECT student_id, full_name, department, completed_sessions, teaching_hours, credits_earned, "
                + "distinct_learners, avg_rating, review_count, learning_sessions "
                + "FROM v_leaderboard "
                + "WHERE completed_sessions > 0 OR learning_sessions > 0 "
                + "ORDER BY completed_sessions DESC, avg_rating DESC, teaching_hours DESC "
                + "LIMIT ?";
        List<LeaderboardEntry> entries = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LeaderboardEntry entry = new LeaderboardEntry();
                    entry.setStudentId(rs.getLong("student_id"));
                    entry.setFullName(rs.getString("full_name"));
                    entry.setDepartment(rs.getString("department"));
                    entry.setTeachingSessions(rs.getInt("completed_sessions"));
                    entry.setTeachingHours(rs.getDouble("teaching_hours"));
                    entry.setCreditsEarned(rs.getInt("credits_earned"));
                    entry.setDistinctLearners(rs.getInt("distinct_learners"));
                    entry.setAvgRating(rs.getDouble("avg_rating"));
                    entry.setReviewCount(rs.getInt("review_count"));
                    entry.setLearningSessions(rs.getInt("learning_sessions"));
                    entries.add(entry);
                }
            }
            return entries;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("doesn't exist")) {
                return topTeachers(limit);
            }
            throw DaoUtils.wrap("loading the leaderboard", e);
        }
    }

    public List<CreditTransaction> recentTransactions(int limit) {
        return new CreditTransactionDAO().listRecent(limit);
    }

    public List<LearningSession> listDisputes() {
        return new SessionDAO().listByStatus(com.skillloop.model.SessionStatus.DISPUTED, 100);
    }

    /** Listing moderation list (administrator). */
    public List<StudentSkill> listListings(String term, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT ss.student_skill_id, ss.student_id, ss.skill_id, ss.skill_type, ss.proficiency_level, "
                + "ss.description, ss.is_active, ss.created_at, ss.updated_at, "
                + "s.skill_name, s.category, st.full_name, st.department "
                + "FROM student_skills ss "
                + "JOIN skills s   ON s.skill_id = ss.skill_id "
                + "JOIN students st ON st.student_id = ss.student_id "
                + "WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (term != null && !term.isBlank()) {
            sql.append(" AND (s.skill_name LIKE ? OR st.full_name LIKE ?)");
            String like = "%" + term.trim() + "%";
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY ss.created_at DESC LIMIT ?");
        params.add(limit);
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<StudentSkill> listings = new ArrayList<>();
                while (rs.next()) {
                    StudentSkill listing = new StudentSkill();
                    listing.setStudentSkillId(rs.getLong("student_skill_id"));
                    listing.setStudentId(rs.getLong("student_id"));
                    listing.setSkillId(rs.getLong("skill_id"));
                    listing.setSkillType(SkillType.fromDb(rs.getString("skill_type")));
                    listing.setProficiency(com.skillloop.model.Proficiency.fromDb(rs.getString("proficiency_level")));
                    listing.setDescription(rs.getString("description"));
                    listing.setActive(rs.getBoolean("is_active"));
                    listing.setCreatedAt(DaoUtils.getDateTime(rs, "created_at"));
                    listing.setSkillName(rs.getString("skill_name"));
                    listing.setCategory(rs.getString("category"));
                    listing.setStudentName(rs.getString("full_name"));
                    listing.setDepartment(rs.getString("department"));
                    listings.add(listing);
                }
                return listings;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the skill listings", e);
        }
    }

    /** Monthly credit movement taken from the {@code v_monthly_credit_volume} view. */
    public List<MonthlyVolume> monthlyVolume(int limit) {
        String sql = "SELECT period, transaction_type, transaction_count, credits_credited, credits_debited "
                + "FROM v_monthly_credit_volume ORDER BY period DESC LIMIT ?";
        List<MonthlyVolume> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MonthlyVolume volume = new MonthlyVolume();
                    volume.setPeriod(rs.getString("period"));
                    volume.setTransactionType(rs.getString("transaction_type"));
                    volume.setTransactionCount(rs.getLong("transaction_count"));
                    volume.setCreditsCredited(rs.getLong("credits_credited"));
                    volume.setCreditsDebited(rs.getLong("credits_debited"));
                    rows.add(volume);
                }
            }
            return rows;
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the credit volume report", e);
        }
    }

    /** Appends an entry to the administrator audit trail. */
    public void logAudit(Connection connection, AdminAuditEvent event) throws SQLException {
        String sql = "INSERT INTO admin_audit_events (admin_id, action, target_type, target_id, details) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, event.getAdminId());
            ps.setString(2, event.getAction());
            DaoUtils.setNullableString(ps, 3, event.getTargetType());
            DaoUtils.setNullableLong(ps, 4, event.getTargetId());
            DaoUtils.setNullableString(ps, 5, event.getDetails());
            ps.executeUpdate();
        }
    }

    public void logAudit(AdminAuditEvent event) {
        try (Connection connection = DBConnection.getConnection()) {
            logAudit(connection, event);
        } catch (SQLException e) {
            throw DaoUtils.wrap("writing the audit event", e);
        }
    }

    public List<AdminAuditEvent> auditLog(int limit) {
        String sql = "SELECT e.event_id, e.admin_id, e.action, e.target_type, e.target_id, e.details, e.created_at, "
                + "s.full_name AS admin_name "
                + "FROM admin_audit_events e JOIN students s ON s.student_id = e.admin_id "
                + "ORDER BY e.created_at DESC LIMIT ?";
        List<AdminAuditEvent> events = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AdminAuditEvent event = new AdminAuditEvent();
                    event.setEventId(rs.getLong("event_id"));
                    event.setAdminId(rs.getLong("admin_id"));
                    event.setAction(rs.getString("action"));
                    event.setTargetType(rs.getString("target_type"));
                    event.setTargetId(DaoUtils.getNullableLong(rs, "target_id"));
                    event.setDetails(rs.getString("details"));
                    event.setCreatedAt(DaoUtils.getDateTime(rs, "created_at"));
                    event.setAdminName(rs.getString("admin_name"));
                    events.add(event);
                }
            }
            return events;
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the audit log", e);
        }
    }

    /** Counts students per department (used by the reports tab). */
    public List<String> departmentReport() {
        String sql = "SELECT department, COUNT(*) AS students FROM students "
                + "GROUP BY department HAVING COUNT(*) > 0 ORDER BY students DESC";
        List<String> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(rs.getString("department") + ": " + rs.getLong("students") + " students");
            }
            return rows;
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the department report", e);
        }
    }

    /** Students matching a filter (delegated to {@link StudentDAO#search}). */
    public List<com.skillloop.model.Student> listStudents(String term, AccountStatus status, int limit) {
        return new StudentDAO().search(term, status, limit);
    }
}
