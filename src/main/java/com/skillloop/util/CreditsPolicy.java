package com.skillloop.util;

import java.util.List;

/**
 * Single source of truth for the credit economy of SkillLoop.
 *
 * <p>Rules implemented here (see docs/PROJECT_REPORT.md, section "Credit policy"):</p>
 * <ul>
 *   <li>A new student starts with <b>0</b> credits.</li>
 *   <li>A <b>one-time starter grant</b> (default 20 credits, configurable, audited in
 *       {@code credit_transactions} with reference {@code STARTER:<studentId>}) lets a new
 *       student book their first session. It can never be claimed twice.</li>
 *   <li>Teaching costs 20 credits per hour: 30 minutes = 10 credits, 60 minutes = 20 credits.</li>
 *   <li>Credits are reserved (held) when a booking is requested and only transferred to the
 *       teacher once both participants confirm the session.</li>
 *   <li>The learner's spendable balance can never become negative (enforced by the service
 *       layer and by a CHECK constraint on {@code wallets}).</li>
 * </ul>
 */
public final class CreditsPolicy {

    /** Credits charged for a full 60 minute session (30 minutes therefore cost 10 credits). */
    public static final int CREDITS_PER_HOUR = 20;

    /** Supported session durations in minutes. */
    public static final List<Integer> ALLOWED_DURATIONS = List.of(30, 45, 60, 90, 120);

    private CreditsPolicy() {
        // utility class
    }

    /** True when the given duration (in minutes) can be booked. */
    public static boolean isAllowedDuration(int durationMinutes) {
        return ALLOWED_DURATIONS.contains(durationMinutes);
    }

    /**
     * Credit cost of a session of the given duration.
     * 30 min = 10, 45 min = 15, 60 min = 20, 90 min = 30, 120 min = 40 credits.
     */
    public static int costFor(int durationMinutes) {
        if (!isAllowedDuration(durationMinutes)) {
            throw new IllegalArgumentException(
                    "Unsupported session duration: " + durationMinutes + " minutes.");
        }
        return (int) Math.round(CREDITS_PER_HOUR * (durationMinutes / 60.0));
    }

    /** One-time starter grant for a newly registered student (0 disables the grant). */
    public static int starterGrantAmount() {
        return AppConfig.get().starterGrantEnabled() ? AppConfig.get().starterGrantAmount() : 0;
    }

    /** Maximum credits an administrator may grant in one manual adjustment. */
    public static int maxAdminAdjustment() {
        return AppConfig.get().maxAdminAdjustment();
    }

    /** Audit reference of the once-in-a-lifetime starter grant for a student. */
    public static String starterReference(long studentId) {
        return "STARTER:" + studentId;
    }

    /** Audit reference of the reservation taken when learner requests session {@code sessionId}. */
    public static String holdReference(long sessionId) {
        return "HOLD:S" + sessionId;
    }

    /** Audit reference of the reservation released for session {@code sessionId}. */
    public static String releaseReference(long sessionId) {
        return "RELEASE:S" + sessionId;
    }

    /** Audit reference of the learner's payment for session {@code sessionId}. */
    public static String spendReference(long sessionId) {
        return "SPEND:S" + sessionId;
    }

    /** Audit reference of the teacher's earning for session {@code sessionId}. */
    public static String earnReference(long sessionId) {
        return "EARN:S" + sessionId;
    }

    /** Audit reference of an administrator refund for session {@code sessionId}. */
    public static String refundReference(long sessionId, long studentId) {
        return "REFUND:S" + sessionId + ":U" + studentId;
    }

    /** Audit reference of a group session charge. */
    public static String groupSpendReference(long groupId, long studentId) {
        return "GSPEND:G" + groupId + ":U" + studentId;
    }

    /** Audit reference of a group session earning. */
    public static String groupEarnReference(long groupId, long studentId) {
        return "GEARN:G" + groupId + ":U" + studentId;
    }
}
