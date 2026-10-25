package com.skillloop.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the database scripts: they must be non-destructive and must cover the tables and SQL
 * features required by the project brief.
 */
class DatabaseScriptTest {

    private String read(String resource) throws IOException {
        try (InputStream in = DatabaseScriptTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("schema.sql contains no DROP TABLE, DROP DATABASE, TRUNCATE or DELETE FROM statement")
    void schemaIsNonDestructive() throws IOException {
        String schema = stripComments(read("/db/schema.sql")).toUpperCase();
        assertFalse(schema.contains("DROP TABLE"), "schema.sql must never drop a table");
        assertFalse(schema.contains("DROP DATABASE"), "schema.sql must never drop the database");
        assertFalse(schema.contains("DROP USER"), "schema.sql must never drop a user");
        assertFalse(schema.contains("TRUNCATE"), "schema.sql must never truncate");
        assertFalse(schema.contains("DELETE FROM"), "schema.sql must never delete rows");
        assertTrue(schema.contains("ON DELETE CASCADE"),
                "Child rows are removed through cascading foreign keys, not through DELETE statements");
    }

    @Test
    @DisplayName("Every table is created with IF NOT EXISTS so the script is re-runnable")
    void schemaIsReRunnable() throws IOException {
        String schema = read("/db/schema.sql");
        assertTrue(schema.contains("CREATE DATABASE IF NOT EXISTS skillloop"));
        assertEquals(15, countOccurrences(schema, "CREATE TABLE IF NOT EXISTS"),
                "Expected 15 application tables");
    }

    @Test
    @DisplayName("The schema declares primary keys, foreign keys, unique keys and CHECK constraints")
    void schemaDeclaresIntegrityRules() throws IOException {
        String schema = read("/db/schema.sql").toUpperCase();
        assertTrue(schema.contains("PRIMARY KEY"));
        assertTrue(schema.contains("FOREIGN KEY"));
        assertTrue(schema.contains("UNIQUE"));
        assertTrue(schema.contains("CHECK ("));
        assertTrue(schema.contains("ENGINE=INNODB"));
        assertTrue(schema.contains("AUTO_INCREMENT"));
    }

    @Test
    @DisplayName("The views cover JOIN, GROUP BY, HAVING and aggregate functions")
    void viewsUseAnalyticalSql() throws IOException {
        String views = read("/db/views.sql").toUpperCase();
        assertTrue(views.contains("JOIN"));
        assertTrue(views.contains("GROUP BY"));
        assertTrue(views.contains("HAVING"));
        assertTrue(views.contains("COUNT("));
        assertTrue(views.contains("SUM(") || views.contains("AVG("));
        assertTrue(views.contains("CREATE OR REPLACE VIEW"));
    }

    @Test
    @DisplayName("seed.sql is optional demo data and is also non-destructive")
    void seedIsNonDestructive() throws IOException {
        String seed = stripComments(read("/db/seed.sql")).toUpperCase();
        assertFalse(seed.contains("DROP TABLE"), "seed.sql must never drop a table");
        assertFalse(seed.contains("DROP DATABASE"), "seed.sql must never drop the database");
        assertFalse(seed.contains("TRUNCATE"), "seed.sql must never truncate");
        assertFalse(seed.contains("DELETE FROM"), "seed.sql must never delete rows");
        assertTrue(seed.contains("INSERT IGNORE") || seed.contains("WHERE NOT EXISTS"),
                "The demo data is inserted only when it is missing");
        assertFalse(seed.contains("'ADMIN'"), "The seed data must not create an administrator account");
    }

    /**
     * Removes SQL line and block comments, so the guards below inspect real statements only -
     * the scripts document their own safety in comments.
     */
    private String stripComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("--[^\\n]*", " ");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
