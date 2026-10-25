package com.skillloop.exception;

/** Raised when a unique constraint would be violated (for example a duplicate email). */
public class DuplicateResourceException extends SkillLoopException {

    private static final long serialVersionUID = 1L;

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
