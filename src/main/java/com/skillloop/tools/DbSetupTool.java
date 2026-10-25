package com.skillloop.tools;

import com.skillloop.util.AppConfig;
import com.skillloop.util.DBConnection;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/**
 * Safe database setup utility.
 *
 * <pre>
 *   mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.DbSetupTool -Dexec.args="--check"
 *   mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.DbSetupTool -Dexec.args="--init"
 *   mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.DbSetupTool -Dexec.args="--seed"
 *   mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.DbSetupTool -Dexec.args="--views"
 *   mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.DbSetupTool -Dexec.args="--all"
 * </pre>
 *
 * <b>This tool never issues DROP, TRUNCATE or DELETE statements.</b> Every script uses
 * "IF NOT EXISTS" / "CREATE OR REPLACE" and can be executed repeatedly without losing data.
 * It refuses to run when the configured user is 'root' unless SKILLLOOP_ALLOW_ROOT=YES is set,
 * so that the application never depends on the MySQL root account.
 */
public final class DbSetupTool {

    private static final Path SOURCE_DB_DIR = Path.of("src", "main", "resources", "db");

    private DbSetupTool() {
        // command line tool
    }

    public static void main(String[] args) {
        List<String> arguments = List.of(args);
        if (arguments.isEmpty() || arguments.contains("--help")) {
            printUsage();
            return;
        }
        AppConfig config = AppConfig.get();
        System.out.println("SkillLoop - database setup");
        System.out.println("Configuration: " + config.maskedDescription());
        try {
            if (arguments.contains("--check")) {
                check();
            }
            if (arguments.contains("--init")) {
                runSchema();
            }
            if (arguments.contains("--seed")) {
                runSeed();
            }
            if (arguments.contains("--views")) {
                runViews();
            }
            if (arguments.contains("--all")) {
                check();
                runSchema();
                runViews();
                runSeed();
            }
        } catch (Exception e) {
            System.out.println();
            System.out.println("FAILED: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void check() throws Exception {
        var result = DBConnection.testConnection();
        System.out.println("Connection: " + result);
        if (!result.isSuccess()) {
            throw new IllegalStateException(result.getMessage());
        }
    }

    private static void runSchema() throws Exception {
        refuseRoot();
        String script = ScriptRunner.load("/db/schema.sql", SOURCE_DB_DIR.resolve("schema.sql"));
        List<String> statements = ScriptRunner.split(script);
        System.out.println("Applying schema.sql (" + statements.size() + " statements)...");
        int executed = executeOnServer(statements);
        System.out.println("schema.sql applied: " + executed + " statements executed (nothing was dropped).");
    }

    private static void runViews() throws Exception {
        String script = ScriptRunner.load("/db/views.sql", SOURCE_DB_DIR.resolve("views.sql"));
        List<String> statements = ScriptRunner.split(script);
        System.out.println("Applying views.sql (" + statements.size() + " statements)...");
        try (Connection connection = DBConnection.getConnection()) {
            int executed = ScriptRunner.execute(connection, statements, true);
            System.out.println("views.sql applied: " + executed + " statements executed.");
        }
    }

    private static void runSeed() throws Exception {
        String script = ScriptRunner.load("/db/seed.sql", SOURCE_DB_DIR.resolve("seed.sql"));
        List<String> statements = ScriptRunner.split(script);
        System.out.println("Applying seed.sql (" + statements.size() + " statements)...");
        try (Connection connection = DBConnection.getConnection()) {
            int executed = ScriptRunner.execute(connection, statements, true);
            System.out.println("seed.sql applied: " + executed + " statements executed.");
        }
    }

    /**
     * Connects to the MySQL server <em>without</em> selecting a database, so that
     * "CREATE DATABASE IF NOT EXISTS skillloop" works on a fresh server.
     */
    private static int executeOnServer(List<String> statements) throws SQLException {
        AppConfig config = AppConfig.get();
        String serverUrl = serverUrl(config.dbUrl());
        try (Connection connection = DriverManager.getConnection(serverUrl, config.dbUser(), config.dbPassword())) {
            return ScriptRunner.execute(connection, statements, true);
        }
    }

    /** Strips the database name (and keeps the parameters) from a MySQL JDBC URL. */
    static String serverUrl(String url) {
        // jdbc:mysql://host:3306/skillloop?params  ->  jdbc:mysql://host:3306/?params
        String prefix = "jdbc:mysql://";
        if (!url.startsWith(prefix)) {
            return url;
        }
        String rest = url.substring(prefix.length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return url;
        }
        String hostPart = rest.substring(0, slash);
        String params = "";
        int question = rest.indexOf('?');
        if (question > slash) {
            params = rest.substring(question);
        }
        return prefix + hostPart + "/" + params;
    }

    private static void refuseRoot() {
        if ("root".equalsIgnoreCase(AppConfig.get().dbUser())
                && !"YES".equalsIgnoreCase(System.getenv("SKILLLOOP_ALLOW_ROOT"))) {
            throw new IllegalStateException(
                    "The configured database user is 'root'. SkillLoop should run with a dedicated application "
                    + "user (see docs/SETUP_GUIDE.md). Set SKILLLOOP_ALLOW_ROOT=YES to override this guard.");
        }
    }

    private static void printUsage() {
        System.out.println("Usage: DbSetupTool [--check] [--init] [--seed] [--views] [--all]");
        System.out.println();
        System.out.println("  --check  test the JDBC connection");
        System.out.println("  --init   create the database and all tables (CREATE ... IF NOT EXISTS)");
        System.out.println("  --views  create or replace the reporting views");
        System.out.println("  --seed   insert the optional demo data (skills, achievements, demo students)");
        System.out.println("  --all    run all of the above");
        System.out.println();
        System.out.println("No step ever drops or deletes data.");
    }
}
