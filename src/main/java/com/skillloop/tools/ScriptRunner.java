package com.skillloop.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Loads and executes MySQL script files (schema.sql, seed.sql, views.sql). */
public final class ScriptRunner {

    private ScriptRunner() {
        // helper class
    }

    /** Reads a script from the classpath (jar) or, while developing, from the source folder. */
    public static String load(String resourceName, Path sourcePath) throws IOException {
        try (InputStream in = ScriptRunner.class.getResourceAsStream(resourceName)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        if (sourcePath != null && Files.isRegularFile(sourcePath)) {
            return Files.readString(sourcePath, StandardCharsets.UTF_8);
        }
        throw new IOException("Script '" + resourceName + "' was not found on the classpath or at " + sourcePath);
    }

    /**
     * Splits a script into single statements, dropping comment lines and empty statements.
     * SkillLoop scripts contain no DELIMITER blocks and no semicolons inside string literals.
     */
    public static List<String> split(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : script.split("\\R")) {
            String line = rawLine;
            int commentAt = line.indexOf("--");
            if (commentAt >= 0) {
                line = line.substring(0, commentAt);
            }
            current.append(line).append('\n');
            if (line.trim().endsWith(";")) {
                String statement = current.toString().trim();
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
                current.setLength(0);
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }

    /**
     * Executes every statement of a script.
     *
     * @return number of executed statements
     */
    public static int execute(Connection connection, List<String> statements, boolean verbose) throws SQLException {
        int executed = 0;
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                try {
                    statement.execute(sql);
                    executed++;
                    if (verbose) {
                        System.out.println("  [ok] " + firstLine(sql));
                    }
                } catch (SQLException e) {
                    if (isHarmless(e, sql)) {
                        if (verbose) {
                            System.out.println("  [skip] " + firstLine(sql) + " (" + e.getMessage() + ")");
                        }
                    } else {
                        System.out.println("  [FAILED] " + firstLine(sql));
                        throw e;
                    }
                }
            }
        }
        return executed;
    }

    /** "Already exists" errors are fine because every script is re-runnable by design. */
    private static boolean isHarmless(SQLException e, String sql) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return message.contains("already exists") || message.contains("duplicate");
    }

    private static String firstLine(String sql) {
        String[] lines = sql.split("\\R");
        String first = lines.length > 0 ? lines[0].trim() : sql;
        if (first.length() > 110) {
            first = first.substring(0, 107) + "...";
        }
        return first;
    }
}
