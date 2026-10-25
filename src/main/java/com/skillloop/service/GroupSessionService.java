package com.skillloop.service;

import com.skillloop.dao.GroupSessionDAO;
import com.skillloop.dao.SkillDAO;
import com.skillloop.exception.BookingException;
import com.skillloop.exception.NotFoundException;
import com.skillloop.exception.ValidationException;
import com.skillloop.model.GroupParticipant;
import com.skillloop.model.GroupSession;
import com.skillloop.model.GroupStatus;
import com.skillloop.model.NotificationType;
import com.skillloop.model.ParticipantStatus;
import com.skillloop.model.SkillType;
import com.skillloop.util.DBConnection;
import com.skillloop.util.InputValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Group learning sessions: one teacher, several learners, a credit cost per learner.
 *
 * <p>Joining charges the learner immediately (there is no two-step confirmation for group
 * sessions), and completing the group distributes one reward entry per learner to the
 * teacher - all inside a single transaction.</p>
 */
public class GroupSessionService {

    private final GroupSessionDAO groupDAO = new GroupSessionDAO();
    private final SkillDAO skillDAO = new SkillDAO();
    private final CreditService creditService = new CreditService();
    private final NotificationService notificationService = new NotificationService();

    public List<GroupSession> openSessions(int limit) {
        return groupDAO.listOpen(limit);
    }

    public List<GroupSession> mySessions(long studentId, int limit) {
        return groupDAO.listForStudent(studentId, limit);
    }

    public GroupSession create(long teacherId, long skillId, String title, LocalDateTime start,
                               LocalDateTime end, int capacity, int costPerLearner) {
        String cleanTitle = InputValidator.requireText(title, "Title", 150);
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ValidationException("Please provide a valid start and end time.");
        }
        if (start.isBefore(LocalDateTime.now().plusMinutes(BookingService.MIN_LEAD_MINUTES))) {
            throw new ValidationException("The group session must start in the future.");
        }
        if (capacity < 2 || capacity > 25) {
            throw new ValidationException("The capacity must be between 2 and 25 learners.");
        }
        if (costPerLearner < 0) {
            throw new ValidationException("The credit cost must not be negative.");
        }
        boolean offers = skillDAO.listByStudent(teacherId, SkillType.OFFER).stream()
                .anyMatch(l -> l.isActive() && l.getSkillId() == skillId);
        if (!offers) {
            throw new ValidationException("You can only host a group session for a skill you offer.");
        }
        GroupSession group = new GroupSession();
        group.setTeacherId(teacherId);
        group.setSkillId(skillId);
        group.setTitle(cleanTitle);
        group.setStartTime(start);
        group.setEndTime(end);
        group.setCapacity(capacity);
        group.setCreditCostPerLearner(costPerLearner);
        group.setStatus(GroupStatus.OPEN);
        group.setGroupId(groupDAO.insert(group));
        return group;
    }

    /** Enrols a learner and charges the enrolment fee atomically. */
    public void join(long groupId, long studentId) {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                GroupSession group = groupDAO.findByIdForUpdate(connection, groupId)
                        .orElseThrow(() -> new NotFoundException("Group session #" + groupId + " does not exist."));
                if (group.getStatus() != GroupStatus.OPEN) {
                    throw new BookingException("This group session is not open for enrolment.");
                }
                if (group.getTeacherId() == studentId) {
                    throw new BookingException("You are the host of this group session.");
                }
                if (group.getStartTime().isBefore(LocalDateTime.now())) {
                    throw new BookingException("This group session has already started.");
                }
                List<GroupParticipant> participants = groupDAO.listParticipants(connection, groupId);
                boolean alreadyIn = participants.stream().anyMatch(p -> p.getStudentId() == studentId
                        && p.getStatus() != ParticipantStatus.CANCELLED);
                if (alreadyIn) {
                    throw new BookingException("You already joined this group session.");
                }
                groupDAO.join(connection, groupId, studentId);
                if (group.getCreditCostPerLearner() > 0) {
                    creditService.chargeGroupLearner(connection, studentId, groupId,
                            group.getCreditCostPerLearner());
                }
                notificationService.notify(connection, group.getTeacherId(), "New group participant",
                        "A student joined your group session \"" + group.getTitle() + "\".",
                        NotificationType.GROUP_SESSION);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not join the group session: " + e.getMessage(), e);
        }
    }

    /** Leaves a group session (the fee is not refunded automatically - an administrator can). */
    public void leave(long groupId, long studentId) {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                groupDAO.updateParticipantStatus(connection, groupId, studentId, ParticipantStatus.CANCELLED);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not leave the group session: " + e.getMessage(), e);
        }
    }

    /**
     * Completes a group session: every joined learner is marked COMPLETED and the teacher
     * receives one GROUP_EARNING entry per learner, all inside one transaction.
     */
    public GroupSession complete(long groupId, long teacherId) {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                GroupSession group = groupDAO.findByIdForUpdate(connection, groupId)
                        .orElseThrow(() -> new NotFoundException("Group session #" + groupId + " does not exist."));
                if (group.getTeacherId() != teacherId) {
                    throw new com.skillloop.exception.AuthorizationException(
                            "Only the host can complete the group session.");
                }
                if (group.getStatus() != GroupStatus.OPEN) {
                    throw new BookingException("Only open group sessions can be completed.");
                }
                int rewarded = 0;
                for (GroupParticipant participant : groupDAO.listParticipants(connection, groupId)) {
                    if (participant.getStatus() != ParticipantStatus.JOINED) {
                        continue;
                    }
                    groupDAO.updateParticipantStatus(connection, groupId, participant.getStudentId(),
                            ParticipantStatus.COMPLETED);
                    if (group.getCreditCostPerLearner() > 0) {
                        creditService.rewardGroupTeaching(connection, teacherId, groupId,
                                participant.getStudentId(), group.getCreditCostPerLearner());
                    }
                    rewarded++;
                }
                groupDAO.updateStatus(connection, groupId, GroupStatus.COMPLETED);
                notificationService.notify(connection, teacherId, "Group session completed",
                        "You earned " + (rewarded * group.getCreditCostPerLearner())
                                + " credits from \"" + group.getTitle() + "\".",
                        NotificationType.GROUP_SESSION);
                connection.commit();
                return groupDAO.findById(connection, groupId).orElse(group);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not complete the group session: " + e.getMessage(), e);
        }
    }

    public List<GroupParticipant> participants(long groupId) {
        try (Connection connection = DBConnection.getConnection()) {
            return groupDAO.listParticipants(connection, groupId);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load the participants: " + e.getMessage(), e);
        }
    }
}
