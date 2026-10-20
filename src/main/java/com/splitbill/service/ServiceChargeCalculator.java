package com.splitbill.service;

import com.splitbill.config.ServiceChargeProperties;
import com.splitbill.domain.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** service_charge_pct = (sum of char codes of lowercase GitHub username) % 10. */
@Component
public class ServiceChargeCalculator {
    private final int percentage;

    public ServiceChargeCalculator(ServiceChargeProperties properties) {
        this.percentage = percentageFor(properties.githubUsername());
    }

    public static int percentageFor(String githubUsername) {
        int sum = githubUsername.trim().toLowerCase(Locale.ROOT).chars().sum();
        return sum % 10;
    }

    public int percentage() {
        return percentage;
    }

    public BigDecimal amountFor(BigDecimal totalExpenses) {
        return totalExpenses
                .multiply(BigDecimal.valueOf(percentage))
                .divide(BigDecimal.valueOf(100), Money.SCALE, RoundingMode.HALF_UP);
    }
}
