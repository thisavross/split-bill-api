package com.splitbill.service;

import com.splitbill.domain.Expense;
import com.splitbill.domain.Money;
import com.splitbill.domain.Payment;
import com.splitbill.domain.Transfer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Pure settlement logic (no Spring, no I/O) so it is trivial to unit test. */
public final class SettlementCalculator {

    private SettlementCalculator() {
    }

    /**
     * Net balance per participant. Positive = is owed money, negative = owes money.
     * net = paid - owed + payments made - payments received.
     */
    public static Map<String, BigDecimal> balances(List<String> participants,
                                                   List<Expense> expenses,
                                                   List<Payment> payments) {
        Map<String, BigDecimal> net = new LinkedHashMap<>();
        participants.forEach(p -> net.put(p, Money.zero()));

        for (Expense e : expenses) {
            net.merge(e.paidBy(), e.amount(), BigDecimal::add);
            e.shares().forEach((p, share) -> net.merge(p, share.negate(), BigDecimal::add));
        }
        for (Payment p : payments) {
            net.merge(p.from(), p.amount(), BigDecimal::add);
            net.merge(p.to(), p.amount().negate(), BigDecimal::add);
        }
        return net;
    }

    /**
     * Greedy debt minimisation: repeatedly match the largest debtor with the largest creditor.
     * Produces at most (n - 1) transfers. Deterministic (ties broken by name).
     */
    public static List<Transfer> minimizeTransfers(Map<String, BigDecimal> balances) {
        Comparator<Node> byAmountDesc = Comparator.comparing((Node n) -> n.remaining).reversed()
                .thenComparing(n -> n.name);
        PriorityQueue<Node> creditors = new PriorityQueue<>(byAmountDesc);
        PriorityQueue<Node> debtors = new PriorityQueue<>(byAmountDesc);

        balances.forEach((name, balance) -> {
            if (balance.signum() > 0) {
                creditors.add(new Node(name, balance));
            } else if (balance.signum() < 0) {
                debtors.add(new Node(name, balance.negate()));
            }
        });

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Node creditor = creditors.poll();
            Node debtor = debtors.poll();
            BigDecimal amount = creditor.remaining.min(debtor.remaining);

            transfers.add(new Transfer(debtor.name, creditor.name, amount));

            creditor.remaining = creditor.remaining.subtract(amount);
            debtor.remaining = debtor.remaining.subtract(amount);
            if (creditor.remaining.signum() > 0) creditors.add(creditor);
            if (debtor.remaining.signum() > 0) debtors.add(debtor);
        }
        return transfers;
    }

    private static final class Node {
        final String name;
        BigDecimal remaining;

        Node(String name, BigDecimal remaining) {
            this.name = name;
            this.remaining = remaining;
        }
    }
}
