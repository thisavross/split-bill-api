package com.splitbill.service;

import com.splitbill.domain.Expense;
import com.splitbill.domain.Payment;
import com.splitbill.domain.SplitType;
import com.splitbill.domain.Transfer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementCalculatorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static Expense expense(String payer, String amount, Map<String, BigDecimal> shares) {
        return new Expense("e", "x", bd(amount), payer, "food", SplitType.EXACT, shares, Instant.now());
    }

    private static final List<String> PEOPLE = List.of("asep", "beni", "siti");

    private List<Expense> tripExpenses() {
        // asep pays 90 split 3 ways; beni pays 30 split between asep and beni.
        return List.of(
                expense("asep", "90.00", Map.of("asep", bd("30.00"), "beni", bd("30.00"), "siti", bd("30.00"))),
                expense("beni", "30.00", Map.of("asep", bd("15.00"), "beni", bd("15.00"))));
    }

    @Test
    void computesNetBalances() {
        Map<String, BigDecimal> net = SettlementCalculator.balances(PEOPLE, tripExpenses(), List.of());

        assertEquals(bd("45.00"), net.get("asep"));
        assertEquals(bd("-15.00"), net.get("beni"));
        assertEquals(bd("-30.00"), net.get("siti"));
    }

    @Test
    void balancesAlwaysSumToZero() {
        Map<String, BigDecimal> net = SettlementCalculator.balances(PEOPLE, tripExpenses(), List.of());
        BigDecimal sum = net.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.signum());
    }

    @Test
    void producesMinimalTransfers() {
        Map<String, BigDecimal> net = SettlementCalculator.balances(PEOPLE, tripExpenses(), List.of());
        List<Transfer> transfers = SettlementCalculator.minimizeTransfers(net);

        assertEquals(2, transfers.size());
        assertTrue(transfers.contains(new Transfer("siti", "asep", bd("30.00"))));
        assertTrue(transfers.contains(new Transfer("beni", "asep", bd("15.00"))));
    }

    @Test
    void collapsesChainOfDebtsIntoSingleTransfer() {
        // A owes B 10, B owes C 10 -> A should simply pay C 10.
        List<String> people = List.of("A", "B", "C");
        List<Expense> expenses = List.of(
                expense("B", "10.00", Map.of("A", bd("10.00"))),
                expense("C", "10.00", Map.of("B", bd("10.00"))));

        List<Transfer> transfers = SettlementCalculator.minimizeTransfers(
                SettlementCalculator.balances(people, expenses, List.of()));

        assertEquals(List.of(new Transfer("A", "C", bd("10.00"))), transfers);
    }

    @Test
    void recordedPaymentsReduceOutstandingBalances() {
        Payment payment = new Payment("p", "siti", "asep", bd("30.00"), Instant.now());
        Map<String, BigDecimal> net = SettlementCalculator.balances(PEOPLE, tripExpenses(), List.of(payment));

        assertEquals(bd("15.00"), net.get("asep"));
        assertEquals(bd("0.00"), net.get("siti"));

        List<Transfer> transfers = SettlementCalculator.minimizeTransfers(net);
        assertEquals(List.of(new Transfer("beni", "asep", bd("15.00"))), transfers);
    }

    @Test
    void noTransfersWhenEveryoneIsSettled() {
        Map<String, BigDecimal> net = new LinkedHashMap<>();
        net.put("A", bd("0.00"));
        net.put("B", bd("0.00"));
        assertTrue(SettlementCalculator.minimizeTransfers(net).isEmpty());
    }
}
