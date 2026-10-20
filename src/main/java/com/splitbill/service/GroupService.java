package com.splitbill.service;

import com.splitbill.domain.BillGroup;
import com.splitbill.domain.Expense;
import com.splitbill.domain.Money;
import com.splitbill.domain.Payment;
import com.splitbill.domain.SplitType;
import com.splitbill.domain.Transfer;
import com.splitbill.dto.Dto.*;
import com.splitbill.exception.InvalidRequestException;
import com.splitbill.exception.NotFoundException;
import com.splitbill.repository.GroupRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class GroupService {
    private static final String DEFAULT_CATEGORY = "uncategorized";

    private final GroupRepository repository;
    private final SplitCalculator splitCalculator;
    private final ServiceChargeCalculator serviceCharge;

    public GroupService(GroupRepository repository, SplitCalculator splitCalculator,
                        ServiceChargeCalculator serviceCharge) {
        this.repository = repository;
        this.splitCalculator = splitCalculator;
        this.serviceCharge = serviceCharge;
    }

    public GroupResponse createGroup(CreateGroupRequest request) {
        List<String> names = request.participants().stream().map(String::trim).toList();
        Set<String> seen = new HashSet<>();
        for (String n : names) {
            if (!seen.add(n.toLowerCase(Locale.ROOT))) {
                throw new InvalidRequestException("Duplicate participant name: " + n);
            }
        }
        BillGroup group = repository.save(new BillGroup(UUID.randomUUID().toString(), request.name().trim(), names));
        return toResponse(group);
    }

    public GroupResponse getGroup(String id) {
        return toResponse(find(id));
    }

    public Expense addExpense(String groupId, AddExpenseRequest request) {
        BillGroup group = find(groupId);
        synchronized (group) {
            String payer = group.resolve(request.paidBy());
            BigDecimal amount = Money.exact(request.amount(), "amount");
            if (amount.signum() <= 0) {
                throw new InvalidRequestException("amount must be positive");
            }
            SplitType type = request.splitType() == null ? SplitType.EQUAL : request.splitType();

            Map<String, BigDecimal> inputs = new LinkedHashMap<>();
            if (request.splits() != null) {
                for (SplitEntry entry : request.splits()) {
                    String name = group.resolve(entry.participant());
                    if (inputs.containsKey(name)) {
                        throw new InvalidRequestException("Participant listed twice in splits: " + name);
                    }
                    inputs.put(name, entry.value());
                }
            }

            Map<String, BigDecimal> shares = splitCalculator.calculate(amount, type, group.getParticipants(), inputs);
            Expense expense = new Expense(UUID.randomUUID().toString(), request.description().trim(), amount, payer,
                    normalizeCategory(request.category()), type, Collections.unmodifiableMap(new LinkedHashMap<>(shares)), Instant.now());
            group.addExpense(expense);
            return expense;
        }
    }

    public List<Expense> listExpenses(String groupId) {
        BillGroup group = find(groupId);
        synchronized (group) {
            return new ArrayList<>(group.getExpenses());
        }
    }

    public Payment recordPayment(String groupId, RecordPaymentRequest request) {
        BillGroup group = find(groupId);
        synchronized (group) {
            String from = group.resolve(request.from());
            String to = group.resolve(request.to());
            if (from.equals(to)) {
                throw new InvalidRequestException("A participant cannot pay themselves");
            }
            BigDecimal amount = Money.exact(request.amount(), "amount");

            Map<String, BigDecimal> net = SettlementCalculator.balances(
                    group.getParticipants(), group.getExpenses(), group.getPayments());
            BigDecimal debt = net.get(from).negate();
            BigDecimal credit = net.get(to);
            if (debt.signum() <= 0) {
                throw new InvalidRequestException(from + " has no outstanding debt");
            }
            if (credit.signum() <= 0) {
                throw new InvalidRequestException(to + " is not owed anything");
            }
            if (amount.compareTo(debt.min(credit)) > 0) {
                throw new InvalidRequestException("Payment exceeds what can be settled between these two (max "
                        + debt.min(credit) + ")");
            }
            Payment payment = new Payment(UUID.randomUUID().toString(), from, to, amount, Instant.now());
            group.addPayment(payment);
            return payment;
        }
    }

    public SettlementResponse settlement(String groupId) {
        BillGroup group = find(groupId);
        synchronized (group) {
            List<String> participants = group.getParticipants();
            Map<String, BigDecimal> paid = zeroed(participants);
            Map<String, BigDecimal> owed = zeroed(participants);
            BigDecimal total = Money.zero();
            for (Expense e : group.getExpenses()) {
                total = total.add(e.amount());
                paid.merge(e.paidBy(), e.amount(), BigDecimal::add);
                e.shares().forEach((p, s) -> owed.merge(p, s, BigDecimal::add));
            }

            Map<String, BigDecimal> net = SettlementCalculator.balances(
                    participants, group.getExpenses(), group.getPayments());
            List<BalanceView> balances = participants.stream()
                    .map(p -> new BalanceView(p, paid.get(p), owed.get(p), net.get(p)))
                    .toList();
            List<Transfer> transfers = SettlementCalculator.minimizeTransfers(net);

            return new SettlementResponse(group.getId(), group.getName(), total,
                    serviceCharge.percentage(), serviceCharge.amountFor(total),
                    transfers.isEmpty(), balances, transfers);
        }
    }

    public List<CategorySummary> categorySummary(String groupId) {
        BillGroup group = find(groupId);
        synchronized (group) {
            Map<String, BigDecimal> totals = new LinkedHashMap<>();
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Expense e : group.getExpenses()) {
                totals.merge(e.category(), e.amount(), BigDecimal::add);
                counts.merge(e.category(), 1, Integer::sum);
            }
            return totals.entrySet().stream()
                    .map(en -> new CategorySummary(en.getKey(), en.getValue(), counts.get(en.getKey())))
                    .toList();
        }
    }

    // ---- helpers ----
    private BillGroup find(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Group not found: " + id));
    }

    private GroupResponse toResponse(BillGroup g) {
        return new GroupResponse(g.getId(), g.getName(), g.getParticipants(), g.getCreatedAt());
    }

    private static String normalizeCategory(String category) {
        return (category == null || category.isBlank()) ? DEFAULT_CATEGORY : category.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, BigDecimal> zeroed(List<String> names) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        names.forEach(n -> m.put(n, Money.zero()));
        return m;
    }
}
