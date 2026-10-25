package com.skillloop.ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.Box;

/** Factory for the recurring Swing pieces: buttons, cards, tables, forms, status pills. */
public final class UiComponents {

    private UiComponents() {
        // utility class
    }

    // ------------------------------------------------------------------ buttons

    public enum ButtonStyle { PRIMARY, SECONDARY, SUCCESS, DANGER, LINK }

    public static JButton button(String text, ButtonStyle style) {
        JButton button = new JButton(text);
        button.setFont(AppTheme.FONT_BOLD);
        button.setFocusPainted(false);
        button.setBorderPainted(style != ButtonStyle.LINK);
        button.setOpaque(style != ButtonStyle.LINK);
        switch (style) {
            case PRIMARY -> {
                button.setBackground(AppTheme.ACCENT);
                button.setForeground(Color.WHITE);
            }
            case SECONDARY -> {
                button.setBackground(AppTheme.NAVY_LIGHT);
                button.setForeground(Color.WHITE);
            }
            case SUCCESS -> {
                button.setBackground(AppTheme.SUCCESS);
                button.setForeground(Color.WHITE);
            }
            case DANGER -> {
                button.setBackground(AppTheme.DANGER);
                button.setForeground(Color.WHITE);
            }
            case LINK -> {
                button.setBackground(AppTheme.BACKGROUND);
                button.setForeground(AppTheme.ACCENT_DARK);
                button.setContentAreaFilled(false);
            }
        }
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(style == ButtonStyle.LINK ? AppTheme.BACKGROUND : darker(
                        button.getBackground())),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        return button;
    }

    private static Color darker(Color color) {
        return color.darker();
    }

    // ------------------------------------------------------------------ labels / cards

    public static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.FONT_HEADING);
        label.setForeground(AppTheme.NAVY);
        return label;
    }

    public static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.FONT_TITLE);
        label.setForeground(AppTheme.NAVY);
        return label;
    }

    public static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.FONT_REGULAR);
        label.setForeground(AppTheme.TEXT);
        return label;
    }

    public static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppTheme.FONT_SMALL);
        label.setForeground(AppTheme.TEXT_MUTED);
        return label;
    }

    /** Small coloured "pill" used for session statuses and roles. */
    public static JLabel statusPill(String text) {
        JLabel label = new JLabel(" " + text + " ");
        label.setFont(AppTheme.FONT_SMALL_BOLD);
        label.setOpaque(true);
        label.setForeground(Color.WHITE);
        label.setBackground(AppTheme.statusColor(text));
        label.setBorder(new EmptyBorder(3, 6, 3, 6));
        return label;
    }

    /** Card with a title and a big value (dashboard tiles). */
    public static JPanel statCard(String caption, String value, Color accent) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppTheme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(AppTheme.FONT_STAT);
        valueLabel.setForeground(accent == null ? AppTheme.NAVY : accent);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel captionLabel = new JLabel(caption);
        captionLabel.setFont(AppTheme.FONT_SMALL);
        captionLabel.setForeground(AppTheme.TEXT_MUTED);
        captionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(valueLabel);
        card.add(Box.createVerticalStrut(2));
        card.add(captionLabel);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        return card;
    }

    /** Titled, bordered white panel used as a section container. */
    public static JPanel card(String title) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(AppTheme.CARD);
        panel.setBorder(AppTheme.cardBorder());
        if (title != null && !title.isBlank()) {
            panel.add(heading(title), BorderLayout.NORTH);
        }
        return panel;
    }

    // ------------------------------------------------------------------ form helpers

    public static JTextField textField(int columns) {
        JTextField field = new JTextField(columns);
        field.setFont(AppTheme.FONT_REGULAR);
        return field;
    }

    public static JPasswordField passwordField(int columns) {
        JPasswordField field = new JPasswordField(columns);
        field.setFont(AppTheme.FONT_REGULAR);
        return field;
    }

    public static JTextArea textArea(int rows, int columns) {
        JTextArea area = new JTextArea(rows, columns);
        area.setFont(AppTheme.FONT_REGULAR);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    public static <T> JComboBox<T> comboBox(T[] items) {
        JComboBox<T> box = new JComboBox<>(items);
        box.setFont(AppTheme.FONT_REGULAR);
        box.setBackground(Color.WHITE);
        return box;
    }

    public static JSpinner spinner(int value, int min, int max, int step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setFont(AppTheme.FONT_REGULAR);
        return spinner;
    }

    /** A labelled field row ("Name: [input]") for the forms. */
    public static JPanel formRow(String labelText, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(10, 4));
        row.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setFont(AppTheme.FONT_SMALL_BOLD);
        label.setForeground(AppTheme.TEXT_MUTED);
        label.setPreferredSize(new Dimension(150, 24));
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    /** Horizontal button bar aligned to the left. */
    public static JPanel buttonBar(JButton... buttons) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setOpaque(false);
        for (JButton button : buttons) {
            bar.add(button);
        }
        return bar;
    }

    // ------------------------------------------------------------------ tables

    /** Non-editable table model with styled header and zebra-free, readable rows. */
    public static DefaultTableModel tableModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    public static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(AppTheme.FONT_REGULAR);
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getTableHeader().setReorderingAllowed(false);
        JTableHeader header = table.getTableHeader();
        header.setFont(AppTheme.FONT_SMALL_BOLD);
        header.setBackground(AppTheme.NAVY);
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 32));
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.LEFT);
        table.setDefaultRenderer(Object.class, center);
        return table;
    }

    public static JScrollPane scroll(JComponent component) {
        JScrollPane pane = new JScrollPane(component);
        pane.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER));
        pane.getViewport().setBackground(AppTheme.CARD);
        return pane;
    }

    /** Empty-state hint shown inside a card while data is loading. */
    public static JLabel emptyHint(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(AppTheme.FONT_SMALL);
        label.setForeground(AppTheme.TEXT_MUTED);
        label.setBorder(new EmptyBorder(20, 10, 20, 10));
        return label;
    }
}
