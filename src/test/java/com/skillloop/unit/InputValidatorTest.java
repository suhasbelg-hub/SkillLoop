package com.skillloop.unit;

import com.skillloop.exception.ValidationException;
import com.skillloop.util.InputValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the central input validation. */
class InputValidatorTest {

    @Test
    @DisplayName("Valid college emails are accepted and normalised to lower case")
    void validEmail() {
        assertEquals("rahul@college.edu", InputValidator.validateEmail("  Rahul@College.edu "));
    }

    @Test
    @DisplayName("Invalid emails are rejected")
    void invalidEmail() {
        assertThrows(ValidationException.class, () -> InputValidator.validateEmail("rahul.college.edu"));
        assertThrows(ValidationException.class, () -> InputValidator.validateEmail("rahul@"));
        assertThrows(ValidationException.class, () -> InputValidator.validateEmail(""));
        assertThrows(ValidationException.class, () -> InputValidator.validateEmail(null));
    }

    @Test
    @DisplayName("Name, department and semester rules are enforced")
    void profileRules() {
        assertEquals("Rahul Sharma", InputValidator.validateFullName("Rahul Sharma"));
        assertThrows(ValidationException.class, () -> InputValidator.validateFullName("A"));
        assertThrows(ValidationException.class, () -> InputValidator.validateDepartment(""));
        assertThrows(ValidationException.class, () -> InputValidator.validateSemester(0));
        assertThrows(ValidationException.class, () -> InputValidator.validateSemester(13));
        assertEquals(8, InputValidator.validateSemester(8));
    }

    @Test
    @DisplayName("Only the supported session durations may be booked")
    void durationRules() {
        assertEquals(30, InputValidator.validateDuration(30));
        assertEquals(120, InputValidator.validateDuration(120));
        assertThrows(ValidationException.class, () -> InputValidator.validateDuration(25));
    }

    @Test
    @DisplayName("Ratings must be between 1 and 5")
    void ratingRules() {
        assertEquals(5, InputValidator.validateRating(5));
        assertThrows(ValidationException.class, () -> InputValidator.validateRating(0));
        assertThrows(ValidationException.class, () -> InputValidator.validateRating(6));
    }

    @Test
    @DisplayName("URLs are validated, empty URLs become null")
    void urlRules() {
        assertEquals("https://example.com/notes", InputValidator.validateUrl("https://example.com/notes"));
        assertEquals(null, InputValidator.validateUrl("   "));
        assertThrows(ValidationException.class, () -> InputValidator.validateUrl("not a url"));
    }

    @Test
    @DisplayName("Text fields reject the angle brackets that could break the UI")
    void htmlCharactersAreRejected() {
        assertThrows(ValidationException.class, () -> InputValidator.requireText("<script>", "Title", 100));
    }

    @Test
    @DisplayName("The registration form collects every problem at once")
    void registrationProblems() {
        var problems = InputValidator.registrationProblems("", "bad-email", "123", "456", "", 99);
        assertTrue(problems.size() >= 5, "Expected at least five problems, got " + problems);
    }
}
