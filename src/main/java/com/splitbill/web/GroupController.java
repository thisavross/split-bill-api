package com.splitbill.web;

import com.splitbill.domain.Expense;
import com.splitbill.domain.Payment;
import com.splitbill.dto.Dto.*;
import com.splitbill.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {
    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(@Valid @RequestBody CreateGroupRequest request) {
        return service.createGroup(request);
    }

    @GetMapping("/{groupId}")
    public GroupResponse get(@PathVariable String groupId) {
        return service.getGroup(groupId);
    }

    @PostMapping("/{groupId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public Expense addExpense(@PathVariable String groupId, @Valid @RequestBody AddExpenseRequest request) {
        return service.addExpense(groupId, request);
    }

    @GetMapping("/{groupId}/expenses")
    public List<Expense> listExpenses(@PathVariable String groupId) {
        return service.listExpenses(groupId);
    }

    @PostMapping("/{groupId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public Payment recordPayment(@PathVariable String groupId, @Valid @RequestBody RecordPaymentRequest request) {
        return service.recordPayment(groupId, request);
    }

    @GetMapping("/{groupId}/settlement")
    public SettlementResponse settlement(@PathVariable String groupId) {
        return service.settlement(groupId);
    }

    @GetMapping("/{groupId}/categories")
    public List<CategorySummary> categories(@PathVariable String groupId) {
        return service.categorySummary(groupId);
    }
}
