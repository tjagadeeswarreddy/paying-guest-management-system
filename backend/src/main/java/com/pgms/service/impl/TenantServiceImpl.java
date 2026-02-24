package com.pgms.service.impl;

import com.pgms.dto.TenantRequest;
import com.pgms.dto.TenantResponse;
import com.pgms.entity.CollectionRent;
import com.pgms.entity.DueRent;
import com.pgms.entity.PaymentStatus;
import com.pgms.entity.RentRecordStatus;
import com.pgms.entity.Tenant;
import com.pgms.exception.BadRequestException;
import com.pgms.exception.ResourceNotFoundException;
import com.pgms.mapper.TenantMapper;
import com.pgms.repository.AccountRepository;
import com.pgms.repository.CollectionRentRepository;
import com.pgms.repository.DueRentRepository;
import com.pgms.repository.TenantRepository;
import com.pgms.service.TenantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
@Transactional
public class TenantServiceImpl implements TenantService {

    private final TenantRepository tenantRepository;
    private final DueRentRepository dueRentRepository;
    private final CollectionRentRepository collectionRentRepository;
    private final AccountRepository accountRepository;

    public TenantServiceImpl(
            TenantRepository tenantRepository,
            DueRentRepository dueRentRepository,
            CollectionRentRepository collectionRentRepository,
            AccountRepository accountRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.dueRentRepository = dueRentRepository;
        this.collectionRentRepository = collectionRentRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public TenantResponse createTenant(TenantRequest request) {
        Tenant tenant = TenantMapper.toEntity(request);
        resolveDailyCollectionAccount(tenant, request.getDailyCollectionAccountId());
        resolveJoiningCollectionAccount(tenant, request.getJoiningCollectionAccountId());
        normalizeIdentityAndContacts(tenant);
        validateUniqueName(tenant.getFullName(), null);
        normalizeFinancials(tenant);
        Tenant saved = tenantRepository.save(tenant);
        syncJoiningLedger(saved);
        return TenantMapper.toResponse(saved);
    }

    @Override
    public TenantResponse updateTenant(Long tenantId, TenantRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));

