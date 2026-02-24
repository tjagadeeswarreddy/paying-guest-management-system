package com.pgms.service;

import com.pgms.dto.AccountRequest;
import com.pgms.dto.AccountResponse;

import java.util.List;

public interface AccountService {
    AccountResponse createAccount(AccountRequest request);
    AccountResponse updateAccount(Long accountId, AccountRequest request);
    void deleteAccount(Long accountId);
    List<AccountResponse> getAccounts();
}
