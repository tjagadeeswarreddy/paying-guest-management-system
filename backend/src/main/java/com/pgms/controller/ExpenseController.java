package com.pgms.controller;

import com.pgms.config.CacheNames;
import com.pgms.dto.ExpenseRequest;
import com.pgms.dto.ExpenseResponse;
import com.pgms.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    @Cacheable(cacheNames = CacheNames.EXPENSES)
    public ResponseEntity<List<ExpenseResponse>> getExpenses() {
        return ResponseEntity.ok(expenseService.getExpenses());
    }

    @PostMapping
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.EXPENSES, allEntries = true)
    })
    public ResponseEntity<ExpenseResponse> createExpense(@Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.createExpense(request));
    }

    @PutMapping("/{expenseId}")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.EXPENSES, allEntries = true)
    })
    public ResponseEntity<ExpenseResponse> updateExpense(
            @PathVariable Long expenseId,
            @Valid @RequestBody ExpenseRequest request
    ) {
        return ResponseEntity.ok(expenseService.updateExpense(expenseId, request));
    }

    @DeleteMapping("/{expenseId}")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.EXPENSES, allEntries = true)
    })
    public ResponseEntity<Void> deleteExpense(@PathVariable Long expenseId) {
        expenseService.deleteExpense(expenseId);
        return ResponseEntity.noContent().build();
    }
}
