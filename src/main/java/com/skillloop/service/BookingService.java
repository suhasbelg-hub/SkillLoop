package com.skillloop.service;

import com.skillloop.dao.AvailabilityDAO;
import com.skillloop.dao.SessionDAO;
import com.skillloop.dao.SkillDAO;
import com.skillloop.dao.StudentDAO;
import com.skillloop.exception.AuthorizationException;
import com.skillloop.exception.BookingException;
import com.skillloop.exception.NotFoundException;
import com.skillloop.exception.ValidationException;
import com.skillloop.model.AvailabilitySlot;
import com.skillloop.model.LearningSession;
import com.skillloop.model.NotificationType;
import com.skillloop.model.SessionStatus;
import com.skillloop.model.SkillType;
import com.skillloop.model.SlotStatus;
import com.skillloop.model.Student;
import com.skillloop.util.CreditsPolicy;
import com.skillloop.util.DBConnection;
import com.skillloop.util.InputValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Availability handling and the complete booking life cycle.
 *
 * <p>Supported states: REQUESTED -> ACCEPTED | REJECTED | CANCELLED -> COMPLETED, plus DISPUTED
 * which an administrator has to resolve. Every state change is validated in the service layer,
 * never in the UI.</p>
 */
public class BookingService {

    /** A booking must start at least this many minutes in the future. */
    public static final int MIN_LEAD_MINUTES = 5;

    private final SessionDAO sessionDAO = new SessionDAO();
    private final StudentDAO studentDAO = new StudentDAO();
    private final SkillDAO skillDAO = new SkillDAO();
    private final AvailabilityDAO availabilityDAO = new AvailabilityDAO();
    private final CreditService creditService = new CreditService();
    private final NotificationService notificationService = new NotificationService();

    // ------------------------------------------------------------------ availability

    public List<AvailabilitySlot> myAvailability(long studentId) {
        return availabilityDAO.listForStudent(studentId, true);
    }

