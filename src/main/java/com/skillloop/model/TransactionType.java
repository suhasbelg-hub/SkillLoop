package com.skillloop.model;

/** Type of a credit ledger entry (see docs/DATABASE_DESIGN.md). */
public enum TransactionType {
    /** One-time welcome grant given to a new student (audited, never repeatable). */
    STARTER_GRANT,
    /** Credits reserved when a booking is requested (balance -> held). */
    HOLD,
    /** Reservation given back (held -> balance) after rejection or cancellation. */
    RELEASE,
    /** Final learner payment when a session is completed. */
    SPEND,
    /** Teacher reward for a completed and verified session. */
    EARN,
    /** Refund ordered by an administrator (dispute resolution). */
    REFUND,
    /** Manual correction by an administrator. */
    ADMIN_ADJUSTMENT,
    /** Teacher reward earned from a completed group session. */
    GROUP_EARNING;

    public static TransactionType fromDb(String value) {
        return value == null ? SPEND : TransactionType.valueOf(value.toUpperCase());
    }
}
