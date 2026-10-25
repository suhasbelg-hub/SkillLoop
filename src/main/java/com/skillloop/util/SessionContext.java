package com.skillloop.util;

import com.skillloop.exception.AuthenticationException;
import com.skillloop.exception.AuthorizationException;
import com.skillloop.model.Student;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Holds the currently logged-in student for the running desktop session.
 *
 * <p>The UI listens to {@link SessionListener} so that the dashboard refreshes whenever the
 * profile or the wallet changes.</p>
 */
public final class SessionContext {

    /** Notified when a user logs in, logs out or refreshes their profile. */
    public interface SessionListener {
        void sessionChanged(Student student);
    }

    private static final List<SessionListener> LISTENERS = new ArrayList<>();
    private static volatile Student currentStudent;

    private SessionContext() {
        // utility class
    }

    public static void addListener(SessionListener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(SessionListener listener) {
        LISTENERS.remove(listener);
    }

    /** Stores the authenticated student and notifies all listeners. */
    public static void login(Student student) {
        if (student == null) {
            throw new AuthenticationException("Login failed: no student was returned by the service.");
        }
        currentStudent = student;
        notifyListeners();
    }

    /** Clears the session (logout). */
    public static void logout() {
        currentStudent = null;
        notifyListeners();
    }

    /** Re-publishes the stored student (used after a profile or wallet update). */
    public static void refresh(Student student) {
        if (student != null) {
            currentStudent = student;
            notifyListeners();
        }
    }

    public static Optional<Student> current() {
        return Optional.ofNullable(currentStudent);
    }

    public static Student currentOrThrow() {
        Student student = currentStudent;
        if (student == null) {
            throw new AuthenticationException("You must be logged in to perform this action.");
        }
        return student;
    }

    public static boolean isLoggedIn() {
        return currentStudent != null;
    }

    public static boolean isAdmin() {
        return currentStudent != null && currentStudent.isAdmin();
    }

    /** Returns the id of the logged-in student or throws. */
    public static long currentId() {
        return currentOrThrow().getStudentId();
    }

    /**
     * Guard for administrator-only operations. The check lives in the service layer so that
     * hiding a button in the UI is never the only protection.
     */
    public static Student requireAdmin() {
        Student student = currentOrThrow();
        if (!student.isAdmin()) {
            throw new AuthorizationException("Administrator permissions are required for this action.");
        }
        return student;
    }

    private static void notifyListeners() {
        for (SessionListener listener : new ArrayList<>(LISTENERS)) {
            listener.sessionChanged(currentStudent);
        }
    }
}
