package com.skillloop.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Central JDBC entry point.
 *
 * <p>Connections are obtained from {@link DriverManager}. The MySQL Connector/J driver is
 * registered explicitly with {@link Class#forName(String)} so the application still starts
 * (with a clear, actionable error message) when the driver jar is missing.</p>
 *
 * <p>Credentials come from {@link AppConfig} (local git-ignored file or environment
 * variables) and are never written to logs: {@link AppConfig#maskedDescription()} is used
 * for every diagnostic message.</p>
 */
public final class DBConnection {

    private static volatile boolean driverRegistered;
    private static volatile String lastError;

    private DBConnection() {
        // utility class
    }

    /** Loads the MySQL driver once. Throws {@link DatabaseUnavailableException} when missing. */
    private static synchronized void ensureDriver() {
        if (driverRegistered) {
            return;
        }
        String driver = AppConfig.get().dbDriver();
        try {
            Class.forName(driver);
            driverRegistered = true;
        } catch (ClassNotFoundException e) {
            throw new DatabaseUnavailableException(
                    "MySQL JDBC driver '" + driver + "' was not found on the classpath. "
                    + "Maven downloads it automatically (com.mysql:mysql-connector-j); "
                    + "run 'mvn clean package' and start the application from the Maven build.", e);
        }
    }

    /** Opens a new connection. Callers must close it (try-with-resources). */
    public static Connection getConnection() throws SQLException {
        ensureDriver();
        AppConfig config = AppConfig.get();
        return DriverManager.getConnection(config.dbUrl(), config.dbUser(), config.dbPassword());
    }

    /**
     * Opens a connection and validates it with a lightweight query.
     *
     * @return a {@link TestResult} describing server version, database and latency
     */
    public static TestResult testConnection() {
        long startedAt = System.nanoTime();
        try (Connection connection = getConnection()) {
            if (connection == null || connection.isClosed()) {
                return TestResult.failure("The driver returned no open connection.");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                         "SELECT VERSION() AS version, DATABASE() AS db");
                 ResultSet resultSet = statement.executeQuery()) {
                String version = "unknown";
                String database = "unknown";
                if (resultSet.next()) {
                    version = resultSet.getString("version");
                    database = resultSet.getString("db");
                }
                long millis = (System.nanoTime() - startedAt) / 1_000_000L;
                lastError = null;
                return TestResult.success(version, database, millis);
            }
        } catch (SQLException | DatabaseUnavailableException e) {
            lastError = e.getMessage();
            return TestResult.failure(e.getMessage());
        }
    }

    /** Message of the last failed connection attempt, or null when the last attempt succeeded. */
    public static String lastError() {
        return lastError;
    }

    /** True when the driver is present and a connection can be opened. */
    public static boolean isAvailable() {
        return testConnection().isSuccess();
    }

    /** Result of {@link #testConnection()}. */
    public static final class TestResult {
        private final boolean success;
        private final String serverVersion;
        private final String databaseName;
        private final long latencyMillis;
        private final String message;

        private TestResult(boolean success, String serverVersion, String databaseName,
                           long latencyMillis, String message) {
            this.success = success;
            this.serverVersion = serverVersion;
            this.databaseName = databaseName;
            this.latencyMillis = latencyMillis;
            this.message = message;
        }

        static TestResult success(String version, String database, long millis) {
            return new TestResult(true, version, database, millis, "Connected");
        }

        static TestResult failure(String message) {
            return new TestResult(false, null, null, -1, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getServerVersion() {
            return serverVersion;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public long getLatencyMillis() {
            return latencyMillis;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            if (!success) {
                return "FAILED: " + message;
            }
            return "OK: MySQL " + serverVersion + ", database '" + databaseName + "', "
                    + latencyMillis + " ms";
        }
    }

    /** Thrown when the JDBC driver or the database server cannot be reached. */
    public static class DatabaseUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DatabaseUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
