package com.skillloop.exception;

/** Raised when a requested row (student, session, skill, wallet, ...) does not exist. */
public class NotFoundException extends SkillLoopException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
