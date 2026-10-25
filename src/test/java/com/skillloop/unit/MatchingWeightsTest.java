package com.skillloop.unit;

import com.skillloop.service.MatchingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the documented matching weights. */
class MatchingWeightsTest {

    @Test
    @DisplayName("The documented maximum score is 100 points")
    void maximumScore() {
        int maximum = MatchingService.MAX_RECIPROCAL
                + 20 /* advanced proficiency */
                + MatchingService.MAX_AVAILABILITY
                + MatchingService.MAX_RATING
                + MatchingService.MAX_EXPERIENCE
                + MatchingService.MAX_INTEREST;
        assertEquals(100, maximum, "The matching weights must add up to 100");
    }

    @Test
    @DisplayName("Proficiency weights follow the documented order")
    void proficiencyWeights() {
        assertTrue(com.skillloop.model.Proficiency.ADVANCED.weight()
                > com.skillloop.model.Proficiency.INTERMEDIATE.weight());
        assertTrue(com.skillloop.model.Proficiency.INTERMEDIATE.weight()
                > com.skillloop.model.Proficiency.BEGINNER.weight());
        assertEquals(20, com.skillloop.model.Proficiency.ADVANCED.weight());
        assertEquals(14, com.skillloop.model.Proficiency.INTERMEDIATE.weight());
        assertEquals(8, com.skillloop.model.Proficiency.BEGINNER.weight());
    }

    @Test
    @DisplayName("Reciprocity is the strongest single signal")
    void reciprocityIsStrongest() {
        assertTrue(MatchingService.MAX_RECIPROCAL >= MatchingService.MAX_RATING);
        assertTrue(MatchingService.MAX_RECIPROCAL >= MatchingService.MAX_AVAILABILITY);
        assertEquals(7.5, MatchingService.NEUTRAL_RATING_POINTS,
                "Unrated teachers get a neutral rating score instead of zero");
    }
}