        TenantMapper.updateEntity(tenant, request);
        resolveDailyCollectionAccount(tenant, request.getDailyCollectionAccountId());
        resolveJoiningCollectionAccount(tenant, request.getJoiningCollectionAccountId());
        normalizeIdentityAndContacts(tenant);
        validateUniqueName(tenant.getFullName(), tenantId);
        normalizeFinancials(tenant);
        Tenant saved = tenantRepository.save(tenant);
        syncJoiningLedger(saved);
        return TenantMapper.toResponse(saved);
    }

    @Override
    public TenantResponse checkoutTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));
        tenant.setActive(false);
        tenant.setCheckoutDate(LocalDate.now());
        Tenant saved = tenantRepository.save(tenant);
        return TenantMapper.toResponse(saved);
    }

    @Override
    public TenantResponse clearDailyCollection(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));
        if (!tenant.isDailyAccommodation()) {
            throw new BadRequestException("Daily collection can be deleted only for daily accommodation tenants.");
        }
        tenant.setDailyCollectionAmount(BigDecimal.ZERO);
        tenant.setDailyCollectionTransactionDate(null);
        tenant.setDailyCollectionAccount(null);
        Tenant saved = tenantRepository.save(tenant);
        return TenantMapper.toResponse(saved);
    }

    @Override
    public void deleteTenant(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));
        tenant.setActive(false);
        tenantRepository.save(tenant);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantResponse> getActiveTenants() {
        return tenantRepository.findAllActiveRegularTenantsOrderByCreatedAtDesc()
                .stream()
                .map(TenantMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantResponse> getDailyTenants() {
        return tenantRepository.findAllActiveDailyTenantsOrderByCreatedAtDesc()
                .stream()
                .map(TenantMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantResponse> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(TenantMapper::toResponse)
                .toList();
    }

    private void normalizeFinancials(Tenant tenant) {
        if (tenant.isDailyAccommodation()) {
            BigDecimal dailyCollection = nvl(tenant.getDailyCollectionAmount()).max(BigDecimal.ZERO);
            tenant.setDailyCollectionAmount(dailyCollection);
            if (dailyCollection.compareTo(BigDecimal.ZERO) > 0) {
                tenant.setDailyCollectionTransactionDate(
                        tenant.getDailyCollectionTransactionDate() != null
                                ? tenant.getDailyCollectionTransactionDate()
                                : (tenant.getJoiningDate() != null ? tenant.getJoiningDate() : LocalDate.now())
                );
            } else {
                tenant.setDailyCollectionTransactionDate(null);
                tenant.setDailyCollectionAccount(null);
            }
            tenant.setDailyStayDays(tenant.getDailyStayDays() == null ? 1 : Math.max(tenant.getDailyStayDays(), 1));
            tenant.setDeposit(BigDecimal.ZERO);
            tenant.setDepositPaidAmount(BigDecimal.ZERO);
            tenant.setRentPaidAmount(BigDecimal.ZERO);
            tenant.setRentDueAmount(BigDecimal.ZERO);
            tenant.setPaymentStatus(PaymentStatus.ON_TIME);
            return;
        }

        BigDecimal rent = nvl(tenant.getRent());
        BigDecimal deposit = nvl(tenant.getDeposit());
        BigDecimal rentPaid = nvl(tenant.getRentPaidAmount()).min(rent).max(BigDecimal.ZERO);
        BigDecimal depositPaid = nvl(tenant.getDepositPaidAmount()).min(deposit).max(BigDecimal.ZERO);
        BigDecimal rentDue = rent.subtract(rentPaid).max(BigDecimal.ZERO);

        tenant.setRent(rent);
        tenant.setDeposit(deposit);
        tenant.setRentPaidAmount(rentPaid);
        tenant.setDepositPaidAmount(depositPaid);
        if (rentPaid.add(depositPaid).compareTo(BigDecimal.ZERO) <= 0) {
            tenant.setJoiningCollectionAccount(null);
        }
        tenant.setRentDueAmount(rentDue);
        tenant.setDailyStayDays(null);
        tenant.setPaymentStatus(rentDue.compareTo(BigDecimal.ZERO) == 0
                ? PaymentStatus.ON_TIME
                : (rentPaid.compareTo(BigDecimal.ZERO) > 0 ? PaymentStatus.PARTIAL : PaymentStatus.DUE));
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void normalizeIdentityAndContacts(Tenant tenant) {
        tenant.setFullName(trimToNull(tenant.getFullName()));
        tenant.setTenantPhoneNumber(trimToNull(tenant.getTenantPhoneNumber()));
        tenant.setEmergencyContactRelationship(trimToEmpty(tenant.getEmergencyContactRelationship()));
        tenant.setEmergencyContactNumber(trimToEmpty(tenant.getEmergencyContactNumber()));
        if (!tenant.isDailyAccommodation()) {
            tenant.setDailyFoodOption(null);
            tenant.setDailyCollectionAmount(BigDecimal.ZERO);
            tenant.setDailyCollectionTransactionDate(null);
            tenant.setDailyCollectionAccount(null);
        }
        if (tenant.isDailyAccommodation()) {
            tenant.setJoiningCollectionAccount(null);
        }
        if (tenant.isActive()) {
            tenant.setCheckoutDate(null);
        }
    }

    private void resolveDailyCollectionAccount(Tenant tenant, Long accountId) {
        if (accountId == null) {
            tenant.setDailyCollectionAccount(null);
            return;
        }
        tenant.setDailyCollectionAccount(
                accountRepository.findById(accountId)
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId))
        );
    }

    private void resolveJoiningCollectionAccount(Tenant tenant, Long accountId) {
        if (accountId == null) {
            tenant.setJoiningCollectionAccount(null);
            return;
        }
        tenant.setJoiningCollectionAccount(
                accountRepository.findById(accountId)
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId))
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private void validateUniqueName(String fullName, Long currentTenantId) {
        if (fullName == null) {
            throw new BadRequestException("Tenant full name is required.");
        }
        boolean exists = currentTenantId == null
                ? tenantRepository.existsByFullNameIgnoreCaseAndActiveTrue(fullName)
                : tenantRepository.existsByFullNameIgnoreCaseAndActiveTrueAndIdNot(fullName, currentTenantId);
        if (exists) {
            throw new BadRequestException("Tenant name already exists. Use a unique full name.");
        }
    }

    private void syncJoiningLedger(Tenant tenant) {
        if (tenant.getJoiningDate() == null || tenant.isDailyAccommodation()) {
            return;
        }

        var billingMonth = YearMonth.from(tenant.getJoiningDate()).atDay(1);
        var totalDueAmount = nvl(tenant.getRent()).add(nvl(tenant.getDeposit()));
        var totalPaidAmount = nvl(tenant.getRentPaidAmount()).add(nvl(tenant.getDepositPaidAmount()))
                .min(totalDueAmount)
                .max(BigDecimal.ZERO);

        DueRent dueRent = dueRentRepository.findByTenant_IdAndBillingMonth(tenant.getId(), billingMonth)
                .orElseGet(DueRent::new);
        dueRent.setTenant(tenant);
        dueRent.setBillingMonth(billingMonth);
        dueRent.setDueAmount(totalDueAmount);
        dueRent.setPaidAmount(totalPaidAmount);
        dueRent.setAccount(tenant.getJoiningCollectionAccount());
        dueRent.setStatus(resolveStatus(totalDueAmount, totalPaidAmount));
        DueRent saved = dueRentRepository.save(dueRent);

        if (totalPaidAmount.compareTo(BigDecimal.ZERO) > 0) {
            CollectionRent collection = collectionRentRepository.findById(saved.getId()).orElseGet(CollectionRent::new);
            collection.setDueRent(saved);
            collection.setTenant(tenant);
            collection.setBillingMonth(billingMonth);
            collection.setCollectedAmount(totalPaidAmount);
            collection.setAccount(tenant.getJoiningCollectionAccount());
            collection.setCollectedAt(OffsetDateTime.now());
            collectionRentRepository.save(collection);
        } else {
            collectionRentRepository.findById(saved.getId()).ifPresent(collectionRentRepository::delete);
        }
    }

    private RentRecordStatus resolveStatus(BigDecimal dueAmount, BigDecimal paidAmount) {
        if (paidAmount.compareTo(dueAmount) >= 0) {
            return RentRecordStatus.PAID;
        }
        if (paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return RentRecordStatus.DUE;
        }
        return RentRecordStatus.PARTIAL;
    }
}
