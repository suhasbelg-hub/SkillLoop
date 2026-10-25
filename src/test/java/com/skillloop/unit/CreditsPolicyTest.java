package com.skillloop.unit;

import com.skillloop.util.CreditsPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the documented credit economy (30 min = 10, 60 min = 20 credits). */
class CreditsPolicyTest {

    @Test
    @DisplayName("A 30 minute session costs 10 credits and a 60 minute session costs 20")
    void documentedRates() {
        assertEquals(10, CreditsPolicy.costFor(30));
        assertEquals(20, CreditsPolicy.costFor(60));
    }

    @Test
    @DisplayName("All supported durations have a proportional price")
    void allDurations() {
        assertEquals(15, CreditsPolicy.costFor(45));
        assertEquals(30, CreditsPolicy.costFor(90));
        assertEquals(40, CreditsPolicy.costFor(120));
    }

    @Test
    @DisplayName("Unsupported durations are rejected")
    void unsupportedDuration() {
        assertFalse(CreditsPolicy.isAllowedDuration(10));
        assertThrows(IllegalArgumentException.class, () -> CreditsPolicy.costFor(10));
    }

    @Test
    @DisplayName("Ledger references are unique per session and per purpose")
    void referencesAreUnique() {
        assertEquals("HOLD:S5", CreditsPolicy.holdReference(5));
        assertEquals("RELEASE:S5", CreditsPolicy.releaseReference(5));
        assertEquals("SPEND:S5", CreditsPolicy.spendReference(5));
        assertEquals("EARN:S5", CreditsPolicy.earnReference(5));
        assertEquals("STARTER:9", CreditsPolicy.starterReference(9));
        assertEquals("GSPEND:G1:U2", CreditsPolicy.groupSpendReference(1, 2));
        assertEquals("GEARN:G1:U2", CreditsPolicy.groupEarnReference(1, 2));
    }

    @Test
    @DisplayName("The starter grant is a bounded, one-time, configurable amount")
    void starterGrant() {
        assertTrue(CreditsPolicy.starterGrantAmount() >= 0);
        assertTrue(CreditsPolicy.starterGrantAmount() <= 50, "The grant must stay small and auditable");
    }
}
