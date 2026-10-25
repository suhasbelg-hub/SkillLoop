package com.skillloop.exception;

/** Raised when user input fails validation (registration form, booking form, and so on). */
public class ValidationException extends SkillLoopException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
