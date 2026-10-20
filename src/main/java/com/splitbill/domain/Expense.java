package com.splitbill.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Immutable record of an expense. {@code shares} is what each participant owes for it. */
public record Expense(
        String id,
        String description,
        BigDecimal amount,
        String paidBy,
        String category,
        SplitType splitType,
        Map<String, BigDecimal> shares,
        Instant createdAt) {
}
