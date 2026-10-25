package com.skillloop.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static guards for the security rules of the project. They scan the real source files, so a
 * regression (a raw Statement, SQL inside a Swing handler, a committed password) fails the build
 * even on a machine without MySQL.
 */
class SourceSecurityTest {

    /**
     * SQL only ever appears as the leading keyword of a statement; a label such as the
     * "Update password" button must not trigger the guard.
     */
    private static final List<Pattern> SQL_PATTERNS = List.of(
            Pattern.compile("(?i)\\bselect\\s+[\\w*,\\(\\)]+\\s+from\\b"),
            Pattern.compile("(?i)\\binsert\\s+into\\b"),
            Pattern.compile("(?i)\\bupdate\\s+[A-Za-z_]+\\s+set\\b"),
            Pattern.compile("(?i)\\bdelete\\s+from\\b"),
            Pattern.compile("(?i)\\b(drop|create)\\s+(table|database|view)\\b"),
            Pattern.compile("(?i)\\bpreparestatement\\s*\\("),
            Pattern.compile("(?i)\\bcreatestatement\\s*\\("));

    /** A JDBC URL that carries a real password inside one string literal. */
    private static final Pattern URL_WITH_PASSWORD =
            Pattern.compile("(?i)\"jdbc:mysql://[^\"]*password=([^\"&?\\s]+)");

    private static final Path MAIN = Paths.get("src", "main", "java", "com", "skillloop");
    private static final Path RESOURCES = Paths.get("src", "main", "resources");

    private List<Path> javaFiles(Path packageDir) throws IOException {
        if (!Files.isDirectory(packageDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(packageDir)) {
            return stream.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    private String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("No source file creates a plain java.sql.Statement")
    void everyStatementIsPrepared() throws IOException {
        for (Path file : javaFiles(Paths.get("src", "main", "java"))) {
            String source = read(file);
            assertFalse(source.matches("(?s).*\\bcreatestatement\\s*\\(.*"),
                    file + " must use PreparedStatement instead of a plain Statement");
        }
    }

    @Test
    @DisplayName("Every data access class prepares its statements")
    void dataAccessUsesPreparedStatements() throws IOException {
        List<Path> daos = javaFiles(MAIN.resolve("dao"));
        assertFalse(daos.isEmpty(), "The DAO package must exist");
        for (Path dao : daos) {
            String source = read(dao);
            assertTrue(source.contains("prepareStatement("),
                    dao.getFileName() + " must build its statements with prepareStatement()");
            assertTrue(source.contains("try ("), dao.getFileName() + " must use try-with-resources");
        }
    }

    @Test
    @DisplayName("The Swing layer never contains SQL")
    void uiHasNoSql() throws IOException {
        List<Path> uiFiles = javaFiles(MAIN.resolve("ui"));
        assertFalse(uiFiles.isEmpty(), "The UI package must exist");
        for (Path file : uiFiles) {
            String source = read(file);
            for (Pattern pattern : SQL_PATTERNS) {
                Matcher matcher = pattern.matcher(source);
                assertFalse(matcher.find(),
                        file.getFileName() + " must not contain SQL (/" + pattern.pattern() + "/)");
            }
        }
    }

    @Test
    @DisplayName("No credential is hard-coded in the sources")
    void noHardCodedCredentials() throws IOException {
        for (Path file : javaFiles(Paths.get("src", "main", "java"))) {
            String source = read(file);
            Matcher matcher = URL_WITH_PASSWORD.matcher(source);
            while (matcher.find()) {
                String value = matcher.group(1);
                assertTrue(value.startsWith("${") || value.contains("***") || value.isBlank(),
                        file + " must not embed a JDBC password (found '" + value + "')");
            }
            assertFalse(source.contains("private static final String PASSWORD ="),
                    file + " must not define a password constant");
        }
    }

    @Test
    @DisplayName("The example configuration only contains placeholders")
    void exampleConfigurationHasPlaceholdersOnly() throws IOException {
        Path example = RESOURCES.resolve("config").resolve("application.properties.example");
        assertTrue(Files.exists(example), "The example configuration must be shipped");
        String content = read(example);
        assertTrue(content.contains("skillloop.db.url"), "The URL key is documented");
        assertTrue(content.contains("skillloop.db.user"), "The user key is documented");
        assertTrue(content.contains("skillloop.db.password"), "The password key is documented");
        String passwordLine = content.lines()
                .filter(line -> line.startsWith("skillloop.db.password"))
                .findFirst().orElse("");
        assertTrue(passwordLine.contains("${") || passwordLine.trim().endsWith("=")
                        || passwordLine.contains("CHANGE_ME") || passwordLine.contains("<"),
                "The example password must stay a placeholder: " + passwordLine);
    }

    @Test
    @DisplayName("The real configuration file is not part of the repository")
    void localConfigurationIsIgnored() throws IOException {
        Path gitIgnore = Paths.get(".gitignore");
        assertTrue(Files.exists(gitIgnore), ".gitignore must exist");
        String content = read(gitIgnore);
        assertTrue(content.contains("application.properties"),
                ".gitignore must exclude the local application.properties");
        assertFalse(Files.exists(RESOURCES.resolve("config").resolve("application.properties")),
                "The local configuration must never be committed");
    }
}
