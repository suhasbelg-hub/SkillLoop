package com.skillloop.exception;

/**
 * Base class for all application errors raised by the service and DAO layers.
 *
 * <p>It is an unchecked exception so that DAO and service signatures stay readable; every
 * exception is caught at the UI boundary and shown to the user as a friendly message.</p>
 */
public class SkillLoopException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SkillLoopException(String message) {
        super(message);
    }

    public SkillLoopException(String message, Throwable cause) {
        super(message, cause);
    }
}
