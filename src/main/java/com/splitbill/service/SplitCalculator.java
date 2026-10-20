package com.splitbill.service;

import com.splitbill.domain.Money;
import com.splitbill.domain.SplitType;
import com.splitbill.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an expense total into per-participant shares. Guarantees that the shares
 * always sum exactly to the total (no lost or invented cents).
 */
@Component
public class SplitCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

    /**
     * @param total           expense total (scale 2, positive)
     * @param type            split strategy
     * @param allParticipants every participant in the group (default set for EQUAL)
     * @param inputs          canonical participant name -> value (percentage / amount); may be empty for EQUAL
     */
    public Map<String, BigDecimal> calculate(BigDecimal total, SplitType type,
                                             List<String> allParticipants, Map<String, BigDecimal> inputs) {
        return switch (type) {
            case EQUAL -> equal(total, inputs.isEmpty() ? allParticipants : new ArrayList<>(inputs.keySet()));
            case PERCENTAGE -> percentage(total, inputs);
            case EXACT -> exact(total, inputs);
        };
    }

    private Map<String, BigDecimal> equal(BigDecimal total, List<String> names) {
        int n = names.size();
        BigDecimal base = total.divide(BigDecimal.valueOf(n), Money.SCALE, RoundingMode.DOWN);
        BigDecimal remainder = total.subtract(base.multiply(BigDecimal.valueOf(n)));
        int extraCents = remainder.movePointRight(Money.SCALE).intValueExact();

        Map<String, BigDecimal> shares = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            shares.put(names.get(i), i < extraCents ? base.add(ONE_CENT) : base);
        }
        return shares;
    }

    private Map<String, BigDecimal> percentage(BigDecimal total, Map<String, BigDecimal> inputs) {
        requireInputs(inputs, "PERCENTAGE");
        BigDecimal pctSum = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : inputs.entrySet()) {
            BigDecimal pct = requireValue(e);
            if (pct.signum() <= 0 || pct.compareTo(HUNDRED) > 0) {
                throw new InvalidRequestException("Percentage for " + e.getKey() + " must be in (0, 100]");
            }
            pctSum = pctSum.add(pct);
        }
        if (pctSum.compareTo(HUNDRED) != 0) {
            throw new InvalidRequestException("Percentages must sum to 100 but sum to " + pctSum.stripTrailingZeros().toPlainString());
        }

        Map<String, BigDecimal> shares = new LinkedHashMap<>();
        BigDecimal assigned = Money.zero();
        for (Map.Entry<String, BigDecimal> e : inputs.entrySet()) {
            BigDecimal share = total.multiply(e.getValue()).divide(HUNDRED, Money.SCALE, RoundingMode.HALF_UP);
            shares.put(e.getKey(), share);
            assigned = assigned.add(share);
        }
        // Absorb rounding drift into the last participant so the shares sum exactly to the total.
        BigDecimal drift = total.subtract(assigned);
        if (drift.signum() != 0) {
            String last = new ArrayList<>(shares.keySet()).get(shares.size() - 1);
            shares.put(last, shares.get(last).add(drift));
        }
        return shares;
    }

    private Map<String, BigDecimal> exact(BigDecimal total, Map<String, BigDecimal> inputs) {
        requireInputs(inputs, "EXACT");
        Map<String, BigDecimal> shares = new LinkedHashMap<>();
        BigDecimal sum = Money.zero();
        for (Map.Entry<String, BigDecimal> e : inputs.entrySet()) {
            BigDecimal amount = Money.exact(requireValue(e), "value for " + e.getKey());
            if (amount.signum() < 0) {
                throw new InvalidRequestException("Amount for " + e.getKey() + " cannot be negative");
            }
            shares.put(e.getKey(), amount);
            sum = sum.add(amount);
        }
        if (sum.compareTo(total) != 0) {
            throw new InvalidRequestException("Exact amounts sum to " + sum + " but the expense total is " + total);
        }
        return shares;
    }

    private void requireInputs(Map<String, BigDecimal> inputs, String type) {
        if (inputs.isEmpty()) {
            throw new InvalidRequestException("'splits' is required for split_type " + type);
        }
    }

    private BigDecimal requireValue(Map.Entry<String, BigDecimal> e) {
        if (e.getValue() == null) {
            throw new InvalidRequestException("'value' is required for " + e.getKey());
        }
        return e.getValue();
    }
}
