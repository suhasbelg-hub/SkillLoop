package com.skillloop.service;

import com.skillloop.dao.StudentDAO;
import com.skillloop.dao.WalletDAO;
import com.skillloop.exception.AuthenticationException;
import com.skillloop.exception.AuthorizationException;
import com.skillloop.exception.DuplicateResourceException;
import com.skillloop.exception.NotFoundException;
import com.skillloop.exception.ValidationException;
import com.skillloop.model.AccountStatus;
import com.skillloop.model.Role;
import com.skillloop.model.Student;
import com.skillloop.util.AppConfig;
import com.skillloop.util.DBConnection;
import com.skillloop.util.InputValidator;
import com.skillloop.util.PasswordUtil;
import com.skillloop.util.SessionContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Registration, login, logout and password management.
 *
 * <p>Registration and wallet creation happen in a <b>single ACID transaction</b>: a student
 * can never exist without a wallet (and vice versa).</p>
 */
public class AuthService {

    private final StudentDAO studentDAO = new StudentDAO();
    private final WalletDAO walletDAO = new WalletDAO();
    private final CreditService creditService = new CreditService();

    /**
     * Registers a new student: validates the input, hashes the password with BCrypt,
     * inserts the student, creates the wallet and grants the one-time starter credit.
     *
     * @throws DuplicateResourceException when the email address is already registered
     * @throws ValidationException       when a field is invalid
     */
    public Student register(String fullName, String email, String password, String confirmation,
                            String department, int semester) {
        String cleanName = InputValidator.validateFullName(fullName);
        String cleanEmail = InputValidator.validateEmail(email);
        String cleanDepartment = InputValidator.validateDepartment(department);
        InputValidator.validateSemester(semester);
        InputValidator.validatePassword(password, confirmation);

        Student student = new Student();
        student.setFullName(cleanName);
        student.setEmail(cleanEmail);
        student.setPasswordHash(PasswordUtil.hash(password));
        student.setDepartment(cleanDepartment);
        student.setSemester(semester);
        // New accounts are never administrators: the role is assigned explicitly by a tool.
        student.setRole(Role.STUDENT);
        student.setAccountStatus(AccountStatus.ACTIVE);

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (studentDAO.existsEmail(connection, cleanEmail)) {
                    connection.rollback();
                    throw new DuplicateResourceException(
                            "The email address '" + cleanEmail + "' is already registered.");
                }
                long studentId = studentDAO.insert(connection, student);
                walletDAO.createWallet(connection, studentId);
                creditService.grantStarterGrant(connection, studentId);
                connection.commit();
                student.setStudentId(studentId);
                return student;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof DuplicateResourceException) {
                    throw e;
                }
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                throw new DuplicateResourceException(
                        "The email address '" + cleanEmail + "' is already registered.", e);
            }
            throw new IllegalStateException("Could not create the account: " + e.getMessage(), e);
        }
    }

    /**
     * Authenticates a student and stores it in the {@link SessionContext}.
     *
     * @throws AuthenticationException for unknown emails, wrong passwords and inactive accounts
     */
    public Student login(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            throw new ValidationException("Please enter your email and password.");
        }
        Student student;
        try {
            student = studentDAO.findByEmail(email.trim().toLowerCase())
                    .orElseThrow(() -> new AuthenticationException("Invalid email or password."));
        } catch (RuntimeException e) {
            throw new AuthenticationException("Invalid email or password.", e);
        }
        if (!PasswordUtil.verify(password, student.getPasswordHash())) {
            throw new AuthenticationException("Invalid email or password.");
        }
        if (student.getAccountStatus() == AccountStatus.SUSPENDED) {
            throw new AuthenticationException("Your account has been suspended. Please contact an administrator.");
        }
        if (student.getAccountStatus() == AccountStatus.INACTIVE) {
            throw new AuthenticationException("Your account is inactive. Please contact an administrator.");
        }
        SessionContext.login(student);
        return student;
    }

    /** Clears the current session. */
    public void logout() {
        SessionContext.logout();
    }

    /** Changes the password of the given student after verifying the old one. */
    public void changePassword(long studentId, String oldPassword, String newPassword, String confirmation) {
        Student student = studentDAO.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student #" + studentId + " does not exist."));
        if (!PasswordUtil.verify(oldPassword, student.getPasswordHash())) {
            throw new AuthenticationException("The current password is not correct.");
        }
        List<String> problems = PasswordUtil.validateStrength(newPassword);
        if (!problems.isEmpty()) {
            throw new ValidationException(String.join(" ", problems));
        }
        if (!newPassword.equals(confirmation)) {
            throw new ValidationException("The new password and its confirmation do not match.");
        }
        if (PasswordUtil.verify(newPassword, student.getPasswordHash())) {
            throw new ValidationException("The new password must be different from the current one.");
        }
        studentDAO.updatePasswordHash(studentId, PasswordUtil.hash(newPassword));
    }

    /**
     * Creates the very first administrator account.
     *
     * <p>There is no hard-coded administrator password anywhere in SkillLoop. This method
     * refuses to run as soon as one administrator exists; from then on new administrators are
     * promoted by an existing administrator through {@link #promoteToAdmin}.</p>
     */
    public Student bootstrapAdministrator(String fullName, String email, String password, String confirmation,
                                          String department, int semester) {
        if (studentDAO.countAdmins() > 0) {
            throw new AuthorizationException(
                    "An administrator already exists. Ask them to promote the new account instead.");
        }
        Student student = register(fullName, email, password, confirmation, department, semester);
        studentDAO.updateRole(student.getStudentId(), Role.ADMIN);
        student.setRole(Role.ADMIN);
        return student;
    }

    /** Promotes an existing account to administrator (audited by the caller). */
    public void promoteToAdmin(long actorId, long studentId) {
        Student actor = studentDAO.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor #" + actorId + " does not exist."));
        if (!actor.isAdmin()) {
            throw new AuthorizationException("Only an administrator can promote another account.");
        }
        studentDAO.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student #" + studentId + " does not exist."));
        if (actorId == studentId) {
            throw new ValidationException("You already are an administrator.");
        }
        studentDAO.updateRole(studentId, Role.ADMIN);
        new NotificationService().notify(studentId, "Role updated",
                "You were promoted to administrator by " + actor.getFullName() + ".",
                com.skillloop.model.NotificationType.ADMIN_MESSAGE);
    }

    /** Demotes an administrator back to an ordinary student. */
    public void demoteToStudent(long actorId, long studentId) {
        Student actor = studentDAO.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor #" + actorId + " does not exist."));
        if (!actor.isAdmin()) {
            throw new AuthorizationException("Only an administrator can change roles.");
        }
        if (actorId == studentId && studentDAO.countAdmins() == 1) {
            throw new ValidationException("The platform must keep at least one administrator.");
        }
        studentDAO.updateRole(studentId, Role.STUDENT);
    }

    /** Human readable reminder where the local credentials are read from. */
    public String configurationHint() {
        return "Database credentials are read from " + AppConfig.LOCAL_CONFIG
                + " or from the SKILLLOOP_DB_URL / SKILLLOOP_DB_USER / SKILLLOOP_DB_PASSWORD "
                + "environment variables. They are never stored in the source code.";
    }
}
