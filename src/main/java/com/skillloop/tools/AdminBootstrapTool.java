package com.skillloop.tools;

import com.skillloop.model.Student;
import com.skillloop.service.AuthService;
import com.skillloop.util.DBConnection;
import com.skillloop.util.InputValidator;

import java.io.Console;
import java.util.List;
import java.util.Scanner;

/**
 * Creates the <b>first</b> administrator account.
 *
 * <p>SkillLoop ships with no default administrator password. This tool asks for the details
 * interactively (the password is read from the console without echo) and refuses to run as soon
 * as one administrator exists - further administrators are promoted from inside the application
 * by an existing administrator.</p>
 *
 * <pre>
 *   mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.AdminBootstrapTool
 * </pre>
 */
public final class AdminBootstrapTool {

    private AdminBootstrapTool() {
        // command line tool
    }

    public static void main(String[] args) {
        System.out.println("=========================================================");
        System.out.println(" SkillLoop - create the first administrator account");
        System.out.println("=========================================================");
        var test = DBConnection.testConnection();
        if (!test.isSuccess()) {
            System.out.println("Cannot reach the database: " + test.getMessage());
            System.exit(1);
        }
        System.out.println("Database: " + test.getDatabaseName() + " (MySQL " + test.getServerVersion() + ")");
        System.out.println();

        Console console = System.console();
        Scanner scanner = new Scanner(System.in);
        String fullName = ask(scanner, "Full name");
        String email = ask(scanner, "College email");
        String department = ask(scanner, "Department");
        int semester = readInt(scanner, "Semester (1-12)", 1);
        String password;
        String confirmation;
        if (console != null) {
            char[] first = console.readPassword("Password (min. 8 characters, letters and digits): ");
            char[] second = console.readPassword("Confirm password: ");
            password = new String(first);
            confirmation = new String(second);
            java.util.Arrays.fill(first, ' ');
            java.util.Arrays.fill(second, ' ');
        } else {
            System.out.println("Console not available - the password will be visible while typing.");
            password = ask(scanner, "Password");
            confirmation = ask(scanner, "Confirm password");
        }

        List<String> problems = InputValidator.registrationProblems(fullName, email, password, confirmation,
                department, semester);
        if (!problems.isEmpty()) {
            problems.forEach(problem -> System.out.println(" - " + problem));
            System.exit(1);
        }

        try {
            AuthService authService = new AuthService();
            Student admin = authService.bootstrapAdministrator(fullName, email, password, confirmation,
                    department, semester);
            System.out.println();
            System.out.println("Administrator account created: " + admin.getFullName()
                    + " (" + admin.getEmail() + ")");
            System.out.println("The password was hashed with BCrypt and is not stored anywhere in plain text.");
            System.out.println("Sign in with this account and use the 'Administrator console' button.");
        } catch (RuntimeException e) {
            System.out.println("Could not create the administrator: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String ask(Scanner scanner, String prompt) {
        System.out.print(prompt + ": ");
        return scanner.nextLine().trim();
    }

    private static int readInt(Scanner scanner, String prompt, int fallback) {
        System.out.print(prompt + ": ");
        String value = scanner.nextLine().trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
