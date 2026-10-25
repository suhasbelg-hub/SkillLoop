package com.skillloop.util;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Date/time helpers shared by the DAO, service and Swing layers. */
public final class DateTimeUtil {

    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private DateTimeUtil() {
        // utility class
    }

    /** Converts a SQL timestamp (may be null) to a {@link LocalDateTime}. */
    public static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /** Converts a {@link LocalDateTime} (may be null) to a SQL timestamp. */
    public static Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    /** Formats a date/time for tables and cards; returns "-" for null. */
    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? "-" : dateTime.format(DATE_TIME);
    }

    public static String formatDate(LocalDate date) {
        return date == null ? "-" : date.format(DATE);
    }

    /** "1 h 30 min" style duration text. */
    public static String formatDuration(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (hours == 0) {
            return mins + " min";
        }
        return mins == 0 ? hours + " h" : hours + " h " + mins + " min";
    }

    /** Formats decimal hours with one decimal place ("2.5 h"). */
    public static String formatHours(double hours) {
        return String.format("%.1f h", hours);
    }

    /** Parses "yyyy-MM-dd HH:mm" (the format used by the booking form), or null when blank. */
    public static LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
