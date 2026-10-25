package com.skillloop.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Application configuration.
 *
 * <p>Configuration is resolved in the following order (first match wins):</p>
 * <ol>
 *   <li>JVM system properties (<code>-Dskillloop.db.url=...</code>)</li>
 *   <li>Environment variables <code>SKILLLOOP_DB_URL</code>, <code>SKILLLOOP_DB_USER</code>,
 *       <code>SKILLLOOP_DB_PASSWORD</code></li>
 *   <li>External file <code>./config/application.properties</code> (git-ignored, for secrets)</li>
 *   <li>Classpath resource <code>/config/application.properties</code></li>
 * </ol>
 *
 * <p>No credential is ever hard-coded in the source code and no credential is ever logged:
 * {@link #maskedDescription()} returns a connection description with the password removed.</p>
 */
public final class AppConfig {

    public static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/skillloop?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    public static final String DEFAULT_USER = "skillloop_app";
    public static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";

    /** Path of the local, git-ignored configuration file. */
    public static final Path LOCAL_CONFIG = Path.of("config", "application.properties");

    private final Properties values = new Properties();

    private AppConfig() {
        reload();
    }

    private static final class Holder {
        static final AppConfig INSTANCE = new AppConfig();
    }

    public static AppConfig get() {
        return Holder.INSTANCE;
    }

    /** Re-reads configuration from disk / environment (used by tests and the setup tool). */
    public final void reload() {
        values.clear();
        loadClasspathDefaults();
        loadExternalFile();
        loadEnvironment();
        loadSystemProperties();
    }

    private void loadClasspathDefaults() {
        try (InputStream in = AppConfig.class.getResourceAsStream("/config/application.properties")) {
            if (in != null) {
                values.load(in);
            }
        } catch (IOException e) {
            // fall through: defaults below will be used
        }
    }

    private void loadExternalFile() {
        if (Files.isRegularFile(LOCAL_CONFIG)) {
            try (InputStream in = Files.newInputStream(LOCAL_CONFIG)) {
                values.load(in);
            } catch (IOException e) {
                System.err.println("[SkillLoop] Could not read " + LOCAL_CONFIG + ": " + e.getMessage());
            }
        }
    }

    private void loadEnvironment() {
        putFromEnv("skillloop.db.url", "SKILLLOOP_DB_URL");
        putFromEnv("skillloop.db.user", "SKILLLOOP_DB_USER");
        putFromEnv("skillloop.db.password", "SKILLLOOP_DB_PASSWORD");
        putFromEnv("skillloop.db.driver", "SKILLLOOP_DB_DRIVER");
    }

    private void putFromEnv(String key, String envName) {
        String value = System.getenv(envName);
        if (value != null && !value.isBlank()) {
            values.setProperty(key, value);
        }
    }

    private void loadSystemProperties() {
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("skillloop.")) {
                values.setProperty(name, System.getProperty(name));
            }
        }
    }

    public String get(String key, String fallback) {
        String value = values.getProperty(key);
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    public int getInt(String key, int fallback) {
        String value = get(key, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean getBoolean(String key, boolean fallback) {
        String value = get(key, null);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    /** Test hook: override a value at runtime (never used to store credentials in code). */
    public void set(String key, String value) {
        values.setProperty(key, value);
    }

    public String dbUrl() {
        return get("skillloop.db.url", DEFAULT_URL);
    }

    public String dbUser() {
        return get("skillloop.db.user", DEFAULT_USER);
    }

    public String dbPassword() {
        return get("skillloop.db.password", "");
    }

    public String dbDriver() {
        return get("skillloop.db.driver", JDBC_DRIVER);
    }

    /** Credits granted once to a new student so that the very first booking is possible. */
    public int starterGrantAmount() {
        return getInt("skillloop.credits.starter.amount", 20);
    }

    public boolean starterGrantEnabled() {
        return getBoolean("skillloop.credits.starter.enabled", true);
    }

    /** Credits charged per full hour of teaching (30 minutes = 10 credits). */
    public int creditsPerHour() {
        return getInt("skillloop.credits.per.hour", 20);
    }

    /** Maximum number of credits an administrator may add in a single adjustment. */
    public int maxAdminAdjustment() {
        return getInt("skillloop.credits.admin.max", 100);
    }

    /** Connection URL without any credential, safe for logs and dialogs. */
    public String maskedDescription() {
        String url = dbUrl();
        int at = url.indexOf("password=");
        if (at >= 0) {
            int end = url.indexOf('&', at);
            url = (end < 0 ? url.substring(0, at) : url.substring(0, at) + url.substring(end)) + "password=***";
        }
        return url + " (user=" + dbUser() + ", password=***)";
    }
}
