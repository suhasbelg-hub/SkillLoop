package com.skillloop.ui;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import java.awt.Component;

/** Thin wrapper around {@link JOptionPane} with consistent titles and icons. */
public final class Dialogs {

    private Dialogs() {
        // utility class
    }

    public static void info(Component parent, String message) {
        info(parent, "SkillLoop", message);
    }

    public static void info(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void error(Component parent, String message) {
        error(parent, "SkillLoop - Problem", message);
    }

    public static void error(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public static void warning(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "SkillLoop", JOptionPane.WARNING_MESSAGE);
    }

    public static boolean confirm(Component parent, String question) {
        return confirm(parent, "Please confirm", question);
    }

    public static boolean confirm(Component parent, String title, String question) {
        return JOptionPane.showConfirmDialog(parent, question, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Multiline text input dialog (used for reasons, notes and comments). */
    public static String input(Component parent, String title, String prompt, String initialValue) {
        Object result = JOptionPane.showInputDialog(parent, prompt, title,
                JOptionPane.PLAIN_MESSAGE, null, null, initialValue);
        return result == null ? null : result.toString();
    }

    /** Password input dialog that never echoes the typed characters. */
    public static String password(Component parent, String title, String prompt) {
        JPasswordField field = new JPasswordField(24);
        int choice = JOptionPane.showConfirmDialog(parent, field, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return null;
        }
        return new String(field.getPassword());
    }

    /** Shows a modal frame when the database is unreachable. */
    public static void databaseUnavailable(Component parent, String details) {
        String message = "SkillLoop cannot reach the MySQL database.\n\n"
                + "Details: " + details + "\n\n"
                + "Please check that MySQL is running, that the 'skillloop' database exists "
                + "(see docs/SETUP_GUIDE.md) and that your credentials are stored in "
                + "config/application.properties or in the SKILLLOOP_DB_* environment variables.";
        JOptionPane.showMessageDialog(parent == null ? new JFrame() : parent, message,
                "SkillLoop - Database unavailable", JOptionPane.ERROR_MESSAGE);
    }
}
