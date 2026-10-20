package com.splitbill.service;

import com.splitbill.domain.SplitType;
import com.splitbill.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SplitCalculatorTest {
    private final SplitCalculator calculator = new SplitCalculator();
    private final List<String> all = List.of("A", "B", "C");

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static BigDecimal sum(Map<String, BigDecimal> shares) {
        return shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void equalSplitDistributesLeftoverCentsWithoutLosingMoney() {
        Map<String, BigDecimal> shares = calculator.calculate(bd("100.00"), SplitType.EQUAL, all, Map.of());

        assertEquals(bd("33.34"), shares.get("A"));
        assertEquals(bd("33.33"), shares.get("B"));
        assertEquals(bd("33.33"), shares.get("C"));
        assertEquals(bd("100.00"), sum(shares));
    }

    @Test
    void equalSplitCanBeLimitedToSubsetOfParticipants() {
        Map<String, BigDecimal> inputs = new LinkedHashMap<>();
        inputs.put("A", null);
        inputs.put("B", null);

        Map<String, BigDecimal> shares = calculator.calculate(bd("10.00"), SplitType.EQUAL, all, inputs);

        assertEquals(2, shares.size());
        assertEquals(bd("5.00"), shares.get("A"));
    }

    @Test
    void percentageSplitSumsExactlyToTotal() {
        Map<String, BigDecimal> inputs = new LinkedHashMap<>();
        inputs.put("A", bd("33.33"));
        inputs.put("B", bd("33.33"));
        inputs.put("C", bd("33.34"));

        Map<String, BigDecimal> shares = calculator.calculate(bd("100.01"), SplitType.PERCENTAGE, all, inputs);

        assertEquals(bd("100.01"), sum(shares));
    }

    @Test
    void percentageMustSumTo100() {
        Map<String, BigDecimal> inputs = new LinkedHashMap<>();
        inputs.put("A", bd("50"));
        inputs.put("B", bd("40"));

        assertThrows(InvalidRequestException.class,
                () -> calculator.calculate(bd("10.00"), SplitType.PERCENTAGE, all, inputs));
    }

    @Test
    void exactAmountsMustMatchTotal() {
        Map<String, BigDecimal> ok = new LinkedHashMap<>();
        ok.put("A", bd("7.00"));
        ok.put("B", bd("3.00"));
        assertEquals(bd("10.00"), sum(calculator.calculate(bd("10.00"), SplitType.EXACT, all, ok)));

        Map<String, BigDecimal> bad = new LinkedHashMap<>();
        bad.put("A", bd("7.00"));
        bad.put("B", bd("2.00"));
        assertThrows(InvalidRequestException.class,
                () -> calculator.calculate(bd("10.00"), SplitType.EXACT, all, bad));
    }
}
