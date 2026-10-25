package com.skillloop.dao;

import com.skillloop.exception.DuplicateResourceException;
import com.skillloop.model.AccountStatus;
import com.skillloop.model.Role;
import com.skillloop.model.Student;
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

/**
 * Persistence for the {@code students} table.
 *
 * <p>Every SQL statement is a {@link PreparedStatement}, so user input can never change the
 * structure of a query (no SQL injection).</p>
 */
public class StudentDAO {

    private static final String COLUMNS =
            "student_id, full_name, email, password_hash, department, semester, bio, role, "
            + "account_status, created_at, updated_at";

    /** Inserts a student and returns the generated primary key. */
    public long insert(Connection connection, Student student) throws SQLException {
        String sql = "INSERT INTO students (full_name, email, password_hash, department, semester, bio, role, account_status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, student.getFullName());
            ps.setString(2, student.getEmail());
            ps.setString(3, student.getPasswordHash());
            ps.setString(4, student.getDepartment());
            ps.setInt(5, student.getSemester());
            DaoUtils.setNullableString(ps, 6, student.getBio());
            ps.setString(7, student.getRole().name());
            ps.setString(8, student.getAccountStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("Inserting a student did not return a generated key.");
        }
    }

    public long insert(Student student) {
        try (Connection connection = DBConnection.getConnection()) {
            return insert(connection, student);
        } catch (SQLException e) {
            if (DaoUtils.isDuplicateKey(e)) {
                throw new DuplicateResourceException("The email address '" + student.getEmail() + "' is already registered.", e);
            }
            throw DaoUtils.wrap("creating the student account", e);
        }
    }

    public Optional<Student> findByEmail(Connection connection, String email) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM students WHERE email = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email == null ? "" : email.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Student> findByEmail(String email) {
        try (Connection connection = DBConnection.getConnection()) {
            return findByEmail(connection, email);
        } catch (SQLException e) {
            throw DaoUtils.wrap("looking up the email address", e);
        }
    }

    public Optional<Student> findById(Connection connection, long studentId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM students WHERE student_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Student> findById(long studentId) {
        try (Connection connection = DBConnection.getConnection()) {
            return findById(connection, studentId);
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the profile", e);
        }
    }

    public boolean existsEmail(Connection connection, String email) throws SQLException {
        String sql = "SELECT COUNT(*) FROM students WHERE email = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email == null ? "" : email.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Updates name, department, semester and bio of an existing student. */
    public void updateProfile(Connection connection, Student student) throws SQLException {
        String sql = "UPDATE students SET full_name = ?, department = ?, semester = ?, bio = ? WHERE student_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, student.getFullName());
            ps.setString(2, student.getDepartment());
            ps.setInt(3, student.getSemester());
            DaoUtils.setNullableString(ps, 4, student.getBio());
            ps.setLong(5, student.getStudentId());
            ps.executeUpdate();
        }
    }

    public void updateProfile(Student student) {
        try (Connection connection = DBConnection.getConnection()) {
            updateProfile(connection, student);
        } catch (SQLException e) {
            throw DaoUtils.wrap("updating the profile", e);
        }
    }

    public void updatePasswordHash(long studentId, String passwordHash) {
        String sql = "UPDATE students SET password_hash = ? WHERE student_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setLong(2, studentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw DaoUtils.wrap("updating the password", e);
        }
    }

    /** Administrator operation: suspend, reactivate or deactivate an account. */
    public void updateAccountStatus(long studentId, AccountStatus status) {
        String sql = "UPDATE students SET account_status = ? WHERE student_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, studentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw DaoUtils.wrap("changing the account status", e);
        }
    }

    /** Administrator operation: promote or demote an account. */
    public void updateRole(long studentId, Role role) {
        String sql = "UPDATE students SET role = ? WHERE student_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, role.name());
            ps.setLong(2, studentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw DaoUtils.wrap("changing the role", e);
        }
    }

    /** Searches students by name, email or department (used by the administrator console). */
    public List<Student> search(String term, AccountStatus status, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM students WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (term != null && !term.isBlank()) {
            sql.append(" AND (full_name LIKE ? OR email LIKE ? OR department LIKE ?)");
            String like = "%" + term.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (status != null) {
            sql.append(" AND account_status = ?");
            params.add(status.name());
        }
        sql.append(" ORDER BY full_name LIMIT ?");
        params.add(limit);
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Student> students = new ArrayList<>();
                while (rs.next()) {
                    students.add(map(rs));
                }
                return students;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("searching students", e);
        }
    }

    public long countAll() {
        return count("SELECT COUNT(*) FROM students");
    }

    public long countByStatus(AccountStatus status) {
        return count("SELECT COUNT(*) FROM students WHERE account_status = '" + status.name() + "'");
    }

    public long countAdmins() {
        return count("SELECT COUNT(*) FROM students WHERE role = 'ADMIN'");
    }

    private long count(String sql) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw DaoUtils.wrap("counting students", e);
        }
    }

    /** Maps the current row of a result set to a {@link Student}. */
    public static Student map(ResultSet rs) throws SQLException {
        Student student = new Student();
        student.setStudentId(rs.getLong("student_id"));
        student.setFullName(rs.getString("full_name"));
        student.setEmail(rs.getString("email"));
        student.setPasswordHash(rs.getString("password_hash"));
        student.setDepartment(rs.getString("department"));
        student.setSemester(rs.getInt("semester"));
        student.setBio(rs.getString("bio"));
        student.setRole(Role.fromDb(rs.getString("role")));
        student.setAccountStatus(AccountStatus.fromDb(rs.getString("account_status")));
        student.setCreatedAt(DaoUtils.getDateTime(rs, "created_at"));
        student.setUpdatedAt(DaoUtils.getDateTime(rs, "updated_at"));
        return student;
    }
}
