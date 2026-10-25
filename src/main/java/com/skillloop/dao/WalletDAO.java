package com.skillloop.dao;

import com.skillloop.exception.DatabaseException;
import com.skillloop.exception.InsufficientCreditsException;
import com.skillloop.model.Wallet;
import com.skillloop.util.DBConnection;
import com.skillloop.util.DaoUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * Persistence for the {@code wallets} table.
 *
 * <p>Balance changes are never computed in Java and written back; they are always applied
 * with a relative UPDATE of the form</p>
 * <pre>UPDATE wallets SET balance = balance + ?, held_credits = held_credits + ? WHERE student_id = ? AND balance + ? &gt;= 0</pre>
 * <p>which is atomic even if two bookings run at the same time. The CHECK constraints on the
 * table are the last line of defence.</p>
 */
public class WalletDAO {

    /** Creates the wallet of a new student (called inside the registration transaction). */
    public long createWallet(Connection connection, long studentId) throws SQLException {
        String sql = "INSERT INTO wallets (student_id, balance, held_credits) VALUES (?, 0, 0)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, studentId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("Creating the wallet did not return a generated key.");
        }
    }

    public Optional<Wallet> findByStudent(Connection connection, long studentId) throws SQLException {
        String sql = "SELECT wallet_id, student_id, balance, held_credits, updated_at FROM wallets WHERE student_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Wallet> findByStudent(long studentId) {
        try (Connection connection = DBConnection.getConnection()) {
            return findByStudent(connection, studentId);
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading the wallet", e);
        }
    }

    /**
     * Reads the wallet and locks the row for update (SELECT ... FOR UPDATE).
     * Must be called inside a transaction.
     */
    public Wallet lockForUpdate(Connection connection, long studentId) throws SQLException {
        String sql = "SELECT wallet_id, student_id, balance, held_credits, updated_at FROM wallets "
                + "WHERE student_id = ? FOR UPDATE";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No wallet exists for student " + studentId);
                }
                return map(rs);
            }
        }
    }

    /**
     * Applies a relative change to the wallet.
     *
     * @param amountDelta change of the spendable balance (may be negative)
     * @param heldDelta   change of the reserved credits (may be negative)
     * @throws InsufficientCreditsException when the change would make a counter negative
     */
    public void applyDelta(Connection connection, long studentId, int amountDelta, int heldDelta) throws SQLException {
        String sql = "UPDATE wallets SET balance = balance + ?, held_credits = held_credits + ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE student_id = ? AND balance + ? >= 0 AND held_credits + ? >= 0";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, amountDelta);
            ps.setInt(2, heldDelta);
            ps.setLong(3, studentId);
            ps.setInt(4, amountDelta);
            ps.setInt(5, heldDelta);
            int updated = ps.executeUpdate();
            if (updated != 1) {
                Wallet wallet = findByStudent(connection, studentId).orElse(null);
                int balance = wallet == null ? 0 : wallet.getBalance();
                throw new InsufficientCreditsException(Math.max(-amountDelta, 0), balance);
            }
        }
    }

    /** Wallet enriched with lifetime totals from the {@code v_wallet_summary} view. */
    public Optional<Wallet> loadSummary(long studentId) {
        String sql = "SELECT student_id, balance, held_credits, credits_earned_total, credits_spent_total, transaction_count "
                + "FROM v_wallet_summary WHERE student_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Wallet wallet = new Wallet();
                wallet.setStudentId(rs.getLong("student_id"));
                wallet.setBalance(rs.getInt("balance"));
                wallet.setHeldCredits(rs.getInt("held_credits"));
                wallet.setTotalEarned(rs.getInt("credits_earned_total"));
                wallet.setTotalSpent(rs.getInt("credits_spent_total"));
                return Optional.of(wallet);
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("doesn't exist")) {
                return findByStudent(studentId);
            }
            throw new DatabaseException("Database error while loading the wallet summary: " + e.getMessage(), e);
        }
    }

    private static Wallet map(ResultSet rs) throws SQLException {
        Wallet wallet = new Wallet();
        wallet.setWalletId(rs.getLong("wallet_id"));
        wallet.setStudentId(rs.getLong("student_id"));
        wallet.setBalance(rs.getInt("balance"));
        wallet.setHeldCredits(rs.getInt("held_credits"));
        wallet.setUpdatedAt(DaoUtils.getDateTime(rs, "updated_at"));
        return wallet;
    }
}
