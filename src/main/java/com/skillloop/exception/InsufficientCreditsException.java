package com.skillloop.exception;

/** Raised when a learner does not have enough spendable credits for a booking or payment. */
public class InsufficientCreditsException extends SkillLoopException {

    private static final long serialVersionUID = 1L;

    private final int required;
    private final int available;

    public InsufficientCreditsException(int required, int available) {
        super("Not enough credits: this booking requires " + required
                + " credits but only " + available + " are available.");
        this.required = required;
        this.available = available;
    }

    public int getRequired() {
        return required;
    }

    public int getAvailable() {
        return available;
    }
}
