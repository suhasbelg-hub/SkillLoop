package com.skillloop.exception;

/**
 * Wraps a {@link java.sql.SQLException} that could not be handled by the DAO layer.
 * The original SQLException is always preserved as the cause.
 */
public class DatabaseException extends SkillLoopException {

    private static final long serialVersionUID = 1L;

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
