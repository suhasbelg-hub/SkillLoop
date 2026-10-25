package com.skillloop.util;

import com.skillloop.exception.DatabaseException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Small helpers shared by all DAO classes: null-safe reading, enum conversion and
 * consistent wrapping of {@link SQLException} into {@link DatabaseException}.
 */
public final class DaoUtils {

    private DaoUtils() {
        // utility class
    }

    /** Wraps a checked SQLException into the application's unchecked DatabaseException. */
    public static DatabaseException wrap(String context, SQLException e) {
        return new DatabaseException("Database error while " + context + ": " + e.getMessage(), e);
    }

    public static LocalDateTime getDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public static void setDateTime(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    /** Reads a nullable numeric column into a {@link Long} (null when the column is NULL). */
    public static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    public static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            ps.setNull(index, java.sql.Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    public static String getString(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value;
    }

    /** Reads an ENUM column and converts it to the matching Java enum constant. */
    public static <E extends Enum<E>> E getEnum(ResultSet rs, String column, Class<E> type) throws SQLException {
        String value = rs.getString(column);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SQLException("Unexpected value '" + value + "' in column " + column);
        }
    }

    public static boolean isDuplicateKey(SQLException e) {
        String state = e.getSQLState();
        return "23000".equals(state) || "23505".equals(state) || e.getErrorCode() == 1062;
    }

    /** True when the failure was caused by a lock wait timeout / deadlock (retryable). */
    public static boolean isConcurrencyFailure(SQLException e) {
        return e.getErrorCode() == 1205 || e.getErrorCode() == 1213;
    }
}
