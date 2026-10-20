package com.splitbill.domain;

public enum SplitType {
    /** Total divided evenly; leftover cents go to the first participants (deterministic). */
    EQUAL,
    /** Each participant's value is a percentage; percentages must sum to 100. */
    PERCENTAGE,
    /** Each participant's value is an exact amount; amounts must sum to the expense total. */
    EXACT
}
