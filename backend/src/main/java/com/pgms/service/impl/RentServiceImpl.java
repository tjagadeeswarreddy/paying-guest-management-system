package com.pgms.service.impl;

import com.pgms.dto.DashboardSummaryResponse;
import com.pgms.dto.RentRecordRequest;
import com.pgms.dto.RentRecordResponse;
import com.pgms.dto.RentRecordUpdateRequest;
import com.pgms.dto.RentTransactionResponse;
import com.pgms.entity.CollectionRent;
import com.pgms.entity.DueRent;
import com.pgms.entity.Account;
import com.pgms.entity.PaymentStatus;
import com.pgms.entity.RentRecordStatus;
import com.pgms.entity.Tenant;
import com.pgms.exception.ResourceNotFoundException;
import com.pgms.repository.AccountRepository;
import com.pgms.repository.CollectionRentRepository;
import com.pgms.repository.DueRentRepository;
import com.pgms.repository.TenantRepository;
import com.pgms.service.RentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "app.data-provider", havingValue = "postgres", matchIfMissing = true)
@Transactional
public class RentServiceImpl implements RentService {

    private final DueRentRepository dueRentRepository;
    private final CollectionRentRepository collectionRentRepository;
    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private volatile LocalDate lastDueGenerationRunDate;

    public RentServiceImpl(
            DueRentRepository dueRentRepository,
            CollectionRentRepository collectionRentRepository,
            TenantRepository tenantRepository,
            AccountRepository accountRepository
    ) {
        this.dueRentRepository = dueRentRepository;
        this.collectionRentRepository = collectionRentRepository;
        this.tenantRepository = tenantRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public RentRecordResponse upsertRentRecord(RentRecordRequest request) {
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + request.getTenantId()));

        LocalDate normalizedMonth = YearMonth.from(request.getBillingMonth()).atDay(1);

        DueRent dueRent = dueRentRepository.findByTenant_IdAndBillingMonth(request.getTenantId(), normalizedMonth)
                .orElseGet(DueRent::new);

        dueRent.setTenant(tenant);
        dueRent.setBillingMonth(normalizedMonth);
        dueRent.setDueAmount(request.getDueAmount());
        dueRent.setPaidAmount(request.getPaidAmount());
        dueRent.setAccount(resolveAccount(request.getAccountId()));
        dueRent.setStatus(resolveStatus(request.getDueAmount(), request.getPaidAmount()));

        DueRent saved = dueRentRepository.save(dueRent);
        syncCollectionFromDue(saved, null);
        syncTenantDueAndPayment(tenant, saved);

