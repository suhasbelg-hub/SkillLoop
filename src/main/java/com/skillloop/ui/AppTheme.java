package com.skillloop.ui;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;

/**
 * Central look and feel definition: navy blue, white and neutral grey.
 *
 * <p>FlatLaf is used when it is on the classpath (it is a Maven dependency), but it is loaded
 * <b>reflectively</b>: if the jar is missing the application still starts and falls back to
 * Nimbus or the system look and feel. The custom colour palette is applied in both cases.</p>
 */
public final class AppTheme {

    // ---- palette -----------------------------------------------------------------
    public static final Color NAVY_DARK = new Color(0x0F, 0x24, 0x40);
    public static final Color NAVY = new Color(0x14, 0x36, 0x5C);
    public static final Color NAVY_LIGHT = new Color(0x1F, 0x4B, 0x7A);
    public static final Color ACCENT = new Color(0x2E, 0x7D, 0xD1);
    public static final Color ACCENT_DARK = new Color(0x1F, 0x5F, 0xA6);
    public static final Color BACKGROUND = new Color(0xF4, 0xF6, 0xF9);
    public static final Color CARD = Color.WHITE;
    public static final Color BORDER = new Color(0xDD, 0xE3, 0xEB);
    public static final Color TEXT = new Color(0x1C, 0x2B, 0x3A);
    public static final Color TEXT_MUTED = new Color(0x6B, 0x7A, 0x8D);
    public static final Color SUCCESS = new Color(0x1E, 0x8E, 0x5A);
    public static final Color WARNING = new Color(0xC8, 0x7A, 0x0B);
    public static final Color DANGER = new Color(0xC0, 0x39, 0x2B);
    public static final Color INFO = new Color(0x17, 0x6C, 0x9E);

    // ---- fonts -------------------------------------------------------------------
    public static final String FONT_FAMILY = "Segoe UI";
    public static final Font FONT_REGULAR = new Font(FONT_FAMILY, Font.PLAIN, 14);
    public static final Font FONT_BOLD = new Font(FONT_FAMILY, Font.BOLD, 14);
    public static final Font FONT_SMALL = new Font(FONT_FAMILY, Font.PLAIN, 12);
    public static final Font FONT_SMALL_BOLD = new Font(FONT_FAMILY, Font.BOLD, 12);
    public static final Font FONT_TITLE = new Font(FONT_FAMILY, Font.BOLD, 22);
    public static final Font FONT_HEADING = new Font(FONT_FAMILY, Font.BOLD, 17);
    public static final Font FONT_STAT = new Font(FONT_FAMILY, Font.BOLD, 26);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 13);

    private static String activeLookAndFeel = "unknown";

    private AppTheme() {
        // utility class
    }

    /**
     * Installs the look and feel. Must be called on the Event Dispatch Thread before any
     * component is created.
     */
    public static void install() {
        activeLookAndFeel = installFlatLaf() ? "FlatLaf" : installNimbus();
        applyCustomDefaults();
    }

    /** Tries FlatLaf through reflection (optional dependency). */
    private static boolean installFlatLaf() {
        try {
            Class<?> flatLightLaf = Class.forName("com.formdev.flatlaf.FlatLightLaf");
            UIManager.setLookAndFeel((javax.swing.LookAndFeel) flatLightLaf.getDeclaredConstructor().newInstance());
            return true;
        } catch (ClassNotFoundException e) {
            return false; // dependency not on the classpath - use the built-in fallbacks
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            System.err.println("[SkillLoop] FlatLaf could not be initialised: " + e.getMessage());
            return false;
        }
    }

    private static String installNimbus() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return "Nimbus";
                }
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            return "System";
        } catch (Exception e) {
            return "Default";
        }
    }

    /** Applies the SkillLoop palette on top of whatever look and feel is active. */
    private static void applyCustomDefaults() {
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("OptionPane.messageFont", FONT_REGULAR);
        UIManager.put("OptionPane.buttonFont", FONT_REGULAR);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", FONT_REGULAR);
        UIManager.put("Button.font", FONT_BOLD);
        UIManager.put("Button.background", ACCENT);
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("TextField.font", FONT_REGULAR);
        UIManager.put("PasswordField.font", FONT_REGULAR);
        UIManager.put("TextArea.font", FONT_REGULAR);
        UIManager.put("Table.font", FONT_REGULAR);
        UIManager.put("Table.background", CARD);
        UIManager.put("Table.selectionBackground", new Color(0xD6, 0xE6, 0xF7));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("TableHeader.font", FONT_SMALL_BOLD);
        UIManager.put("TableHeader.background", NAVY);
        UIManager.put("TableHeader.foreground", Color.WHITE);
        UIManager.put("ComboBox.font", FONT_REGULAR);
        UIManager.put("TabbedPane.font", FONT_BOLD);
        UIManager.put("TabbedPane.background", BACKGROUND);
        UIManager.put("TabbedPane.contentAreaColor", BACKGROUND);
    }

    /** Name of the look and feel that is actually active (shown on the login screen). */
    public static String activeLookAndFeel() {
        return activeLookAndFeel;
    }

    /** Standard empty border used by cards and forms. */
    public static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(12, 14, 12, 14));
    }

    /** Thin border used by table panels. */
    public static Border panelBorder(String title) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER), title),
                BorderFactory.createEmptyBorder(6, 8, 8, 8));
    }

    /** Returns a status colour for the labels used in the session tables. */
    public static Color statusColor(String status) {
        if (status == null) {
            return TEXT_MUTED;
        }
        return switch (status.toUpperCase()) {
            case "COMPLETED" -> SUCCESS;
            case "ACCEPTED" -> INFO;
            case "REQUESTED" -> WARNING;
            case "REJECTED", "CANCELLED" -> TEXT_MUTED;
            case "DISPUTED" -> DANGER;
            default -> TEXT;
        };
    }
}
