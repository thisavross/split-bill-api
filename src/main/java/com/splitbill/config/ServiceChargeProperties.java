package com.splitbill.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "splitbill")
@Validated
public record ServiceChargeProperties(@NotBlank String githubUsername) {
}
