package com.skillloop.ui;

import com.skillloop.model.CreditTransaction;
import com.skillloop.model.Wallet;
import com.skillloop.service.CreditService;
import com.skillloop.util.CreditsPolicy;
import com.skillloop.util.DateTimeUtil;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Optional;

/** Credit wallet: balance, reserved credits, lifetime totals and the full ledger. */
public class WalletPanel extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    private final long studentId;
    private final Runnable walletListener;
    private final JPanel statsRow = new JPanel(new GridLayout(1, 5, 12, 12));
    private final DefaultTableModel model = UiComponents.tableModel(
            new String[]{"When", "Type", "Amount", "Held", "Balance after", "Reference", "Details"});
    private final JButton starterButton = UiComponents.button("Claim starter credit",
            UiComponents.ButtonStyle.SECONDARY);
    private final CreditService creditService = new CreditService();

    public WalletPanel(long studentId, Runnable walletListener) {
        this.studentId = studentId;
        this.walletListener = walletListener;
        setLayout(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        buildUi();
    }

    private void buildUi() {
        statsRow.setOpaque(false);
        statsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));

        JPanel historyCard = UiComponents.card("Credit transaction history");
        JTable table = UiComponents.table(model);
        historyCard.add(UiComponents.scroll(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        actions.setOpaque(false);
        JButton reloadButton = UiComponents.button("Reload", UiComponents.ButtonStyle.LINK);
        actions.add(starterButton);
        actions.add(reloadButton);
        historyCard.add(actions, BorderLayout.SOUTH);

        starterButton.addActionListener(event -> AsyncRunner.run(this,
                () -> creditService.grantStarterGrant(studentId), granted -> {
                    if (Boolean.TRUE.equals(granted)) {
                        Dialogs.info(this, CreditsPolicy.starterGrantAmount()
                                + " starter credits were added to your wallet.");
                    } else {
                        Dialogs.info(this, "The starter credit was already claimed for your account.");
                    }
                    if (walletListener != null) {
                        walletListener.run();
                    }
                    refresh();
                }));
        reloadButton.addActionListener(event -> refresh());

        JPanel policy = UiComponents.card("Credit policy");
        policy.add(UiComponents.muted(
                        "<html>30 minutes = 10 credits, 60 minutes = 20 credits. "
                                + "Credits are reserved when you request a session and transferred to the teacher "
                                + "only after both of you confirm completion. Cancelled sessions release the "
                                + "reservation automatically.</html>"),
                BorderLayout.CENTER);
        policy.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(statsRow);
        content.add(Box.createVerticalStrut(12));
        content.add(historyCard);
        content.add(Box.createVerticalStrut(12));
        content.add(policy);
        add(content, BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        AsyncRunner.run(this, () -> {
            WalletData data = new WalletData();
            data.wallet = creditService.getWallet(studentId);
            data.transactions = creditService.history(studentId, 100);
            return data;
        }, this::render);
    }

    private void render(WalletData data) {
        Optional<Wallet> wallet = data.wallet;
        int balance = wallet.map(Wallet::getBalance).orElse(0);
        int held = wallet.map(Wallet::getHeldCredits).orElse(0);
        int earned = wallet.map(Wallet::getTotalEarned).orElse(0);
        int spent = wallet.map(Wallet::getTotalSpent).orElse(0);

        statsRow.removeAll();
        statsRow.add(UiComponents.statCard("Available credits", String.valueOf(balance), AppTheme.NAVY));
        statsRow.add(UiComponents.statCard("Reserved (held)", String.valueOf(held), AppTheme.WARNING));
        statsRow.add(UiComponents.statCard("Credits earned", String.valueOf(earned), AppTheme.SUCCESS));
        statsRow.add(UiComponents.statCard("Credits spent", String.valueOf(spent), AppTheme.DANGER));
        statsRow.add(UiComponents.statCard("Transactions", String.valueOf(data.transactions.size()),
                AppTheme.ACCENT_DARK));
        statsRow.revalidate();
        statsRow.repaint();

        starterButton.setVisible(earned == 0 && balance == 0);

        model.setRowCount(0);
        for (CreditTransaction transaction : data.transactions) {
            model.addRow(new Object[]{
                    DateTimeUtil.format(transaction.getCreatedAt()),
                    transaction.getType().name(),
                    (transaction.getAmount() > 0 ? "+" : "") + transaction.getAmount(),
                    transaction.getHeldAmount() == 0 ? "" : (transaction.getHeldAmount() > 0 ? "+" : "")
                            + transaction.getHeldAmount(),
                    transaction.getBalanceAfter(),
                    transaction.getReference(),
                    transaction.getDescription()});
        }
    }

    /** Snapshot loaded in one background task. */
    private static final class WalletData {
        private Optional<Wallet> wallet = Optional.empty();
        private List<CreditTransaction> transactions = List.of();
    }
}
