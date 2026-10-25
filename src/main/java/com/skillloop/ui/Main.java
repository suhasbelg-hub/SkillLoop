package com.skillloop;

import com.skillloop.ui.AppTheme;
import com.skillloop.ui.LoginFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Entry point of the SkillLoop desktop application.
 *
 * <p>Usage:</p>
 * <pre>
 *   mvn -q exec:java -Dexec.mainClass=com.skillloop.Main     (or run this class from Eclipse)
 *   java -jar target/skillloop.jar
 * </pre>
 */
public final class Main {

    private Main() {
        // application entry point
    }

    public static void main(String[] args) {
        if (args.length > 0 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            printHelp();
            return;
        }
        // All Swing work must happen on the Event Dispatch Thread.
        SwingUtilities.invokeLater(Main::start);
    }

    private static void start() {
        AppTheme.install();
        UIManager.put("OptionPane.buttonFont", AppTheme.FONT_REGULAR);
        LoginFrame loginFrame = new LoginFrame();
        loginFrame.checkDatabase();
        loginFrame.setVisible(true);
    }

    private static void printHelp() {
        System.out.println("SkillLoop - A Credit-Based Peer Learning Platform");
        System.out.println();
        System.out.println("  com.skillloop.Main                 start the Swing application");
        System.out.println("  com.skillloop.tools.ConnectionTest  test the JDBC connection");
        System.out.println("  com.skillloop.tools.DbSetupTool     --check | --init | --seed | --views");
        System.out.println("  com.skillloop.tools.AdminBootstrapTool  create the first administrator");
        System.out.println();
        System.out.println("Database credentials are read from config/application.properties or from");
        System.out.println("SKILLLOOP_DB_URL / SKILLLOOP_DB_USER / SKILLLOOP_DB_PASSWORD.");
    }
}
