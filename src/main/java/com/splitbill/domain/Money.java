package com.splitbill.domain;

import com.splitbill.exception.InvalidRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money helpers. All monetary values in the system use scale 2. */
public final class Money {
    public static final int SCALE = 2;

    private Money() {
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE);
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Accepts only values with at most 2 decimal places; never silently rounds user input. */
    public static BigDecimal exact(BigDecimal value, String field) {
        if (value == null) {
            throw new InvalidRequestException(field + " is required");
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new InvalidRequestException(field + " must have at most " + SCALE + " decimal places");
        }
        return value.setScale(SCALE);
    }
}
