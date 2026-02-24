package com.pgms.repository;

import com.pgms.entity.CollectionRent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public interface CollectionRentRepository extends JpaRepository<CollectionRent, Long> {

    @Query("select c from CollectionRent c where c.collectedAt >= :fromTs and c.collectedAt < :toTs order by c.collectedAt desc")
    List<CollectionRent> findAllByCollectedAtBetweenOrderByCollectedAtDesc(
            @Param("fromTs") OffsetDateTime fromTs,
            @Param("toTs") OffsetDateTime toTs
    );

    @Query("""
            select c from CollectionRent c
            where c.collectedAt >= :fromTs and c.collectedAt < :toTs
              and (:accountId is null or c.account.id = :accountId)
            order by c.collectedAt desc
            """)
    List<CollectionRent> findAllByCollectedAtBetweenAndAccountOrderByCollectedAtDesc(
            @Param("fromTs") OffsetDateTime fromTs,
            @Param("toTs") OffsetDateTime toTs,
            @Param("accountId") Long accountId
    );

    @Query("select coalesce(sum(c.collectedAmount), 0) from CollectionRent c where c.collectedAt >= :fromTs and c.collectedAt < :toTs")
    BigDecimal sumCollectedBetween(@Param("fromTs") OffsetDateTime fromTs, @Param("toTs") OffsetDateTime toTs);

    @Modifying
    @Query("update CollectionRent c set c.account = null where c.account.id = :accountId")
    int clearAccountByAccountId(@Param("accountId") Long accountId);
}
