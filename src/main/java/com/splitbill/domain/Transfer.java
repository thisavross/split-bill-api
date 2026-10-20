package com.splitbill.domain;

import java.math.BigDecimal;

/** A suggested transfer: {@code from} should pay {@code to} the given amount. */
public record Transfer(String from, String to, BigDecimal amount) {
}
