package com.skillloop.unit;

import com.skillloop.util.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for BCrypt hashing and the password strength rules (no database needed). */
class PasswordUtilTest {

    @Test
    @DisplayName("A hashed password can be verified with the correct plaintext")
    void hashAndVerify() {
        String hash = PasswordUtil.hash("SkillLoop@123");
        assertTrue(PasswordUtil.verify("SkillLoop@123", hash));
        assertFalse(PasswordUtil.verify("wrong-password", hash));
    }

    @Test
    @DisplayName("Every hash uses a different salt, so identical passwords hash differently")
    void hashesAreSalted() {
        String first = PasswordUtil.hash("SkillLoop@123");
        String second = PasswordUtil.hash("SkillLoop@123");
        assertNotEquals(first, second, "BCrypt must use a random salt per password");
        assertTrue(first.startsWith("$2a$"), "Expected a BCrypt hash, got: " + first);
    }

    @Test
    @DisplayName("Malformed or empty stored hashes never crash the verification")
    void malformedHashIsRejected() {
        assertFalse(PasswordUtil.verify("anything", "not-a-hash"));
        assertFalse(PasswordUtil.verify("anything", null));
        assertFalse(PasswordUtil.verify(null, PasswordUtil.hash("SkillLoop@123")));
    }

    @Test
    @DisplayName("Weak passwords are rejected with a clear reason")
    void strengthRules() {
        assertFalse(PasswordUtil.isStrongEnough("short1"));      // too short
        assertFalse(PasswordUtil.isStrongEnough("nodigitshere")); // no digit
        assertFalse(PasswordUtil.isStrongEnough("12345678"));     // no letter
        assertTrue(PasswordUtil.isStrongEnough("SkillLoop@123"));
        java.util.List<String> problems = PasswordUtil.validateStrength("abc");
        assertEquals(2, problems.size(),
                "A three letter password is too short and has no digit: " + problems);
        assertTrue(problems.stream().anyMatch(problem -> problem.toLowerCase().contains("at least")),
                "The length problem is reported: " + problems);
        assertTrue(problems.stream().anyMatch(problem -> problem.toLowerCase().contains("digit")),
                "The missing digit is reported: " + problems);
        assertEquals(1, PasswordUtil.validateStrength("").size(),
                "An empty password reports exactly one problem");
    }
}
