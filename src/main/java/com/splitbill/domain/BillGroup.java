package com.splitbill.domain;

import com.splitbill.exception.InvalidRequestException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root: a group with its participants, expenses and recorded payments.
 * Not thread-safe by itself; callers synchronize on the instance.
 */
public class BillGroup {
    private final String id;
    private final String name;
    private final Instant createdAt;
    private final List<String> participants;
    private final List<Expense> expenses = new ArrayList<>();
    private final List<Payment> payments = new ArrayList<>();

    public BillGroup(String id, String name, List<String> participants) {
        this.id = id;
        this.name = name;
        this.createdAt = Instant.now();
        this.participants = List.copyOf(participants);
    }

    /** Resolves a participant name case-insensitively to its canonical spelling. */
    public String resolve(String name) {
        if (name != null) {
            String wanted = name.trim();
            for (String p : participants) {
                if (p.equalsIgnoreCase(wanted)) {
                    return p;
                }
            }
        }
        throw new InvalidRequestException("Unknown participant '" + name + "' in this group");
    }

    public void addExpense(Expense e) {
        expenses.add(e);
    }

    public void addPayment(Payment p) {
        payments.add(p);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public List<String> getParticipants() { return participants; }
    public List<Expense> getExpenses() { return Collections.unmodifiableList(expenses); }
    public List<Payment> getPayments() { return Collections.unmodifiableList(payments); }
}
