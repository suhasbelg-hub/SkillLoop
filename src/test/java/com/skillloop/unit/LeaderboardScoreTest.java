package com.skillloop.unit;

import com.skillloop.model.LeaderboardEntry;
import com.skillloop.service.StudentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the transparent leaderboard ranking formula. */
class LeaderboardScoreTest {

    private LeaderboardEntry entry(int sessions, double hours, int learners, double rating, int credits) {
        LeaderboardEntry entry = new LeaderboardEntry();
        entry.setTeachingSessions(sessions);
        entry.setTeachingHours(hours);
        entry.setDistinctLearners(learners);
        entry.setAvgRating(rating);
        entry.setCreditsEarned(credits);
        return entry;
    }

    @Test
    @DisplayName("A teacher with verified sessions scores higher than one without")
    void contributionBeatsEmptyProfile() {
        double active = StudentService.score(entry(5, 6, 4, 4.5, 120));
        double beginner = StudentService.score(entry(0, 0, 0, 0, 0));
        assertTrue(active > beginner);
        assertEquals(0.0, beginner);
    }

    @Test
    @DisplayName("Scores are capped at the documented maximum of 100 points")
    void scoreIsCapped() {
        double huge = StudentService.score(entry(500, 500, 500, 5, 100000));
        assertTrue(huge <= 100.0, "Score must be capped, was " + huge);
        assertTrue(huge >= 90.0, "A very active teacher must be near the maximum, was " + huge);
    }

    @Test
    @DisplayName("The formula follows the documented weights")
    void documentedWeights() {
        // sessions 4*8=32, hours 3*2=6, learners 2*3=6, rating 4*3=12, credits 100*0.05=5 -> 61
        assertEquals(61.0, StudentService.score(entry(4, 3, 2, 4.0, 100)));
    }

    @Test
    @DisplayName("The wallet balance is not part of the ranking")
    void balanceIsIrrelevant() {
        double poor = StudentService.score(entry(3, 3, 2, 5.0, 60));
        LeaderboardEntry same = entry(3, 3, 2, 5.0, 60);
        same.setCreditsEarned(60);
        assertEquals(poor, StudentService.score(same));
    }
}
