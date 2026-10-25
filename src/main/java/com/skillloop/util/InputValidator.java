package com.skillloop.util;

import com.skillloop.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Central input validation. Every value that reaches the service layer is validated here,
 * which keeps the UI free of business rules and protects the database from bad data.
 */
public final class InputValidator {

    /** Practical college e-mail pattern; deliberately permissive but not trivial. */
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern NAME = Pattern.compile("^[\\p{L} .'-]{2,120}$");
    private static final Pattern SAFE_TEXT = Pattern.compile("^[^<>]*$");

    private InputValidator() {
        // utility class
    }

    // ---------------------------------------------------------------- text helpers

    public static String requireText(String value, String fieldName, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException(fieldName + " is required.");
        }
        if (trimmed.length() > maxLength) {
            throw new ValidationException(fieldName + " must not exceed " + maxLength + " characters.");
        }
        if (!SAFE_TEXT.matcher(trimmed).matches()) {
            throw new ValidationException(fieldName + " must not contain the characters < or >.");
        }
        return trimmed;
    }

    public static String optionalText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, fieldName, maxLength);
    }

    // ---------------------------------------------------------------- registration

    public static String validateEmail(String email) {
        String trimmed = email == null ? "" : email.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            throw new ValidationException("College email is required.");
        }
        if (trimmed.length() > 190) {
            throw new ValidationException("College email is too long.");
        }
        if (!EMAIL.matcher(trimmed).matches()) {
            throw new ValidationException("Please enter a valid email address, for example rahul@college.edu.");
        }
        return trimmed;
    }

    public static String validateFullName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() < 2) {
            throw new ValidationException("Full name must contain at least 2 characters.");
        }
        if (trimmed.length() > 120) {
            throw new ValidationException("Full name must not exceed 120 characters.");
        }
        if (!NAME.matcher(trimmed).matches()) {
            throw new ValidationException("Full name may only contain letters, spaces, dots, apostrophes and hyphens.");
        }
        return trimmed;
    }

    public static String validateDepartment(String department) {
        return requireText(department, "Department", 100);
    }

    public static int validateSemester(int semester) {
        if (semester < 1 || semester > 12) {
            throw new ValidationException("Semester must be between 1 and 12.");
        }
        return semester;
    }

    public static String validateBio(String bio) {
        return optionalText(bio, "Bio", 500);
    }

    public static void validatePassword(String password, String confirmation) {
        List<String> problems = PasswordUtil.validateStrength(password);
        if (!problems.isEmpty()) {
            throw new ValidationException(String.join(" ", problems));
        }
        if (confirmation == null || !confirmation.equals(password)) {
            throw new ValidationException("Password and confirmation do not match.");
        }
    }

    // ---------------------------------------------------------------- skills

    public static String validateSkillName(String name) {
        String trimmed = requireText(name, "Skill name", 100);
        if (trimmed.length() < 2) {
            throw new ValidationException("Skill name must contain at least 2 characters.");
        }
        return trimmed;
    }

    public static String validateCategory(String category) {
        return requireText(category, "Category", 60);
    }

    // ---------------------------------------------------------------- sessions

    public static int validateDuration(int minutes) {
        if (!CreditsPolicy.isAllowedDuration(minutes)) {
            throw new ValidationException("Duration must be one of " + CreditsPolicy.ALLOWED_DURATIONS + " minutes.");
        }
        return minutes;
    }

    public static int validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new ValidationException("Rating must be between 1 and 5 stars.");
        }
        return rating;
    }

    public static String validateUrl(String url) {
        String trimmed = url == null ? "" : url.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 500) {
            throw new ValidationException("URL must not exceed 500 characters.");
        }
        if (!trimmed.matches("^(https?://|www\\.)[A-Za-z0-9./?=_&%:+-]+$")) {
            throw new ValidationException("Please enter a valid URL starting with http://, https:// or www.");
        }
        return trimmed;
    }

    public static int validatePositiveInt(String value, String fieldName, int min, int max) {
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(fieldName + " must be a whole number.");
        }
        if (parsed < min || parsed > max) {
            throw new ValidationException(fieldName + " must be between " + min + " and " + max + ".");
        }
        return parsed;
    }

    /** Collects every problem of a registration form instead of failing on the first one. */
    public static List<String> registrationProblems(String name, String email, String password,
                                                    String confirmation, String department, int semester) {
        List<String> problems = new ArrayList<>();
        try {
            validateFullName(name);
        } catch (ValidationException e) {
            problems.add(e.getMessage());
        }
        try {
            validateEmail(email);
        } catch (ValidationException e) {
            problems.add(e.getMessage());
        }
        problems.addAll(PasswordUtil.validateStrength(password));
        if (confirmation == null || !confirmation.equals(password)) {
            problems.add("Password and confirmation do not match.");
        }
        try {
            validateDepartment(department);
        } catch (ValidationException e) {
            problems.add(e.getMessage());
        }
        try {
            validateSemester(semester);
        } catch (ValidationException e) {
            problems.add(e.getMessage());
        }
        return problems;
    }
}
