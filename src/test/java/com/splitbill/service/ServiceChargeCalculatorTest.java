package com.splitbill.service;

import com.splitbill.config.ServiceChargeProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceChargeCalculatorTest {

    @Test
    void matchesWorkedExampleFromSpec() {
        // johndoe47 -> sum 850 -> 850 % 10 = 0
        assertEquals(0, ServiceChargeCalculator.percentageFor("johndoe47"));
    }

    @Test
    void isCaseInsensitive() {
        assertEquals(ServiceChargeCalculator.percentageFor("johndoe47"),
                ServiceChargeCalculator.percentageFor("JohnDoe47"));
    }

    @Test
    void appliesPercentageToTotal() {
        // "a" = 97 -> 7%
        ServiceChargeCalculator calc = new ServiceChargeCalculator(new ServiceChargeProperties("a"));
        assertEquals(7, calc.percentage());
        assertEquals(new BigDecimal("7.00"), calc.amountFor(new BigDecimal("100.00")));
        assertEquals(new BigDecimal("0.07"), calc.amountFor(new BigDecimal("1.00")));
    }
}
