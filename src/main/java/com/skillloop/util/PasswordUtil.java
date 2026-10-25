package com.skillloop.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Password hashing and password-strength utilities.
 *
 * <p>Hashing uses BCrypt with a per-password random salt (implemented by the vendored
 * {@link BCrypt} class, ISC licence, originally from jBCrypt by Damien Miller).
 * BCrypt is a slow, salted, adaptive KDF, which is the recommended way to store
 * passwords; plaintext passwords are never persisted anywhere in SkillLoop.</p>
 */
public final class PasswordUtil {

    /** BCrypt work factor (2^12 key setup rounds). Balance between latency and strength. */
    public static final int DEFAULT_LOG_ROUNDS = 12;

    /** BCrypt only uses the first 72 bytes of a password, so longer passwords are rejected. */
    public static final int MAX_PASSWORD_BYTES = 72;

    private static final int MIN_LENGTH = 8;

    private PasswordUtil() {
        // utility class
    }

    /** Returns the BCrypt hash (including the salt) for the given plaintext password. */
    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty.");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(DEFAULT_LOG_ROUNDS));
    }

    /**
     * Verifies a plaintext password against a stored BCrypt hash.
     * Returns false (instead of throwing) when the stored hash is malformed.
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, storedHash);
        } catch (IllegalArgumentException malformedHash) {
            return false;
        }
    }

    /**
     * Password strength rules: at least 8 characters, at least one letter, at least one
     * digit, and a maximum of 72 bytes (BCrypt limit).
     *
     * @return list of human readable problems; empty when the password is acceptable
     */
    public static List<String> validateStrength(String password) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            problems.add("Password is required.");
            return problems;
        }
        if (password.length() < MIN_LENGTH) {
            problems.add("Password must be at least " + MIN_LENGTH + " characters long.");
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            problems.add("Password must not exceed " + MAX_PASSWORD_BYTES + " bytes.");
        }
        if (!password.chars().anyMatch(Character::isLetter)) {
            problems.add("Password must contain at least one letter.");
        }
        if (!password.chars().anyMatch(Character::isDigit)) {
            problems.add("Password must contain at least one digit.");
        }
        return problems;
    }

    /** Convenience: true when {@link #validateStrength(String)} reports no problems. */
    public static boolean isStrongEnough(String password) {
        return validateStrength(password).isEmpty();
    }
}
