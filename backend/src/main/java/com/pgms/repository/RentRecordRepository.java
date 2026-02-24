package com.pgms.repository;

import com.pgms.entity.RentRecord;
import com.pgms.entity.RentRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RentRecordRepository extends JpaRepository<RentRecord, Long> {

    Optional<RentRecord> findByTenant_IdAndBillingMonth(Long tenantId, LocalDate billingMonth);

    Optional<RentRecord> findTopByTenant_IdOrderByBillingMonthDesc(Long tenantId);

    List<RentRecord> findAllByStatusInAndBillingMonthBetweenOrderByBillingMonthDesc(
            List<RentRecordStatus> statuses,
            LocalDate from,
            LocalDate to
    );

    List<RentRecord> findAllByStatusAndBillingMonthBetweenOrderByBillingMonthDesc(
            RentRecordStatus status,
            LocalDate from,
            LocalDate to
    );

    @Query("select coalesce(sum(r.paidAmount), 0) from RentRecord r where r.billingMonth between :from and :to")
    BigDecimal sumPaidBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(r.dueAmount), 0) from RentRecord r where r.billingMonth between :from and :to")
    BigDecimal sumDueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
