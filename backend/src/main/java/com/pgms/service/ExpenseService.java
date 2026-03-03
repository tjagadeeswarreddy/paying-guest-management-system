package com.pgms.service;

import com.pgms.dto.ExpenseRequest;
import com.pgms.dto.ExpenseResponse;

import java.util.List;

public interface ExpenseService {
    ExpenseResponse createExpense(ExpenseRequest request);
    ExpenseResponse updateExpense(Long expenseId, ExpenseRequest request);
    void deleteExpense(Long expenseId);
    List<ExpenseResponse> getExpenses();
}
