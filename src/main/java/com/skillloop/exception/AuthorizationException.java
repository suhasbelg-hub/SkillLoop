package com.skillloop.exception;

/**
 * Raised when a student tries to perform an action they are not allowed to perform
 * (for example a non-administrator calling an administrator service, or a student
 * editing somebody else's skill listing).
 */
public class AuthorizationException extends SkillLoopException {

    private static final long serialVersionUID = 1L;

    public AuthorizationException(String message) {
        super(message);
    }
}
