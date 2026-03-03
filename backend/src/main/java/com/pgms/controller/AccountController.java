package com.pgms.controller;

import com.pgms.config.CacheNames;
import com.pgms.dto.AccountRequest;
import com.pgms.dto.AccountResponse;
import com.pgms.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Cacheable(cacheNames = CacheNames.ACCOUNTS)
    public ResponseEntity<List<AccountResponse>> getAccounts() {
        return ResponseEntity.ok(accountService.getAccounts());
    }

    @PostMapping
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.ACCOUNTS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.EXPENSES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_DUE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true)
    })
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    @PutMapping("/{accountId}")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.ACCOUNTS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.EXPENSES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_DUE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true)
    })
    public ResponseEntity<AccountResponse> updateAccount(@PathVariable Long accountId, @Valid @RequestBody AccountRequest request) {
        return ResponseEntity.ok(accountService.updateAccount(accountId, request));
    }

    @DeleteMapping("/{accountId}")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.ACCOUNTS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.EXPENSES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_DUE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true)
    })
    public ResponseEntity<Void> deleteAccount(@PathVariable Long accountId) {
        accountService.deleteAccount(accountId);
        return ResponseEntity.noContent().build();
    }
}
