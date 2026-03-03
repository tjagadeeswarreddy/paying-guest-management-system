package com.pgms.repository;

import com.pgms.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findAllByOrderByTransactionDateDescIdDesc();

    @Modifying
    @Query("update Expense e set e.account = null where e.account.id = :accountId")
    void clearAccountByAccountId(@Param("accountId") Long accountId);
}
