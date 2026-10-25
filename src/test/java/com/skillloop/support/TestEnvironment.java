package com.skillloop.support;

import com.skillloop.model.LearningSession;
import com.skillloop.service.BookingService;
import com.skillloop.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Helpers for the database integration tests.
 *
 * <p>Integration tests only run when a MySQL server is reachable; otherwise they are skipped
 * (never failed), so <code>mvn test</code> also works on a machine without MySQL. Every helper is
 * non-destructive: it only ever touches rows that a test created itself, and the clean-up removes
 * the test students instead of truncating application tables.</p>
 */
public final class TestEnvironment {

    /** Prefix used for every test account, so clean-up only ever touches test data. */
    public static final String TEST_EMAIL_DOMAIN = "skillloop.test";

    private static Boolean available;

    private TestEnvironment() {
        // test helper
    }

    /** True when the configured MySQL database can be reached. */
    public static synchronized boolean isDatabaseAvailable() {
        if (available == null) {
            available = DBConnection.testConnection().isSuccess();
            if (!available) {
                System.out.println("[TestEnvironment] MySQL is not reachable - database tests are skipped.");
            }
        }
        return available;
    }

    /** Marks the current test as skipped when no database is available. */
    public static void assumeDatabase() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isDatabaseAvailable(),
                "No MySQL server available - configure config/application.properties to run this test.");
    }

    /** Unique email address for a test student. */
    public static String uniqueEmail() {
        return TEST_EMAIL_DOMAIN + "." + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    }

    /** Unique skill name for a test skill. */
    public static String uniqueSkillName() {
        return "TestSkill-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Creates an ACCEPTED session whose schedule is moved into the past, so that completion and
     * review flows can be exercised without waiting for a real appointment.
     *
     * <p>The booking itself goes through the ordinary service (which correctly refuses
     * appointments in the past); afterwards only the schedule of that one session row is
     * back-dated. Everything else - the credit reservation, the notification and the state
     * machine - is the real, unmodified application code.</p>
     */
    public static LearningSession acceptedPastSession(BookingService bookingService, long learnerId, long teacherId,
                                                      long skillId, int durationMinutes) {
        LocalDateTime planned = LocalDateTime.now().plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LearningSession session = bookingService.requestBooking(learnerId, teacherId, skillId, null,
                planned, durationMinutes, "integration test session");
        bookingService.accept(session.getSessionId(), teacherId);

        LocalDateTime pastStart = LocalDateTime.now().minusHours(durationMinutes / 60L + 2).minusMinutes(30);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE learning_sessions SET start_time = ?, end_time = ? WHERE session_id = ?")) {
                ps.setObject(1, pastStart);
                ps.setObject(2, pastStart.plusMinutes(durationMinutes));
                ps.setLong(3, session.getSessionId());
                ps.executeUpdate();
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not back-date the test session: " + e.getMessage(), e);
        }
        return bookingService.find(session.getSessionId()).orElseThrow();
    }

    /** Convenience overload for the tests that only need one completed session. */
    public static LearningSession completedSession(BookingService bookingService, long learnerId, long teacherId,
                                                   long skillId, int durationMinutes) {
        LearningSession session = acceptedPastSession(bookingService, learnerId, teacherId, skillId, durationMinutes);
        bookingService.confirmCompletion(session.getSessionId(), teacherId);
        return bookingService.confirmCompletion(session.getSessionId(), learnerId);
    }

    /**
     * Removes students that were created by a test, together with every row that belongs to them.
     * Only the given ids are ever touched - no truncate, no drop, no global delete.
     */
    public static void deleteStudent(long studentId) {
        try (Connection connection = DBConnection.getConnection()) {
            execute(connection, "UPDATE learning_sessions  SET resolved_by = NULL WHERE resolved_by = ?", studentId);
            execute(connection, "UPDATE credit_transactions SET created_by  = NULL WHERE created_by  = ?", studentId);
            execute(connection, "DELETE FROM admin_audit_events WHERE admin_id = ?", studentId);
            execute(connection, "DELETE FROM group_sessions     WHERE teacher_id = ?", studentId);
            execute(connection, "DELETE FROM learning_sessions  WHERE teacher_id = ? OR learner_id = ?",
                    studentId, studentId);
            execute(connection, "DELETE FROM students           WHERE student_id = ?", studentId);
        } catch (SQLException e) {
            System.out.println("[TestEnvironment] clean-up of student " + studentId + " failed: " + e.getMessage());
        }
    }

    /** Removes all students created by a test in one call. */
    public static void deleteStudents(List<Long> studentIds) {
        studentIds.forEach(TestEnvironment::deleteStudent);
    }

    private static void execute(Connection connection, String sql, long... ids) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < ids.length; i++) {
                ps.setLong(i + 1, ids[i]);
            }
            ps.executeUpdate();
        }
    }
}
