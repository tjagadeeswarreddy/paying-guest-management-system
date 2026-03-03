package com.pgms.service.impl;

import com.pgms.dto.ExpenseRequest;
import com.pgms.dto.ExpenseResponse;
import com.pgms.entity.Account;
import com.pgms.entity.Expense;
import com.pgms.exception.ResourceNotFoundException;
import com.pgms.repository.AccountRepository;
import com.pgms.repository.ExpenseRepository;
import com.pgms.service.ExpenseService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.data-provider", havingValue = "postgres", matchIfMissing = true)
@Transactional
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final AccountRepository accountRepository;

    public ExpenseServiceImpl(ExpenseRepository expenseRepository, AccountRepository accountRepository) {
        this.expenseRepository = expenseRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public ExpenseResponse createExpense(ExpenseRequest request) {
        Expense expense = new Expense();
        applyRequest(expense, request);
        return toResponse(expenseRepository.save(expense));
    }

    @Override
    public ExpenseResponse updateExpense(Long expenseId, ExpenseRequest request) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + expenseId));
        applyRequest(expense, request);
        return toResponse(expenseRepository.save(expense));
    }

    @Override
    public void deleteExpense(Long expenseId) {
        if (!expenseRepository.existsById(expenseId)) {
            throw new ResourceNotFoundException("Expense not found with id: " + expenseId);
        }
        expenseRepository.deleteById(expenseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses() {
        return expenseRepository.findAllByOrderByTransactionDateDescIdDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void applyRequest(Expense expense, ExpenseRequest request) {
        expense.setDescription((request.getDescription() == null ? "" : request.getDescription()).trim());
        expense.setAmount((request.getAmount() == null ? BigDecimal.ZERO : request.getAmount()).max(BigDecimal.ZERO));
        expense.setTransactionDate(request.getTransactionDate());
        expense.setTag(request.getTag() == null ? null : request.getTag().trim());
        expense.setAccount(resolveAccount(request.getAccountId()));
    }

    private Account resolveAccount(Long accountId) {
        if (accountId == null) return null;
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
    }

    private ExpenseResponse toResponse(Expense expense) {
        ExpenseResponse response = new ExpenseResponse();
        response.setId(expense.getId());
        response.setDescription(expense.getDescription());
        response.setAmount(expense.getAmount());
        response.setTransactionDate(expense.getTransactionDate());
        response.setTag(expense.getTag());
        response.setAccountId(expense.getAccount() != null ? expense.getAccount().getId() : null);
        response.setAccountName(expense.getAccount() != null ? expense.getAccount().getName() : null);
        response.setCreatedAt(expense.getCreatedAt());
        response.setUpdatedAt(expense.getUpdatedAt());
        return response;
    }
}
