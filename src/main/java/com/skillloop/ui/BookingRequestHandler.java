package com.skillloop.ui;

/** Callback used by the recommendation screens to open the booking form for a teacher. */
@FunctionalInterface
public interface BookingRequestHandler {
    /** Opens the booking screen for the given teacher and skill. */
    void openBooking(long teacherId, long skillId);
}
