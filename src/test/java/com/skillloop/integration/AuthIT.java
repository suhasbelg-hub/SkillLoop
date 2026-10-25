package com.skillloop.integration;

import com.skillloop.exception.AuthenticationException;
import com.skillloop.exception.DuplicateResourceException;
import com.skillloop.exception.ValidationException;
import com.skillloop.model.AccountStatus;
import com.skillloop.model.Role;
import com.skillloop.model.Student;
import com.skillloop.model.Wallet;
import com.skillloop.service.AuthService;
import com.skillloop.service.CreditService;
import com.skillloop.support.TestEnvironment;
import com.skillloop.util.CreditsPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registration, login and wallet initialisation against a real MySQL database. */
class AuthIT {

    private static final String PASSWORD = "SkillLoop@123";
    private final List<Long> created = new ArrayList<>();
    private final AuthService authService = new AuthService();

    @BeforeAll
    static void requireDatabase() {
        TestEnvironment.assumeDatabase();
    }

    @AfterEach
    void cleanUp() {
        created.forEach(TestEnvironment::deleteStudent);
        created.clear();
    }

    private Student register(String email) {
        Student student = authService.register("Test Student", email, PASSWORD, PASSWORD,
                "Computer Science", 5);
        created.add(student.getStudentId());
        return student;
    }

    @Test
    @DisplayName("Registration stores a BCrypt hash, never the plaintext password")
    void passwordIsHashed() {
        Student student = register(TestEnvironment.uniqueEmail());
        assertNotEquals(PASSWORD, student.getPasswordHash());
        assertTrue(student.getPasswordHash().startsWith("$2a$"));
    }

    @Test
    @DisplayName("Registration is atomic: the student and the wallet are created together")
    void walletIsCreatedAtomically() {
        Student student = register(TestEnvironment.uniqueEmail());
        Wallet wallet = new CreditService().getWallet(student.getStudentId()).orElse(null);
        assertTrue(wallet != null, "A wallet must exist directly after the registration");
        assertEquals(CreditsPolicy.starterGrantAmount(), wallet.getBalance(),
                "The one-time starter credit must be granted during the registration");
    }

    @Test
    @DisplayName("A new account is a normal student, never an administrator")
    void newAccountsAreStudents() {
        Student student = register(TestEnvironment.uniqueEmail());
        assertEquals(Role.STUDENT, student.getRole());
        assertEquals(AccountStatus.ACTIVE, student.getAccountStatus());
    }

    @Test
    @DisplayName("A duplicate email address is rejected")
    void duplicateEmailIsRejected() {
        String email = TestEnvironment.uniqueEmail();
        register(email);
        assertThrows(DuplicateResourceException.class,
                () -> authService.register("Someone Else", email, PASSWORD, PASSWORD, "Design", 3));
    }

    @Test
    @DisplayName("Login works with the correct password and fails with a wrong one")
    void loginBehaviour() {
        Student student = register(TestEnvironment.uniqueEmail());
        Student loggedIn = authService.login(student.getEmail(), PASSWORD);
        assertEquals(student.getStudentId(), loggedIn.getStudentId());
        assertThrows(AuthenticationException.class,
                () -> authService.login(student.getEmail(), "wrong-password"));
        assertThrows(AuthenticationException.class,
                () -> authService.login("unknown" + TestEnvironment.uniqueEmail(), PASSWORD));
    }

    @Test
    @DisplayName("Suspended accounts cannot sign in")
    void suspendedAccountCannotSignIn() {
        Student student = register(TestEnvironment.uniqueEmail());
        new com.skillloop.dao.StudentDAO().updateAccountStatus(student.getStudentId(), AccountStatus.SUSPENDED);
        assertThrows(AuthenticationException.class, () -> authService.login(student.getEmail(), PASSWORD));
    }

    @Test
    @DisplayName("Weak passwords and mismatching confirmations are rejected before any SQL runs")
    void weakPasswordIsRejected() {
        String email = TestEnvironment.uniqueEmail();
        assertThrows(ValidationException.class,
                () -> authService.register("Test Student", email, "123", "123", "CS", 5));
        assertThrows(ValidationException.class,
                () -> authService.register("Test Student", email, PASSWORD, PASSWORD + "x", "CS", 5));
    }

    @Test
    @DisplayName("The starter credit can never be claimed twice")
    void starterGrantIsOneTime() {
        Student student = register(TestEnvironment.uniqueEmail());
        CreditService creditService = new CreditService();
        int before = creditService.availableBalance(student.getStudentId());
        creditService.grantStarterGrant(student.getStudentId());
        assertEquals(before, creditService.availableBalance(student.getStudentId()),
                "The second grant must be a no-op");
    }
}
