package com.skillloop.exception;

/** Raised when a booking rule is violated (self-booking, overlap, invalid date, bad state). */
public class BookingException extends SkillLoopException {

    private static final long serialVersionUID = 1L;

    public BookingException(String message) {
        super(message);
    }
}
