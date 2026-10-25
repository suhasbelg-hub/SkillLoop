package com.skillloop.tools;

import com.skillloop.util.AppConfig;
import com.skillloop.util.DBConnection;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;
import java.sql.PreparedStatement;

/**
 * Simple JDBC connectivity test.
 *
 * <pre>
 *   mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.ConnectionTest
 * </pre>
 *
 * Prints the (masked) configuration, the driver state, the server version and the number of
 * rows in every SkillLoop table. No credential is ever printed.
 */
public final class ConnectionTest {

    private static final String[] TABLES = {
            "students", "skills", "student_skills", "wallets", "learning_sessions", "availability_slots",
            "credit_transactions", "reviews", "notifications", "learning_resources", "group_sessions",
            "group_participants", "achievements", "student_achievements", "admin_audit_events"
    };

    private ConnectionTest() {
        // command line tool
    }

    public static void main(String[] args) {
        System.out.println("=========================================================");
        System.out.println(" SkillLoop - JDBC connection test");
        System.out.println("=========================================================");
        AppConfig config = AppConfig.get();
        System.out.println("Configuration : " + config.maskedDescription());
        System.out.println("Local config  : " + AppConfig.LOCAL_CONFIG.toAbsolutePath());
        System.out.println("Driver class  : " + config.dbDriver());
        System.out.println();

        DBConnection.TestResult result = DBConnection.testConnection();
        if (!result.isSuccess()) {
            System.out.println("RESULT        : FAILED");
            System.out.println("Reason        : " + result.getMessage());
            System.out.println();
            System.out.println("Checklist:");
            System.out.println("  1. Is the MySQL server running on localhost:3306?");
            System.out.println("  2. Does the database 'skillloop' exist?  (DbSetupTool --init)");
            System.out.println("  3. Are the credentials correct in config/application.properties?");
            System.out.println("  4. Was the MySQL driver downloaded by Maven?  (mvn clean package)");
            System.exit(1);
        }

        System.out.println("RESULT        : OK");
        System.out.println("Server        : MySQL " + result.getServerVersion());
        System.out.println("Database      : " + result.getDatabaseName());
        System.out.println("Latency       : " + result.getLatencyMillis() + " ms");
        System.out.println();

        try (Connection connection = DBConnection.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            System.out.println("JDBC driver   : " + metaData.getDriverName() + " " + metaData.getDriverVersion());
            System.out.println("Tables:");
            for (String table : TABLES) {
                System.out.printf("  %-22s %s%n", table, describeTable(connection, table));
            }
        } catch (Exception e) {
            System.out.println("Could not read the table list: " + e.getMessage());
        }
        System.out.println();
        System.out.println("The connection works. You can now start the application.");
    }

    /**
     * Counts the rows of one table. The table name cannot be bound as a JDBC parameter, so it is
     * taken from the fixed whitelist {@link #TABLES} above and quoted as an identifier.
     */
    private static String describeTable(Connection connection, String table) {
        if (!List.of(TABLES).contains(table)) {
            return "unknown table";
        }
        String sql = "SELECT COUNT(*) FROM `" + table + "`";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) + " rows" : "0 rows";
        } catch (Exception e) {
            return "missing (run DbSetupTool --init)";
        }
    }
}
