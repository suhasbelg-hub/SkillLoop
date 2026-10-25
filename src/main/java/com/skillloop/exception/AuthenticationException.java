package com.skillloop.exception;

/** Raised when a login attempt fails (unknown email, wrong password, inactive account). */
public class AuthenticationException extends SkillLoopException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
