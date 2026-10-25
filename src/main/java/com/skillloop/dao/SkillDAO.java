package com.skillloop.dao;

import com.skillloop.exception.DuplicateResourceException;
import com.skillloop.model.Proficiency;
import com.skillloop.model.Skill;
import com.skillloop.model.SkillType;
import com.skillloop.model.StudentSkill;
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

/** Persistence for the {@code skills} and {@code student_skills} tables. */
public class SkillDAO {

    // ------------------------------------------------------------------ catalogue

    public long insertSkill(Connection connection, Skill skill) throws SQLException {
        String sql = "INSERT INTO skills (skill_name, category, description) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, skill.getSkillName());
            ps.setString(2, skill.getCategory());
            DaoUtils.setNullableString(ps, 3, skill.getDescription());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("Inserting a skill did not return a generated key.");
        }
    }

    public long insertSkill(Skill skill) {
        try (Connection connection = DBConnection.getConnection()) {
            return insertSkill(connection, skill);
        } catch (SQLException e) {
            if (DaoUtils.isDuplicateKey(e)) {
                throw new DuplicateResourceException("The skill '" + skill.getSkillName() + "' already exists.", e);
            }
            throw DaoUtils.wrap("creating the skill", e);
        }
    }

    public Optional<Skill> findSkillByName(Connection connection, String name) throws SQLException {
        String sql = "SELECT skill_id, skill_name, category, description, created_at FROM skills WHERE skill_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapSkill(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Skill> findSkillByName(String name) {
        try (Connection connection = DBConnection.getConnection()) {
            return findSkillByName(connection, name);
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the skill", e);
        }
    }

    public Optional<Skill> findSkillById(long skillId) {
        String sql = "SELECT skill_id, skill_name, category, description, created_at FROM skills WHERE skill_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, skillId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapSkill(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the skill", e);
        }
    }

    public List<Skill> findAllSkills() {
        String sql = "SELECT skill_id, skill_name, category, description, created_at FROM skills ORDER BY category, skill_name";
        return querySkills(sql, List.of());
    }

    /** Catalogue enriched with demand counters taken from the {@code v_skill_demand} view. */
    public List<Skill> findAllWithDemand() {
        String plain = "SELECT skill_id, skill_name, category, description, teachers_offering, learners_wanting, completed_sessions "
                + "FROM v_skill_demand ORDER BY teachers_offering DESC, skill_name";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(plain);
             ResultSet rs = ps.executeQuery()) {
            List<Skill> skills = new ArrayList<>();
            while (rs.next()) {
                Skill skill = mapSkill(rs);
                skill.setTeachersOffering(rs.getInt("teachers_offering"));
                skill.setLearnersWanting(rs.getInt("learners_wanting"));
                skill.setCompletedSessions(rs.getInt("completed_sessions"));
                skills.add(skill);
            }
            return skills;
        } catch (SQLException e) {
            // Fall back to the plain catalogue when the views were not created yet.
            if (e.getMessage() != null && e.getMessage().contains("doesn't exist")) {
                return findAllSkills();
            }
            throw DaoUtils.wrap("loading the skill catalogue", e);
        }
    }

    /** Free text search across skill name, category and description. */
    public List<Skill> searchSkills(String term, String category) {
        StringBuilder sql = new StringBuilder(
                "SELECT s.skill_id, s.skill_name, s.category, s.description, s.created_at, "
                + "(SELECT COUNT(*) FROM student_skills o WHERE o.skill_id = s.skill_id AND o.skill_type = 'OFFER' AND o.is_active = TRUE) AS teachers_offering, "
                + "(SELECT COUNT(*) FROM student_skills l WHERE l.skill_id = s.skill_id AND l.skill_type = 'LEARN' AND l.is_active = TRUE) AS learners_wanting, "
                + "(SELECT COUNT(*) FROM learning_sessions ls WHERE ls.skill_id = s.skill_id AND ls.session_status = 'COMPLETED') AS completed_sessions "
                + "FROM skills s WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (term != null && !term.isBlank()) {
            sql.append(" AND (s.skill_name LIKE ? OR s.category LIKE ? OR s.description LIKE ?)");
            String like = "%" + term.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) {
            sql.append(" AND s.category = ?");
            params.add(category);
        }
        sql.append(" ORDER BY teachers_offering DESC, s.skill_name LIMIT 200");
        return querySkills(sql.toString(), params);
    }

    public List<String> findCategories() {
        String sql = "SELECT DISTINCT category FROM skills ORDER BY category";
        List<String> categories = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                categories.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the skill categories", e);
        }
        return categories;
    }

    public long countSkills() {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM skills");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw DaoUtils.wrap("counting skills", e);
        }
    }

    // ------------------------------------------------------------------ student skills

    public long addStudentSkill(Connection connection, StudentSkill listing) throws SQLException {
        String sql = "INSERT INTO student_skills (student_id, skill_id, skill_type, proficiency_level, description, is_active) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, listing.getStudentId());
            ps.setLong(2, listing.getSkillId());
            ps.setString(3, listing.getSkillType().name());
            ps.setString(4, listing.getProficiency().name());
            DaoUtils.setNullableString(ps, 5, listing.getDescription());
            ps.setBoolean(6, listing.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("Inserting the skill listing did not return a generated key.");
        }
    }

    public long addStudentSkill(StudentSkill listing) {
        try (Connection connection = DBConnection.getConnection()) {
            return addStudentSkill(connection, listing);
        } catch (SQLException e) {
            if (DaoUtils.isDuplicateKey(e)) {
                throw new DuplicateResourceException("You already have a listing for this skill.", e);
            }
            throw DaoUtils.wrap("adding the skill listing", e);
        }
    }

    public void updateStudentSkill(Connection connection, StudentSkill listing) throws SQLException {
        String sql = "UPDATE student_skills SET proficiency_level = ?, description = ?, is_active = ? "
                + "WHERE student_skill_id = ? AND student_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, listing.getProficiency().name());
            DaoUtils.setNullableString(ps, 2, listing.getDescription());
            ps.setBoolean(3, listing.isActive());
            ps.setLong(4, listing.getStudentSkillId());
            ps.setLong(5, listing.getStudentId());
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Listing " + listing.getStudentSkillId() + " does not belong to this student.");
            }
        }
    }

    public void updateStudentSkill(StudentSkill listing) {
        try (Connection connection = DBConnection.getConnection()) {
            updateStudentSkill(connection, listing);
        } catch (SQLException e) {
            throw DaoUtils.wrap("updating the skill listing", e);
        }
    }

    /** Administrator moderation: hide an inappropriate listing. */
    public void setListingActive(long studentSkillId, boolean active) {
        String sql = "UPDATE student_skills SET is_active = ? WHERE student_skill_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setLong(2, studentSkillId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw DaoUtils.wrap("moderating the skill listing", e);
        }
    }

    public Optional<StudentSkill> findStudentSkillById(long studentSkillId) {
        String sql = StudentSkillQuery.BASE + " WHERE ss.student_skill_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentSkillId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapStudentSkill(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the skill listing", e);
        }
    }

    public List<StudentSkill> listByStudent(long studentId, SkillType type) {
        StringBuilder sql = new StringBuilder(StudentSkillQuery.BASE + " WHERE ss.student_id = ?");
        if (type != null) {
            sql.append(" AND ss.skill_type = ?");
        }
        sql.append(" ORDER BY ss.is_active DESC, s.skill_name");
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            ps.setLong(1, studentId);
            if (type != null) {
                ps.setString(2, type.name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<StudentSkill> listings = new ArrayList<>();
                while (rs.next()) {
                    listings.add(mapStudentSkill(rs));
                }
                return listings;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading your skills", e);
        }
    }

    /** Active teachers of a skill, optionally filtered by a minimum proficiency. */
    public List<StudentSkill> findTeachers(long skillId, Proficiency minimum) {
        StringBuilder sql = new StringBuilder(
                StudentSkillQuery.BASE + " WHERE ss.skill_id = ? AND ss.skill_type = 'OFFER' AND ss.is_active = TRUE "
                + "AND st.account_status = 'ACTIVE'");
        if (minimum != null) {
            sql.append(" AND ss.proficiency_level IN (").append(allowedProficiencies(minimum)).append(")");
        }
        sql.append(" ORDER BY FIELD(ss.proficiency_level, 'ADVANCED', 'INTERMEDIATE', 'BEGINNER'), st.full_name");
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            ps.setLong(1, skillId);
            try (ResultSet rs = ps.executeQuery()) {
                List<StudentSkill> listings = new ArrayList<>();
                while (rs.next()) {
                    listings.add(mapStudentSkill(rs));
                }
                return listings;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("searching for teachers", e);
        }
    }

    private String allowedProficiencies(Proficiency minimum) {
        return switch (minimum) {
            case ADVANCED -> "'ADVANCED'";
            case INTERMEDIATE -> "'INTERMEDIATE', 'ADVANCED'";
            case BEGINNER -> "'BEGINNER', 'INTERMEDIATE', 'ADVANCED'";
        };
    }

    public long countListings() {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM student_skills WHERE is_active = TRUE");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw DaoUtils.wrap("counting skill listings", e);
        }
    }

    // ------------------------------------------------------------------ mapping helpers

    private List<Skill> querySkills(String sql, List<Object> params) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Skill> skills = new ArrayList<>();
                while (rs.next()) {
                    Skill skill = mapSkill(rs);
                    skill.setTeachersOffering(readInt(rs, "teachers_offering"));
                    skill.setLearnersWanting(readInt(rs, "learners_wanting"));
                    skill.setCompletedSessions(readInt(rs, "completed_sessions"));
                    skills.add(skill);
                }
                return skills;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading skills", e);
        }
    }

    private static int readInt(ResultSet rs, String column) {
        try {
            return rs.getInt(column);
        } catch (SQLException e) {
            return 0;
        }
    }

    private static Skill mapSkill(ResultSet rs) throws SQLException {
        Skill skill = new Skill();
        skill.setSkillId(rs.getLong("skill_id"));
        skill.setSkillName(rs.getString("skill_name"));
        skill.setCategory(rs.getString("category"));
        skill.setDescription(rs.getString("description"));
        skill.setCreatedAt(DaoUtils.getDateTime(rs, "created_at"));
        return skill;
    }

    private static StudentSkill mapStudentSkill(ResultSet rs) throws SQLException {
        StudentSkill listing = new StudentSkill();
        listing.setStudentSkillId(rs.getLong("student_skill_id"));
        listing.setStudentId(rs.getLong("student_id"));
        listing.setSkillId(rs.getLong("skill_id"));
        listing.setSkillType(SkillType.fromDb(rs.getString("skill_type")));
        listing.setProficiency(Proficiency.fromDb(rs.getString("proficiency_level")));
        listing.setDescription(rs.getString("description"));
        listing.setActive(rs.getBoolean("is_active"));
        listing.setCreatedAt(DaoUtils.getDateTime(rs, "created_at"));
        listing.setUpdatedAt(DaoUtils.getDateTime(rs, "updated_at"));
        listing.setSkillName(rs.getString("skill_name"));
        listing.setCategory(rs.getString("category"));
        listing.setStudentName(rs.getString("full_name"));
        listing.setDepartment(rs.getString("department"));
        return listing;
    }

    /** Shared SELECT prefix joining listings with the skill catalogue and the student. */
    private static final class StudentSkillQuery {
        static final String BASE =
                "SELECT ss.student_skill_id, ss.student_id, ss.skill_id, ss.skill_type, ss.proficiency_level, "
                + "ss.description, ss.is_active, ss.created_at, ss.updated_at, "
                + "s.skill_name, s.category, st.full_name, st.department "
                + "FROM student_skills ss "
                + "JOIN skills s   ON s.skill_id = ss.skill_id "
                + "JOIN students st ON st.student_id = ss.student_id";
    }
}
