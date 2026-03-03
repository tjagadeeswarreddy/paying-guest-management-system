package com.pgms.service.impl;

import com.pgms.dto.AccountRequest;
import com.pgms.dto.AccountResponse;
import com.pgms.entity.Account;
import com.pgms.exception.BadRequestException;
import com.pgms.exception.ResourceNotFoundException;
import com.pgms.repository.AccountRepository;
import com.pgms.repository.CollectionRentRepository;
import com.pgms.repository.DueRentRepository;
import com.pgms.repository.ExpenseRepository;
import com.pgms.repository.TenantRepository;
import com.pgms.service.AccountService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.data-provider", havingValue = "postgres", matchIfMissing = true)
@Transactional
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final DueRentRepository dueRentRepository;
    private final CollectionRentRepository collectionRentRepository;
    private final TenantRepository tenantRepository;
    private final ExpenseRepository expenseRepository;

    public AccountServiceImpl(
            AccountRepository accountRepository,
            DueRentRepository dueRentRepository,
            CollectionRentRepository collectionRentRepository,
            TenantRepository tenantRepository,
            ExpenseRepository expenseRepository
    ) {
        this.accountRepository = accountRepository;
        this.dueRentRepository = dueRentRepository;
        this.collectionRentRepository = collectionRentRepository;
        this.tenantRepository = tenantRepository;
        this.expenseRepository = expenseRepository;
    }

    @Override
    public AccountResponse createAccount(AccountRequest request) {
        String name = normalize(request.getName());
        String mode = normalize(request.getMode());
        if (accountRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("Account already exists: " + name);
        }
        Account account = new Account();
        account.setName(name);
        account.setMode(mode);
        return toResponse(accountRepository.save(account));
    }

    @Override
    public AccountResponse updateAccount(Long accountId, AccountRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        String name = normalize(request.getName());
        String mode = normalize(request.getMode());
        if (accountRepository.existsByNameIgnoreCaseAndIdNot(name, accountId)) {
            throw new BadRequestException("Account already exists: " + name);
        }
        account.setName(name);
        account.setMode(mode);
        return toResponse(accountRepository.save(account));
    }

    @Override
    public void deleteAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        dueRentRepository.clearAccountByAccountId(accountId);
        collectionRentRepository.clearAccountByAccountId(accountId);
        tenantRepository.clearDailyCollectionAccountByAccountId(accountId);
        tenantRepository.clearJoiningCollectionAccountByAccountId(accountId);
        expenseRepository.clearAccountByAccountId(accountId);
        accountRepository.delete(account);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccounts() {
        return accountRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private AccountResponse toResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setName(account.getName());
        response.setMode(account.getMode());
        return response;
    }
}
