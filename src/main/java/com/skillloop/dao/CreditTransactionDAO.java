package com.skillloop.dao;

import com.skillloop.model.CreditTransaction;
import com.skillloop.model.TransactionType;
import com.skillloop.util.DBConnection;
import com.skillloop.util.DaoUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for the append-only credit ledger.
 *
 * <p>The unique column {@code txn_reference} guarantees at database level that a session can
 * never be settled twice, even if two threads race through the service layer.</p>
 */
public class CreditTransactionDAO {

    private static final String BASE =
            "SELECT ct.transaction_id, ct.student_id, ct.session_id, ct.amount, ct.held_amount, ct.balance_after, "
            + "ct.held_after, ct.transaction_type, ct.txn_reference, ct.description, ct.created_by, ct.created_at, "
            + "s.full_name AS student_name "
            + "FROM credit_transactions ct JOIN students s ON s.student_id = ct.student_id ";

    public long insert(Connection connection, CreditTransaction transaction) throws SQLException {
        String sql = "INSERT INTO credit_transactions (student_id, session_id, amount, held_amount, balance_after, "
                + "held_after, transaction_type, txn_reference, description, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, transaction.getStudentId());
            DaoUtils.setNullableLong(ps, 2, transaction.getSessionId());
            ps.setInt(3, transaction.getAmount());
            ps.setInt(4, transaction.getHeldAmount());
            ps.setInt(5, transaction.getBalanceAfter());
            ps.setInt(6, transaction.getHeldAfter());
            ps.setString(7, transaction.getType().name());
            ps.setString(8, transaction.getReference());
            DaoUtils.setNullableString(ps, 9, transaction.getDescription());
            DaoUtils.setNullableLong(ps, 10, transaction.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("Inserting the ledger entry did not return a generated key.");
        }
    }

    /** Idempotency guard: true when a ledger entry with this reference already exists. */
    public boolean existsReference(Connection connection, String reference) throws SQLException {
        String sql = "SELECT COUNT(*) FROM credit_transactions WHERE txn_reference = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, reference);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public Optional<CreditTransaction> findByReference(Connection connection, String reference) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(BASE + "WHERE ct.txn_reference = ?")) {
            ps.setString(1, reference);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<CreditTransaction> listForStudent(long studentId, int limit) {
        String sql = BASE + "WHERE ct.student_id = ? ORDER BY ct.created_at DESC, ct.transaction_id DESC LIMIT ?";
        return query(sql, List.of(studentId, limit));
    }

    public List<CreditTransaction> listForSession(Connection connection, long sessionId) throws SQLException {
        String sql = BASE + "WHERE ct.session_id = ? ORDER BY ct.transaction_id";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<CreditTransaction> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(map(rs));
                }
                return rows;
            }
        }
    }

    /** Most recent ledger entries across all students (administrator monitoring). */
    public List<CreditTransaction> listRecent(int limit) {
        String sql = BASE + "ORDER BY ct.created_at DESC, ct.transaction_id DESC LIMIT ?";
        return query(sql, List.of(limit));
    }

    /** Sum of all amounts of one type for a student (for example lifetime earnings). */
    public int sumByType(long studentId, TransactionType type) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM credit_transactions WHERE student_id = ? AND transaction_type = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("summarising credit transactions", e);
        }
    }

    private List<CreditTransaction> query(String sql, List<Object> params) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<CreditTransaction> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(map(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw DaoUtils.wrap("loading credit transactions", e);
        }
    }

    private static CreditTransaction map(ResultSet rs) throws SQLException {
        CreditTransaction row = new CreditTransaction();
        row.setTransactionId(rs.getLong("transaction_id"));
        row.setStudentId(rs.getLong("student_id"));
        row.setSessionId(DaoUtils.getNullableLong(rs, "session_id"));
        row.setAmount(rs.getInt("amount"));
        row.setHeldAmount(rs.getInt("held_amount"));
        row.setBalanceAfter(rs.getInt("balance_after"));
        row.setHeldAfter(rs.getInt("held_after"));
        row.setType(TransactionType.fromDb(rs.getString("transaction_type")));
        row.setReference(rs.getString("txn_reference"));
        row.setDescription(rs.getString("description"));
        row.setCreatedBy(DaoUtils.getNullableLong(rs, "created_by"));
        row.setCreatedAt(DaoUtils.getDateTime(rs, "created_at"));
        row.setStudentName(rs.getString("student_name"));
        return row;
    }
}
