package com.pgms.repository;

import com.pgms.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    List<Tenant> findAllByActiveTrueOrderByCreatedAtDesc();
    @Query("select t from Tenant t where t.active = true and (t.dailyAccommodation = false or t.dailyAccommodation is null) order by t.createdAt desc")
    List<Tenant> findAllActiveRegularTenantsOrderByCreatedAtDesc();
    @Query("select t from Tenant t where t.active = true and t.dailyAccommodation = true order by t.createdAt desc")
    List<Tenant> findAllActiveDailyTenantsOrderByCreatedAtDesc();
    List<Tenant> findAllByJoiningDateBetween(LocalDate from, LocalDate to);
    @Query("select t from Tenant t where t.joiningDate between :from and :to and (t.dailyAccommodation = false or t.dailyAccommodation is null)")
    List<Tenant> findAllRegularByJoiningDateBetween(LocalDate from, LocalDate to);

    @Query("""
            select t from Tenant t
            where t.dailyAccommodation = true
              and coalesce(t.dailyCollectionAmount, 0) > 0
              and t.dailyCollectionTransactionDate between :from and :to
              and (:accountId is null or t.dailyCollectionAccount.id = :accountId)
            order by t.dailyCollectionTransactionDate desc, t.createdAt desc
            """)
    List<Tenant> findDailyCollectionsForReport(LocalDate from, LocalDate to, Long accountId);

    boolean existsByFullNameIgnoreCaseAndActiveTrue(String fullName);
    boolean existsByFullNameIgnoreCaseAndActiveTrueAndIdNot(String fullName, Long id);
    long countByActiveTrue();

    @Modifying
    @Query("update Tenant t set t.dailyCollectionAccount = null where t.dailyCollectionAccount.id = :accountId")
    int clearDailyCollectionAccountByAccountId(@Param("accountId") Long accountId);

    @Modifying
    @Query("update Tenant t set t.joiningCollectionAccount = null where t.joiningCollectionAccount.id = :accountId")
    int clearJoiningCollectionAccountByAccountId(@Param("accountId") Long accountId);
}
