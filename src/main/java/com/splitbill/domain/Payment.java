package com.splitbill.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** A settlement payment made by {@code from} to {@code to}. */
public record Payment(String id, String from, String to, BigDecimal amount, Instant createdAt) {
}