    public AvailabilitySlot addAvailability(long studentId, LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new ValidationException("Please provide a start and an end time.");
        }
        if (!end.isAfter(start)) {
            throw new ValidationException("The end time must be after the start time.");
        }
        if (start.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new ValidationException("The slot must start in the future.");
        }
        AvailabilitySlot slot = new AvailabilitySlot(studentId, start, end);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (availabilityDAO.hasOverlap(connection, studentId, start, end)) {
                    connection.rollback();
                    throw new ValidationException("This slot overlaps a slot you already published.");
                }
                slot.setSlotId(availabilityDAO.insert(connection, slot));
                connection.commit();
                return slot;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the availability slot: " + e.getMessage(), e);
        }
    }

    public void removeAvailability(long studentId, long slotId) {
        AvailabilitySlot slot = availabilityDAO.findById(slotId)
                .orElseThrow(() -> new NotFoundException("Slot #" + slotId + " does not exist."));
        if (slot.getStudentId() != studentId) {
            throw new AuthorizationException("You can only remove your own availability slots.");
        }
        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new BookingException("This slot is already booked and can no longer be removed.");
        }
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                availabilityDAO.updateStatus(connection, slotId, SlotStatus.BLOCKED, null);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not remove the slot: " + e.getMessage(), e);
        }
    }

    public List<AvailabilitySlot> bookableSlotsOf(long teacherId) {
        return availabilityDAO.listBookableForTeacher(teacherId);
    }

    // ------------------------------------------------------------------ booking

    /**
     * Creates a booking request and reserves the credits of the learner.
     *
     * <p>Rules enforced here: no self-booking, no past dates, no overlapping confirmed
     * sessions for either participant, teacher must actively offer the skill, the slot must
     * be free and long enough, and the learner must be able to pay.</p>
     */
    public LearningSession requestBooking(long learnerId, long teacherId, long skillId, Long slotId,
                                          LocalDateTime start, int durationMinutes, String note) {
        if (learnerId == teacherId) {
            throw new BookingException("You cannot book a session with yourself.");
        }
        InputValidator.validateDuration(durationMinutes);
        if (start == null) {
            throw new ValidationException("Please choose a start date and time.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (start.isBefore(now.plusMinutes(MIN_LEAD_MINUTES))) {
            throw new ValidationException("A session must start at least " + MIN_LEAD_MINUTES
                    + " minutes in the future.");
        }
        LocalDateTime end = start.plusMinutes(durationMinutes);
        int cost = CreditsPolicy.costFor(durationMinutes);

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // --- teacher and skill ---------------------------------------------------
                Student teacher = studentDAO.findById(connection, teacherId)
                        .orElseThrow(() -> new NotFoundException("Teacher #" + teacherId + " does not exist."));
                if (!teacher.isActive()) {
                    throw new BookingException("This teacher's account is not active.");
                }
                boolean offers = skillDAO.listByStudent(teacherId, SkillType.OFFER).stream()
                        .anyMatch(l -> l.isActive() && l.getSkillId() == skillId);
                if (!offers) {
                    throw new BookingException(teacher.getFullName() + " does not offer this skill.");
                }

                // --- slot -----------------------------------------------------------------
                if (slotId != null) {
                    AvailabilitySlot slot = availabilityDAO.findByIdForUpdate(connection, slotId)
                            .orElseThrow(() -> new NotFoundException("Slot #" + slotId + " does not exist."));
                    if (slot.getStudentId() != teacherId) {
                        throw new BookingException("This slot does not belong to the selected teacher.");
                    }
                    if (slot.getStatus() != SlotStatus.AVAILABLE) {
                        throw new BookingException("This time slot is no longer available.");
                    }
                    if (!slot.covers(start, end)) {
                        throw new BookingException("The session does not fit inside the teacher's slot ("
                                + slot + ").");
                    }
                    if (sessionDAO.hasOpenRequestForSlot(connection, slotId)) {
                        throw new BookingException("There is already an open booking for this slot.");
                    }
                }

                // --- overlaps --------------------------------------------------------------
                if (sessionDAO.hasOverlap(connection, teacherId, start, end, null)) {
                    throw new BookingException("The teacher already has a session in this time window.");
                }
                if (sessionDAO.hasOverlap(connection, learnerId, start, end, null)) {
                    throw new BookingException("You already have a session in this time window.");
                }

                // --- create the session ------------------------------------------------------
                LearningSession session = new LearningSession();
                session.setTeacherId(teacherId);
                session.setLearnerId(learnerId);
                session.setSkillId(skillId);
                session.setSlotId(slotId);
                session.setStartTime(start);
                session.setEndTime(end);
                session.setDurationMinutes(durationMinutes);
                session.setCreditCost(cost);
                session.setStatus(SessionStatus.REQUESTED);
                session.setLearnerNote(InputValidator.optionalText(note, "Note", 500));
                session.setSessionId(sessionDAO.insert(connection, session));

                // --- reserve the credits ------------------------------------------------------
                creditService.placeHold(connection, learnerId, session.getSessionId(), cost);

                // --- notify the teacher ---------------------------------------------------------
                Student learner = studentDAO.findById(connection, learnerId).orElseThrow();
                notificationService.notify(connection, teacherId, "New booking request",
                        learner.getFullName() + " requested a " + durationMinutes + "-minute session ("
                                + cost + " credits).", NotificationType.BOOKING_REQUEST);

                connection.commit();
                return sessionDAO.findById(session.getSessionId())
                        .orElseThrow(() -> new NotFoundException("The booking was not created."));
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Booking failed and was rolled back: " + e.getMessage(), e);
        }
    }

    /** Teacher accepts a REQUESTED booking; the slot becomes BOOKED. */
    public LearningSession accept(long sessionId, long actorId) {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LearningSession session = sessionDAO.findByIdForUpdate(connection, sessionId)
                        .orElseThrow(() -> new NotFoundException("Session #" + sessionId + " does not exist."));
                requireParticipant(session, actorId, "Only the teacher can accept this booking.");
                if (session.getTeacherId() != actorId) {
                    throw new AuthorizationException("Only the teacher can accept this booking.");
                }
                if (session.getStatus() != SessionStatus.REQUESTED) {
                    throw new BookingException("Only requested sessions can be accepted (current state: "
                            + session.getStatus() + ").");
                }
                session.setStatus(SessionStatus.ACCEPTED);
                sessionDAO.update(connection, session);
                if (session.getSlotId() != null) {
                    availabilityDAO.updateStatus(connection, session.getSlotId(), SlotStatus.BOOKED, sessionId);
                }
                notificationService.notify(connection, session.getLearnerId(), "Booking accepted",
                        session.getTeacherName() + " accepted your booking for "
                                + session.getSkillName() + ".", NotificationType.BOOKING_ACCEPTED);
                connection.commit();
                return session;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not accept the booking: " + e.getMessage(), e);
        }
    }

    /** Teacher rejects a REQUESTED booking; the reserved credits are released. */
    public LearningSession reject(long sessionId, long actorId, String reason) {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LearningSession session = sessionDAO.findByIdForUpdate(connection, sessionId)
                        .orElseThrow(() -> new NotFoundException("Session #" + sessionId + " does not exist."));
                if (session.getTeacherId() != actorId) {
                    throw new AuthorizationException("Only the teacher can reject this booking.");
                }
                if (session.getStatus() != SessionStatus.REQUESTED) {
                    throw new BookingException("Only requested sessions can be rejected.");
                }
                session.setStatus(SessionStatus.REJECTED);
                session.setResolutionNote(InputValidator.optionalText(reason, "Reason", 500));
                sessionDAO.update(connection, session);
                creditService.releaseHold(connection, session.getLearnerId(), sessionId, session.getCreditCost());
                availabilityDAO.releaseSlot(connection, session.getSlotId());
                notificationService.notify(connection, session.getLearnerId(), "Booking rejected",
                        session.getTeacherName() + " rejected your booking. Your reserved credits were released.",
                        NotificationType.BOOKING_REJECTED);
                connection.commit();
                return session;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not reject the booking: " + e.getMessage(), e);
        }
    }

    /** Either participant cancels before completion; the reservation is released. */
    public LearningSession cancel(long sessionId, long actorId, String reason) {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LearningSession session = sessionDAO.findByIdForUpdate(connection, sessionId)
                        .orElseThrow(() -> new NotFoundException("Session #" + sessionId + " does not exist."));
                requireParticipant(session, actorId, "Only the teacher or the learner can cancel this booking.");
                if (session.getStatus() != SessionStatus.REQUESTED
                        && session.getStatus() != SessionStatus.ACCEPTED) {
                    throw new BookingException("Only requested or accepted sessions can be cancelled.");
                }
                session.setStatus(SessionStatus.CANCELLED);
                session.setResolutionNote(InputValidator.optionalText(reason, "Reason", 500));
                sessionDAO.update(connection, session);
                creditService.releaseHold(connection, session.getLearnerId(), sessionId, session.getCreditCost());
                availabilityDAO.releaseSlot(connection, session.getSlotId());
                long otherParty = session.getTeacherId() == actorId ? session.getLearnerId() : session.getTeacherId();
                notificationService.notify(connection, otherParty, "Session cancelled",
                        "Session #" + sessionId + " was cancelled. Reserved credits were released.",
                        NotificationType.SESSION_CANCELLED);
                connection.commit();
                return session;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not cancel the booking: " + e.getMessage(), e);
        }
    }

    /**
     * Teacher or learner confirms that the session took place. As soon as both confirmed, the
     * credit transfer runs (in its own transaction, so a failure never loses the confirmation).
     */
    public LearningSession confirmCompletion(long sessionId, long actorId) {
        boolean bothConfirmed;
        LearningSession session;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                session = sessionDAO.findByIdForUpdate(connection, sessionId)
                        .orElseThrow(() -> new NotFoundException("Session #" + sessionId + " does not exist."));
                requireParticipant(session, actorId, "Only the teacher or the learner can confirm this session.");
                if (session.getStatus() != SessionStatus.ACCEPTED) {
                    throw new BookingException("Only accepted sessions can be confirmed (current state: "
                            + session.getStatus() + ").");
                }
                if (session.getStartTime().isAfter(LocalDateTime.now())) {
                    throw new BookingException("A session can only be confirmed after its start time.");
                }
                if (session.getTeacherId() == actorId && session.isTeacherConfirmation()) {
                    throw new BookingException("You have already confirmed this session.");
                }
                if (session.getLearnerId() == actorId && session.isLearnerConfirmation()) {
                    throw new BookingException("You have already confirmed this session.");
                }
                if (session.getTeacherId() == actorId) {
                    session.setTeacherConfirmation(true);
                } else {
                    session.setLearnerConfirmation(true);
                }
                sessionDAO.update(connection, session);
                bothConfirmed = session.isFullyConfirmed();
                long otherParty = session.getTeacherId() == actorId ? session.getLearnerId() : session.getTeacherId();
                notificationService.notify(connection, otherParty, "Completion confirmed",
                        "Your partner confirmed completion of session #" + sessionId + ".",
                        NotificationType.SESSION_COMPLETED);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not confirm the session: " + e.getMessage(), e);
        }

        if (bothConfirmed) {
            // Idempotent: a second call is a no-op thanks to the unique ledger references.
            creditService.settleSession(sessionId);
            session = sessionDAO.findById(sessionId).orElse(session);
        }
        return session;
    }

    /** A participant reports a problem; an administrator has to resolve the dispute. */
    public LearningSession raiseDispute(long sessionId, long actorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Please describe the problem before raising a dispute.");
        }
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LearningSession session = sessionDAO.findByIdForUpdate(connection, sessionId)
                        .orElseThrow(() -> new NotFoundException("Session #" + sessionId + " does not exist."));
                requireParticipant(session, actorId, "Only the teacher or the learner can raise a dispute.");
                if (session.getStatus() != SessionStatus.ACCEPTED) {
                    throw new BookingException("Only accepted sessions can be disputed.");
                }
                session.setStatus(SessionStatus.DISPUTED);
                session.setResolutionNote(InputValidator.optionalText(reason, "Reason", 500));
                sessionDAO.update(connection, session);
                long otherParty = session.getTeacherId() == actorId ? session.getLearnerId() : session.getTeacherId();
                notificationService.notify(connection, otherParty, "Dispute raised",
                        "A dispute was raised for session #" + sessionId
                                + ". The reserved credits stay on hold until an administrator decides.",
                        NotificationType.DISPUTE_RAISED);
                connection.commit();
                return session;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not raise the dispute: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ queries

    public List<LearningSession> sessionsFor(long studentId, String role, SessionStatus status, int limit) {
        return sessionDAO.listForStudent(studentId, role, status, limit);
    }

    public List<LearningSession> upcoming(long studentId, int limit) {
        return sessionDAO.listUpcoming(studentId, limit);
    }

    public Optional<LearningSession> find(long sessionId) {
        return sessionDAO.findById(sessionId);
    }

    /** Bookings that wait for a decision of the given student. */
    public List<LearningSession> pendingForMe(long studentId) {
        return sessionDAO.listForStudent(studentId, "ALL", SessionStatus.REQUESTED, 100).stream()
                .filter(s -> s.getTeacherId() == studentId || s.getLearnerId() == studentId)
                .filter(s -> s.getStatus() == SessionStatus.REQUESTED)
                .toList();
    }

    /** Sessions that are finished but still need the confirmation of the given student. */
    public List<LearningSession> awaitingMyConfirmation(long studentId) {
        return sessionDAO.listForStudent(studentId, "ALL", SessionStatus.ACCEPTED, 100).stream()
                .filter(s -> s.getStartTime() != null && s.getStartTime().isBefore(LocalDateTime.now()))
                .filter(s -> (s.getTeacherId() == studentId && !s.isTeacherConfirmation())
                        || (s.getLearnerId() == studentId && !s.isLearnerConfirmation()))
                .toList();
    }

    private void requireParticipant(LearningSession session, long actorId, String message) {
        if (session.getTeacherId() != actorId && session.getLearnerId() != actorId) {
            throw new AuthorizationException(message);
        }
    }
}