        return toResponse(saved);
    }

    @Override
    public RentRecordResponse updateRentRecord(Long recordId, RentRecordUpdateRequest request) {
        DueRent dueRent = dueRentRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Rent record not found with id: " + recordId));

        dueRent.setDueAmount(request.getDueAmount());
        dueRent.setPaidAmount(request.getPaidAmount());
        dueRent.setAccount(resolveAccount(request.getAccountId()));
        dueRent.setStatus(resolveStatus(request.getDueAmount(), request.getPaidAmount()));

        DueRent saved = dueRentRepository.save(dueRent);
        syncCollectionFromDue(saved, request.getTransactionDate());
        syncTenantDueAndPayment(saved.getTenant(), saved);
        return toResponse(saved);
    }

    @Override
    public void deleteRentRecord(Long recordId) {
        DueRent dueRent = dueRentRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Rent record not found with id: " + recordId));

        dueRent.setDueAmount(BigDecimal.ZERO);
        dueRent.setStatus(resolveStatus(dueRent.getDueAmount(), dueRent.getPaidAmount()));
        DueRent saved = dueRentRepository.save(dueRent);
        syncTenantDueAndPayment(saved.getTenant(), saved);
    }

    @Override
    public void deleteCollectedRecord(Long recordId) {
        collectionRentRepository.findById(recordId).ifPresent(collectionRentRepository::delete);
    }

    @Override
    public RentRecordResponse markAsPaid(Long recordId) {
        DueRent dueRent = dueRentRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Rent record not found with id: " + recordId));

        dueRent.setPaidAmount(dueRent.getDueAmount());
        dueRent.setStatus(RentRecordStatus.PAID);
        DueRent saved = dueRentRepository.save(dueRent);
        syncCollectionFromDue(saved, null);
        syncTenantDueAndPayment(saved.getTenant(), saved);

        return toResponse(saved);
    }

    @Override
    public List<RentRecordResponse> getDueRentRecords(LocalDate from, LocalDate to) {
        LocalDate start = normalizeStart(from);
        LocalDate end = normalizeEnd(to);
        LocalDate currentMonthStart = YearMonth.from(LocalDate.now()).atDay(1);
        if (!end.isBefore(currentMonthStart)) {
            ensureAutoGeneratedDueRecords();
        }
        return dueRentRepository.findAllByStatusInAndBillingMonthBetweenOrderByBillingMonthDesc(
                        List.of(RentRecordStatus.DUE, RentRecordStatus.PARTIAL), start, end)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RentRecordResponse> getCollectedRentRecords(LocalDate from, LocalDate to) {
        LocalDate start = normalizeStart(from);
        LocalDate end = normalizeEnd(to);
        ZoneId zone = ZoneId.systemDefault();
        return collectionRentRepository.findAllByCollectedAtBetweenOrderByCollectedAtDesc(
                        start.atStartOfDay(zone).toOffsetDateTime(),
                        end.plusDays(1).atStartOfDay(zone).toOffsetDateTime())
                .stream()
                .map(this::toCollectedResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RentTransactionResponse> getRentTransactions(Long recordId) {
        return collectionRentRepository.findById(recordId)
                .map(this::toTransactionResponse)
                .map(List::of)
                .orElse(Collections.emptyList());
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCollectionReportCsv(LocalDate from, LocalDate to, Long accountId) {
        LocalDate start = normalizeStart(from);
        LocalDate end = normalizeEnd(to);
        ZoneId zone = ZoneId.systemDefault();

        List<CollectionRent> regularCollections = collectionRentRepository
                .findAllByCollectedAtBetweenAndAccountOrderByCollectedAtDesc(
                        start.atStartOfDay(zone).toOffsetDateTime(),
                        end.plusDays(1).atStartOfDay(zone).toOffsetDateTime(),
                        accountId
                );

        List<Tenant> dailyCollections = tenantRepository.findDailyCollectionsForReport(start, end, accountId);

        List<ExportRow> rows = new ArrayList<>();
        regularCollections.forEach(record -> rows.add(ExportRow.regular(record)));
        dailyCollections.forEach(tenant -> rows.add(ExportRow.daily(tenant)));
        rows.sort(Comparator.comparing(ExportRow::transactionDateTime).reversed());

        StringBuilder csv = new StringBuilder();
        csv.append("Type,Transaction Date-Time,Tenant Name,Room Number,Billing Month,Amount,Account Name,Account Mode\n");
        rows.forEach(row -> {
            csv.append(csvCell(row.type())).append(',')
                    .append(csvCell(row.transactionDateTime())).append(',')
                    .append(csvCell(row.tenantName())).append(',')
                    .append(csvCell(row.roomNumber())).append(',')
                    .append(csvCell(row.billingMonth())).append(',')
                    .append(csvCell(row.amount())).append(',')
                    .append(csvCell(row.accountName())).append(',')
                    .append(csvCell(row.accountMode()))
                    .append('\n');
        });

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(LocalDate from, LocalDate to) {
        LocalDate start = normalizeStart(from);
        LocalDate end = normalizeEnd(to);
        ZoneId zone = ZoneId.systemDefault();

        BigDecimal rentRecordPaid = collectionRentRepository.sumCollectedBetween(
                start.atStartOfDay(zone).toOffsetDateTime(),
                end.plusDays(1).atStartOfDay(zone).toOffsetDateTime()
        );
        BigDecimal rentRecordDue = dueRentRepository.sumDueBetween(start, end);
        BigDecimal pendingRentRecords = rentRecordDue.subtract(rentRecordPaid).max(BigDecimal.ZERO);

        List<Tenant> joiningTenants = tenantRepository.findAllRegularByJoiningDateBetween(start, end);
        BigDecimal joiningCollection = joiningTenants.stream()
                .map(t -> nvl(t.getRentPaidAmount()).add(nvl(t.getDepositPaidAmount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal joiningPending = joiningTenants.stream()
                .map(t -> {
                    BigDecimal depositDue = nvl(t.getDeposit()).subtract(nvl(t.getDepositPaidAmount())).max(BigDecimal.ZERO);
                    return nvl(t.getRentDueAmount()).add(depositDue);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCollection = rentRecordPaid.add(joiningCollection);
        BigDecimal totalDue = pendingRentRecords.add(joiningPending);

        DashboardSummaryResponse response = new DashboardSummaryResponse();
        response.setTotalRentCollection(totalCollection);
        response.setTotalDueAmount(totalDue);
        response.setTotalPendingCollection(totalDue);
        response.setActiveTenants(tenantRepository.countByActiveTrue());
        return response;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private synchronized void ensureAutoGeneratedDueRecords() {
        LocalDate today = LocalDate.now();
        if (today.equals(lastDueGenerationRunDate)) {
            return;
        }
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate billingMonth = currentMonth.atDay(1);
        List<Tenant> activeTenants = tenantRepository.findAllActiveRegularTenantsOrderByCreatedAtDesc();
        Set<Long> existingDueTenantIds = new HashSet<>(dueRentRepository.findTenantIdsByBillingMonth(billingMonth));
        List<Tenant> tenantsToUpdate = new ArrayList<>();

        for (Tenant tenant : activeTenants) {
            if (tenant.getJoiningDate() == null) {
                continue;
            }

            LocalDate nextDueDate = tenant.getLastDueGeneratedFor() != null
                    ? tenant.getLastDueGeneratedFor().plusMonths(1)
                    : tenant.getJoiningDate().plusMonths(1).minusDays(1);

            if (nextDueDate.isAfter(today)) {
                continue;
            }

            LocalDate dueToGenerate = nextDueDate;
            if (YearMonth.from(nextDueDate).isBefore(currentMonth)) {
                // No historical backfill: older tenants should get current month due from month-start.
                dueToGenerate = currentMonth.atDay(1);
            }

            if (!YearMonth.from(dueToGenerate).equals(currentMonth) || dueToGenerate.isAfter(today)) {
                continue;
            }

            if (!existingDueTenantIds.contains(tenant.getId())) {
                DueRent dueRent = new DueRent();
                dueRent.setTenant(tenant);
                dueRent.setBillingMonth(billingMonth);
                dueRent.setDueAmount(nvl(tenant.getRent()));
                dueRent.setPaidAmount(BigDecimal.ZERO);
                dueRent.setStatus(RentRecordStatus.DUE);
                try {
                    dueRentRepository.save(dueRent);
                    existingDueTenantIds.add(tenant.getId());
                } catch (DataIntegrityViolationException ignored) {
                    // Concurrent request may have inserted the same tenant+month due row.
                }
            }

            if (!dueToGenerate.equals(tenant.getLastDueGeneratedFor())) {
                tenant.setLastDueGeneratedFor(dueToGenerate);
                tenantsToUpdate.add(tenant);
            }
        }

        if (!tenantsToUpdate.isEmpty()) {
            tenantRepository.saveAll(tenantsToUpdate);
        }
        lastDueGenerationRunDate = today;
    }

    private RentRecordStatus resolveStatus(BigDecimal due, BigDecimal paid) {
        if (paid.compareTo(due) >= 0) {
            return RentRecordStatus.PAID;
        }
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return RentRecordStatus.DUE;
        }
        return RentRecordStatus.PARTIAL;
    }

    private void syncCollectionFromDue(DueRent dueRent, LocalDate transactionDate) {
        BigDecimal paid = nvl(dueRent.getPaidAmount());
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            collectionRentRepository.findById(dueRent.getId()).ifPresent(collectionRentRepository::delete);
            return;
        }

        CollectionRent collection = collectionRentRepository.findById(dueRent.getId()).orElseGet(CollectionRent::new);
        collection.setDueRent(dueRent);
        collection.setTenant(dueRent.getTenant());
        collection.setBillingMonth(dueRent.getBillingMonth());
        collection.setCollectedAmount(paid);
        collection.setAccount(dueRent.getAccount());
        collection.setCollectedAt(OffsetDateTime.now());
        collectionRentRepository.save(collection);
    }

    private void syncTenantDueAndPayment(Tenant tenant, DueRent dueRent) {
        BigDecimal balance = dueRent.getDueAmount().subtract(dueRent.getPaidAmount()).max(BigDecimal.ZERO);
        tenant.setRentPaidAmount(dueRent.getPaidAmount());
        tenant.setRentDueAmount(balance);
        tenant.setPaymentStatus(balance.compareTo(BigDecimal.ZERO) == 0
                ? PaymentStatus.ON_TIME
                : (dueRent.getPaidAmount().compareTo(BigDecimal.ZERO) > 0 ? PaymentStatus.PARTIAL : PaymentStatus.DUE));
        tenantRepository.save(tenant);
    }

    private LocalDate normalizeStart(LocalDate from) {
        LocalDate now = LocalDate.now();
        return from == null ? YearMonth.from(now).atDay(1) : YearMonth.from(from).atDay(1);
    }

    private LocalDate normalizeEnd(LocalDate to) {
        LocalDate now = LocalDate.now();
        return to == null ? YearMonth.from(now).atEndOfMonth() : YearMonth.from(to).atEndOfMonth();
    }

    private RentRecordResponse toResponse(DueRent dueRent) {
        RentRecordResponse response = new RentRecordResponse();
        response.setId(dueRent.getId());
        response.setTenantId(dueRent.getTenant().getId());
        response.setTenantName(dueRent.getTenant().getFullName());
        response.setRoomNumber(dueRent.getTenant().getRoomNumber());
        response.setBillingMonth(dueRent.getBillingMonth());
        response.setTransactionAt(dueRent.getUpdatedAt());
        response.setDueAmount(dueRent.getDueAmount());
        response.setPaidAmount(dueRent.getPaidAmount());
        response.setStatus(dueRent.getStatus());
        response.setAccountId(dueRent.getAccount() != null ? dueRent.getAccount().getId() : null);
        response.setAccountName(dueRent.getAccount() != null ? dueRent.getAccount().getName() : null);
        return response;
    }

    private RentRecordResponse toCollectedResponse(CollectionRent collectionRent) {
        RentRecordResponse response = new RentRecordResponse();
        response.setId(collectionRent.getDueRentId());
        response.setTenantId(collectionRent.getTenant().getId());
        response.setTenantName(collectionRent.getTenant().getFullName());
        response.setRoomNumber(collectionRent.getTenant().getRoomNumber());
        response.setBillingMonth(collectionRent.getBillingMonth());
        response.setTransactionAt(collectionRent.getCollectedAt());
        response.setDueAmount(collectionRent.getDueRent().getDueAmount());
        response.setPaidAmount(collectionRent.getCollectedAmount());
        response.setStatus(RentRecordStatus.PAID);
        response.setAccountId(collectionRent.getAccount() != null ? collectionRent.getAccount().getId() : null);
        response.setAccountName(collectionRent.getAccount() != null ? collectionRent.getAccount().getName() : null);
        return response;
    }

    private RentTransactionResponse toTransactionResponse(CollectionRent collectionRent) {
        RentTransactionResponse row = new RentTransactionResponse();
        row.setId(collectionRent.getDueRentId());
        row.setDueRentId(collectionRent.getDueRentId());
        row.setTenantId(collectionRent.getTenant() != null ? collectionRent.getTenant().getId() : null);
        row.setTenantName(collectionRent.getTenant() != null ? collectionRent.getTenant().getFullName() : null);
        row.setRoomNumber(collectionRent.getTenant() != null ? collectionRent.getTenant().getRoomNumber() : null);
        row.setPaidAmount(collectionRent.getCollectedAmount());
        row.setAccountId(collectionRent.getAccount() != null ? collectionRent.getAccount().getId() : null);
        row.setAccountName(collectionRent.getAccount() != null ? collectionRent.getAccount().getName() : null);
        row.setTransactionAt(collectionRent.getCollectedAt());
        return row;
    }

    private String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        return "\"" + text + "\"";
    }

    private record ExportRow(
            String type,
            String transactionDateTime,
            String tenantName,
            String roomNumber,
            String billingMonth,
            String amount,
            String accountName,
            String accountMode
    ) {
        static ExportRow regular(CollectionRent record) {
            return new ExportRow(
                    "REGULAR_RENT",
                    record.getCollectedAt() != null ? record.getCollectedAt().toString() : "",
                    record.getTenant() != null ? record.getTenant().getFullName() : "",
                    record.getTenant() != null ? record.getTenant().getRoomNumber() : "",
                    record.getBillingMonth() != null ? record.getBillingMonth().toString() : "",
                    record.getCollectedAmount() != null ? record.getCollectedAmount().toPlainString() : "0",
                    record.getAccount() != null ? record.getAccount().getName() : "",
                    record.getAccount() != null ? record.getAccount().getMode() : ""
            );
        }

        static ExportRow daily(Tenant tenant) {
            return new ExportRow(
                    "DAILY_COLLECTION",
                    tenant.getDailyCollectionTransactionDate() != null
                            ? tenant.getDailyCollectionTransactionDate().atStartOfDay().atOffset(ZoneOffset.UTC).toString()
                            : "",
                    tenant.getFullName(),
                    tenant.getRoomNumber(),
                    tenant.getDailyCollectionTransactionDate() != null
                            ? YearMonth.from(tenant.getDailyCollectionTransactionDate()).atDay(1).toString()
                            : "",
                    tenant.getDailyCollectionAmount() != null ? tenant.getDailyCollectionAmount().toPlainString() : "0",
                    tenant.getDailyCollectionAccount() != null ? tenant.getDailyCollectionAccount().getName() : "",
                    tenant.getDailyCollectionAccount() != null ? tenant.getDailyCollectionAccount().getMode() : ""
            );
        }
    }

    private Account resolveAccount(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
    }
}
