package com.pgms.repository;

import com.pgms.entity.DueRent;
import com.pgms.entity.RentRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DueRentRepository extends JpaRepository<DueRent, Long> {

    Optional<DueRent> findByTenant_IdAndBillingMonth(Long tenantId, LocalDate billingMonth);

    Optional<DueRent> findTopByTenant_IdOrderByBillingMonthDesc(Long tenantId);

    List<DueRent> findAllByStatusInAndBillingMonthBetweenOrderByBillingMonthDesc(
            List<RentRecordStatus> statuses,
            LocalDate from,
            LocalDate to
    );

    @Query("select coalesce(sum(d.dueAmount), 0) from DueRent d where d.billingMonth between :from and :to")
    BigDecimal sumDueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Modifying
    @Query("update DueRent d set d.account = null where d.account.id = :accountId")
    int clearAccountByAccountId(@Param("accountId") Long accountId);
}
