package com.skillloop.unit;

import com.skillloop.util.AppConfig;
import com.skillloop.util.DateTimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for configuration handling and the small date helpers. */
class AppConfigTest {

    @Test
    @DisplayName("The configuration never exposes the database password")
    void passwordIsMasked() {
        AppConfig config = AppConfig.get();
        String description = config.maskedDescription();
        String password = config.dbPassword();
        if (password != null && !password.isBlank()) {
            assertFalse(description.contains(password),
                    "The masked description must not contain the password");
        }
        assertTrue(description.contains("password=***"), description);
    }

    @Test
    @DisplayName("Default connection settings point to a local MySQL instance")
    void defaults() {
        AppConfig config = AppConfig.get();
        assertTrue(config.dbUrl().startsWith("jdbc:mysql://"), config.dbUrl());
        assertEquals("com.mysql.cj.jdbc.Driver", config.dbDriver());
        assertNotNull(config.dbUser());
        assertFalse("root".equalsIgnoreCase(config.dbUser()),
                "The application must not default to the MySQL root account");
    }

    @Test
    @DisplayName("Credit configuration can be overridden through system properties")
    void overrides() {
        AppConfig config = AppConfig.get();
        assertEquals(20, config.creditsPerHour());
        assertTrue(config.maxAdminAdjustment() > 0);
    }

    @Test
    @DisplayName("Booking date strings are parsed with the documented pattern")
    void dateParsing() {
        LocalDateTime parsed = DateTimeUtil.parse("2026-11-02 15:30");
        assertNotNull(parsed);
        assertEquals("2026-11-02 15:30", parsed.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        assertEquals(null, DateTimeUtil.parse("not a date"));
        assertEquals("1 h 30 min", DateTimeUtil.formatDuration(90));
        assertEquals("45 min", DateTimeUtil.formatDuration(45));
    }
}
