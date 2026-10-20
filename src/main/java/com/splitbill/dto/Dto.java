package com.splitbill.dto;

import com.splitbill.domain.SplitType;
import com.splitbill.domain.Transfer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Request / response payloads. JSON uses snake_case (configured globally). */
public final class Dto {
    private Dto() {
    }

    // ---- requests ----
    public record CreateGroupRequest(
            @NotBlank String name,
            @NotNull @Size(min = 2, message = "a group needs at least 2 participants") List<@NotBlank String> participants) {
    }

    public record SplitEntry(@NotBlank String participant, BigDecimal value) {
    }

    public record AddExpenseRequest(
            @NotBlank String description,
            @NotNull @DecimalMin(value = "0.01", message = "must be at least 0.01") BigDecimal amount,
            @NotBlank String paidBy,
            String category,
            SplitType splitType,
            @Valid List<SplitEntry> splits) {
    }

    public record RecordPaymentRequest(
            @NotBlank String from,
            @NotBlank String to,
            @NotNull @DecimalMin(value = "0.01", message = "must be at least 0.01") BigDecimal amount) {
    }

    // ---- responses ----
    public record GroupResponse(String id, String name, List<String> participants, Instant createdAt) {
    }

    public record BalanceView(String participant, BigDecimal totalPaid, BigDecimal totalOwed, BigDecimal netBalance) {
    }

    public record SettlementResponse(
            String groupId,
            String groupName,
            BigDecimal totalExpenses,
            int serviceChargePct,
            BigDecimal serviceChargeAmount,
            boolean fullySettled,
            List<BalanceView> balances,
            List<Transfer> transfers) {
    }

    public record CategorySummary(String category, BigDecimal total, int expenseCount) {
    }
}
