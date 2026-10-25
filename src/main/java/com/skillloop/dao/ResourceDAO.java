package com.skillloop.dao;

import com.skillloop.model.LearningResource;
import com.skillloop.util.DBConnection;
import com.skillloop.util.DaoUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Persistence for the shared learning resources (notes, links, practice material). */
public class ResourceDAO {

    private static final String BASE =
            "SELECT r.resource_id, r.student_id, r.skill_id, r.title, r.description, r.resource_url, r.created_at, "
            + "s.full_name AS student_name, sk.skill_name "
            + "FROM learning_resources r "
            + "JOIN students s ON s.student_id = r.student_id "
            + "JOIN skills sk ON sk.skill_id = r.skill_id ";

    public long insert(Connection connection, LearningResource resource) throws SQLException {
        String sql = "INSERT INTO learning_resources (student_id, skill_id, title, description, resource_url) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, resource.getStudentId());
            ps.setLong(2, resource.getSkillId());
            ps.setString(3, resource.getTitle());
            DaoUtils.setNullableString(ps, 4, resource.getDescription());
            DaoUtils.setNullableString(ps, 5, resource.getResourceUrl());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("Inserting the resource did not return a generated key.");
        }
    }

    public long insert(LearningResource resource) {
        try (Connection connection = DBConnection.getConnection()) {
            return insert(connection, resource);
        } catch (SQLException e) {
            throw DaoUtils.wrap("sharing the learning resource", e);
        }
    }

    public List<LearningResource> listAll(int limit) {
        return query(BASE + "ORDER BY r.created_at DESC LIMIT ?", List.of(limit));
    }

    public List<LearningResource> listForSkill(long skillId) {
        return query(BASE + "WHERE r.skill_id = ? ORDER BY r.created_at DESC LIMIT 100", List.of(skillId));
    }

    public List<LearningResource> listByStudent(long studentId) {
        return query(BASE + "WHERE r.student_id = ? ORDER BY r.created_at DESC LIMIT 100", List.of(studentId));
    }

    public void delete(long resourceId, long ownerId) {
        String sql = "DELETE FROM learning_resources WHERE resource_id = ? AND student_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, resourceId);
            ps.setLong(2, ownerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw DaoUtils.wrap("deleting the resource", e);
        }
    }

    private List<LearningResource> query(String sql, List<Object> params) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<LearningResource> resources = new ArrayList<>();
                while (rs.next()) {
                    LearningResource resource = new LearningResource();
                    resource.setResourceId(rs.getLong("resource_id"));
                    resource.setStudentId(rs.getLong("student_id"));
                    resource.setSkillId(rs.getLong("skill_id"));
                    resource.setTitle(rs.getString("title"));
                    resource.setDescription(rs.getString("description"));
                    resource.setResourceUrl(rs.getString("resource_url"));
                    resource.setCreatedAt(DaoUtils.getDateTime(rs, "created_at"));
                    resource.setStudentName(rs.getString("student_name"));
                    resource.setSkillName(rs.getString("skill_name"));
                    resources.add(resource);
                }
                return resources;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading learning resources", e);
        }
    }
}
