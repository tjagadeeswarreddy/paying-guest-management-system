package com.pgms.service.impl;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import com.pgms.dto.DashboardSummaryResponse;
import com.pgms.dto.RentRecordRequest;
import com.pgms.dto.RentRecordResponse;
import com.pgms.dto.RentRecordUpdateRequest;
import com.pgms.dto.RentTransactionResponse;
import com.pgms.entity.PaymentStatus;
import com.pgms.entity.RentRecordStatus;
import com.pgms.exception.ResourceNotFoundException;
import com.pgms.service.RentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

@Service
@ConditionalOnProperty(name = "app.data-provider", havingValue = "firebase")
public class FirestoreRentServiceImpl implements RentService {

    private static final Logger log = LoggerFactory.getLogger(FirestoreRentServiceImpl.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String DUE_RENTS_COLLECTION = "dueRents";
    private static final String COLLECTION_RENTS_COLLECTION = "collectionRents";
    private static final String COLLECTION_TRANSACTIONS_COLLECTION = "collectionTransactions";
    private static final String TENANTS_COLLECTION = "tenants";
    private static final String ACCOUNTS_COLLECTION = "accounts";
    private static final String META_COLLECTION = "_meta";
    private static final String DUE_RENT_COUNTER_DOC = "dueRentSeq";

    private final Firestore firestore;

    public FirestoreRentServiceImpl(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public RentRecordResponse upsertRentRecord(RentRecordRequest request) {
        TenantSnapshot tenant = getTenantSnapshot(request.getTenantId());
        LocalDate billingMonth = YearMonth.from(request.getBillingMonth()).atDay(1);
        DueRentPayload dueRent = findDueRentByTenantAndMonth(request.getTenantId(), billingMonth);
        BigDecimal previousPaid = dueRent != null ? nvl(dueRent.paidAmount) : ZERO;
        if (dueRent == null) {
            dueRent = new DueRentPayload();
            dueRent.id = nextDueRentId();
            dueRent.tenantId = tenant.id;
            dueRent.tenantName = tenant.fullName;
            dueRent.roomNumber = tenant.roomNumber;
            dueRent.billingMonth = billingMonth;
        }

        AccountSnapshot account = resolveAccount(request.getAccountId());
        dueRent.dueAmount = nvl(request.getDueAmount());
        dueRent.paidAmount = nvl(request.getPaidAmount()).min(dueRent.dueAmount).max(ZERO);
        dueRent.status = resolveStatus(dueRent.dueAmount, dueRent.paidAmount);
        dueRent.accountId = account != null ? account.id : null;
        dueRent.accountName = account != null ? account.name : null;
        dueRent.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);

        writeDueRent(dueRent);
        syncCollectionFromDue(dueRent, null);
        logCollectionTransaction(dueRent, dueRent.paidAmount.subtract(previousPaid), null);
        syncTenantDueAndPayment(tenant, dueRent);
        return toResponse(dueRent);
    }

    @Override
    public RentRecordResponse updateRentRecord(Long recordId, RentRecordUpdateRequest request) {
        DueRentPayload dueRent = getDueRentById(recordId);
        BigDecimal previousPaid = nvl(dueRent.paidAmount);
        TenantSnapshot tenant = getTenantSnapshot(dueRent.tenantId);
        AccountSnapshot account = resolveAccount(request.getAccountId());

        dueRent.dueAmount = nvl(request.getDueAmount());
        dueRent.paidAmount = nvl(request.getPaidAmount()).min(dueRent.dueAmount).max(ZERO);
        dueRent.status = resolveStatus(dueRent.dueAmount, dueRent.paidAmount);
        dueRent.accountId = account != null ? account.id : null;
        dueRent.accountName = account != null ? account.name : null;
        dueRent.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);

        writeDueRent(dueRent);
        syncCollectionFromDue(dueRent, request.getTransactionDate());
        logCollectionTransaction(dueRent, dueRent.paidAmount.subtract(previousPaid), request.getTransactionDate());
        syncTenantDueAndPayment(tenant, dueRent);
        return toResponse(dueRent);
    }

    @Override
    public void deleteRentRecord(Long recordId) {
        DueRentPayload dueRent = getDueRentById(recordId);
        TenantSnapshot tenant = getTenantSnapshot(dueRent.tenantId);
        dueRent.dueAmount = ZERO;
        dueRent.status = resolveStatus(dueRent.dueAmount, dueRent.paidAmount);
        dueRent.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        writeDueRent(dueRent);
        syncCollectionFromDue(dueRent, null);
        syncTenantDueAndPayment(tenant, dueRent);
    }

    @Override
    public void deleteCollectedRecord(Long recordId) {
        try {
            collectionRents().document(String.valueOf(recordId)).delete().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while deleting collected record", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to delete collected record", e);
        }
    }

    @Override
    public RentRecordResponse markAsPaid(Long recordId) {
        DueRentPayload dueRent = getDueRentById(recordId);
        BigDecimal previousPaid = nvl(dueRent.paidAmount);
        TenantSnapshot tenant = getTenantSnapshot(dueRent.tenantId);
        dueRent.paidAmount = dueRent.dueAmount;
        dueRent.status = RentRecordStatus.PAID;
        dueRent.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        writeDueRent(dueRent);
        syncCollectionFromDue(dueRent, null);
        logCollectionTransaction(dueRent, dueRent.paidAmount.subtract(previousPaid), null);
        syncTenantDueAndPayment(tenant, dueRent);
        return toResponse(dueRent);
    }

    @Override
    public List<RentRecordResponse> getDueRentRecords(LocalDate from, LocalDate to) {
        LocalDate start = normalizeStart(from);
        LocalDate end = normalizeEnd(to);
        try {
            QuerySnapshot snapshot = dueRents()
                    .whereGreaterThanOrEqualTo("billingMonth", start.toString())
                    .whereLessThanOrEqualTo("billingMonth", end.toString())
                    .orderBy("billingMonth", Query.Direction.DESCENDING)
                    .get()
                    .get();

            List<RentRecordResponse> records = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                DueRentPayload payload = toDueRentPayload(doc);
                if (payload.status == RentRecordStatus.DUE || payload.status == RentRecordStatus.PARTIAL) {
                    records.add(toResponse(payload));
                }
            }
            return records;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying due rents", e);
        } catch (ExecutionException e) {
            if (isQuotaExceeded(e)) {
                log.warn("Firestore quota exceeded while querying due rents. Returning empty due list.");
                return List.of();
            }
            throw new IllegalStateException("Failed to query due rents", e);
        }
    }

    @Override
    public List<RentRecordResponse> getCollectedRentRecords(LocalDate from, LocalDate to) {
        LocalDate start = normalizeStart(from);
        LocalDate end = normalizeEnd(to);
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime startTs = start.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime endTs = end.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        try {
            QuerySnapshot snapshot = collectionRents()
                    .whereGreaterThanOrEqualTo("collectedAt", timestamp(startTs))
                    .whereLessThan("collectedAt", timestamp(endTs))
                    .orderBy("collectedAt", Query.Direction.DESCENDING)
                    .get()
                    .get();

            List<RentRecordResponse> records = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                records.add(toCollectedResponse(toCollectionPayload(doc)));
            }
            return records;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying collected rents", e);
        } catch (ExecutionException e) {
            if (isQuotaExceeded(e)) {
                log.warn("Firestore quota exceeded while querying collected rents. Returning empty collected list.");
                return List.of();
            }
            throw new IllegalStateException("Failed to query collected rents", e);
        }
    }

    @Override
    public List<RentTransactionResponse> getRentTransactions(Long recordId) {
        try {
            QuerySnapshot snapshot = collectionTransactions()
                    .whereEqualTo("dueRentId", recordId)
                    .get()
                    .get();
            List<RentTransactionResponse> rows = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                rows.add(toTransactionResponse(doc));
            }
            rows.sort(Comparator.comparing(RentTransactionResponse::getTransactionAt).reversed());
            return rows;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying transactions", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to query transactions", e);
        }
    }

    @Override
    public byte[] exportCollectionReportCsv(LocalDate from, LocalDate to, Long accountId) {
        LocalDate start = normalizeStart(from);
        LocalDate end = normalizeEnd(to);

        List<ExportRow> rows = new ArrayList<>();
        for (CollectionPayload regular : getCollectionPayloads(start, end)) {
            if (accountId == null || Objects.equals(regular.accountId, accountId)) {
                rows.add(ExportRow.regular(regular));
            }
        }
        for (TenantSnapshot tenant : getDailyCollectionTenants(start, end, accountId)) {
            rows.add(ExportRow.daily(tenant));
        }
        rows.sort(Comparator.comparing(ExportRow::transactionDateTime).reversed());

        StringBuilder csv = new StringBuilder();
        csv.append("Type,Transaction Date-Time,Tenant Name,Room Number,Billing Month,Amount,Account Name,Account Mode\n");
        for (ExportRow row : rows) {
            csv.append(csvCell(row.type())).append(',')
                    .append(csvCell(row.transactionDateTime())).append(',')
                    .append(csvCell(row.tenantName())).append(',')
                    .append(csvCell(row.roomNumber())).append(',')
                    .append(csvCell(row.billingMonth())).append(',')
                    .append(csvCell(row.amount())).append(',')
                    .append(csvCell(row.accountName())).append(',')
                    .append(csvCell(row.accountMode()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public DashboardSummaryResponse getDashboardSummary(LocalDate from, LocalDate to) {
        LocalDate start = normalizeStart(from);
        LocalDate end = normalizeEnd(to);

        BigDecimal rentRecordPaid = getCollectionPayloads(start, end).stream()
                .map(p -> p.collectedAmount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal rentRecordDue = getDueRentPayloads(start, end).stream()
                .map(p -> p.dueAmount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal pendingRentRecords = rentRecordDue.subtract(rentRecordPaid).max(ZERO);

        List<TenantSnapshot> joiningTenants = getRegularJoiningTenants(start, end);
        BigDecimal joiningCollection = joiningTenants.stream()
                .map(t -> nvl(t.rentPaidAmount).add(nvl(t.depositPaidAmount)))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal joiningPending = joiningTenants.stream()
                .map(t -> nvl(t.rentDueAmount).add(nvl(t.deposit).subtract(nvl(t.depositPaidAmount)).max(ZERO)))
                .reduce(ZERO, BigDecimal::add);

        BigDecimal totalCollection = rentRecordPaid.add(joiningCollection);
        BigDecimal totalDue = pendingRentRecords.add(joiningPending);

        DashboardSummaryResponse response = new DashboardSummaryResponse();
        response.setTotalRentCollection(totalCollection);
        response.setTotalDueAmount(totalDue);
        response.setTotalPendingCollection(totalDue);
        response.setActiveTenants(countActiveTenants());
        return response;
    }

    private CollectionReference dueRents() {
        return firestore.collection(DUE_RENTS_COLLECTION);
    }

    private CollectionReference collectionRents() {
        return firestore.collection(COLLECTION_RENTS_COLLECTION);
    }

    private CollectionReference collectionTransactions() {
        return firestore.collection(COLLECTION_TRANSACTIONS_COLLECTION);
    }

    private CollectionReference tenants() {
        return firestore.collection(TENANTS_COLLECTION);
    }

    private CollectionReference accounts() {
        return firestore.collection(ACCOUNTS_COLLECTION);
    }

    private Long nextDueRentId() {
        try {
            return firestore.runTransaction(transaction -> {
                DocumentReference counterRef = firestore.collection(META_COLLECTION).document(DUE_RENT_COUNTER_DOC);
                DocumentSnapshot counterSnapshot = transaction.get(counterRef).get();
                long nextId = 1L;
                if (counterSnapshot.exists() && counterSnapshot.getLong("nextId") != null) {
                    nextId = counterSnapshot.getLong("nextId");
                }
                Map<String, Object> update = new HashMap<>();
                update.put("nextId", nextId + 1L);
                update.put("updatedAt", Timestamp.now());
                transaction.set(counterRef, update, SetOptions.merge());
                return nextId;
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while generating due rent id", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to generate due rent id", e);
        }
    }

    private DueRentPayload findDueRentByTenantAndMonth(Long tenantId, LocalDate billingMonth) {
        try {
            QuerySnapshot snapshot = dueRents()
                    .whereEqualTo("tenantId", tenantId)
                    .whereEqualTo("billingMonth", billingMonth.toString())
                    .get()
                    .get();
            if (snapshot.isEmpty()) {
                return null;
            }
            return toDueRentPayload(snapshot.getDocuments().get(0));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while finding due rent", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to find due rent", e);
        }
    }

    private DueRentPayload getDueRentById(Long id) {
        try {
            DocumentSnapshot snapshot = dueRents().document(String.valueOf(id)).get().get();
            if (!snapshot.exists()) {
                throw new ResourceNotFoundException("Rent record not found with id: " + id);
            }
            return toDueRentPayload(snapshot);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading due rent", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to read due rent", e);
        }
    }

    private AccountSnapshot resolveAccount(Long accountId) {
        if (accountId == null) {
            return null;
        }
        try {
            DocumentSnapshot snapshot = accounts().document(String.valueOf(accountId)).get().get();
            if (!snapshot.exists()) {
                throw new ResourceNotFoundException("Account not found with id: " + accountId);
            }
            AccountSnapshot account = new AccountSnapshot();
            account.id = readLong(snapshot.get("id"), snapshot.getId());
            account.name = asString(snapshot.get("name"));
            account.mode = asString(snapshot.get("mode"));
            return account;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading account", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to read account", e);
        }
    }

    private TenantSnapshot getTenantSnapshot(Long tenantId) {
        try {
            DocumentSnapshot snapshot = tenants().document(String.valueOf(tenantId)).get().get();
            if (!snapshot.exists()) {
                throw new ResourceNotFoundException("Tenant not found with id: " + tenantId);
            }
            TenantSnapshot tenant = toTenantSnapshot(snapshot);
            tenant.reference = snapshot.getReference();
            return tenant;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading tenant", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to read tenant", e);
        }
    }

    private void writeDueRent(DueRentPayload payload) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", payload.id);
        doc.put("tenantId", payload.tenantId);
        doc.put("tenantName", payload.tenantName);
        doc.put("roomNumber", payload.roomNumber);
        doc.put("billingMonth", payload.billingMonth.toString());
        doc.put("dueAmount", payload.dueAmount.toPlainString());
        doc.put("paidAmount", payload.paidAmount.toPlainString());
        doc.put("status", payload.status.name());
        doc.put("accountId", payload.accountId);
        doc.put("accountName", payload.accountName);
        doc.put("updatedAt", timestamp(payload.updatedAt));
        try {
            dueRents().document(String.valueOf(payload.id)).set(doc).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing due rent", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to write due rent", e);
        }
    }

    private void syncCollectionFromDue(DueRentPayload dueRent, LocalDate transactionDate) {
        if (dueRent.paidAmount.compareTo(ZERO) <= 0) {
            try {
                collectionRents().document(String.valueOf(dueRent.id)).delete().get();
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while deleting collection record", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Failed to delete collection record", e);
            }
        }

        OffsetDateTime collectedAt = OffsetDateTime.now();

        AccountSnapshot account = resolveAccount(dueRent.accountId);
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", dueRent.id);
        doc.put("dueRentId", dueRent.id);
        doc.put("tenantId", dueRent.tenantId);
        doc.put("tenantName", dueRent.tenantName);
        doc.put("roomNumber", dueRent.roomNumber);
        doc.put("billingMonth", dueRent.billingMonth.toString());
        doc.put("dueAmount", dueRent.dueAmount.toPlainString());
        doc.put("collectedAmount", dueRent.paidAmount.toPlainString());
        doc.put("accountId", account != null ? account.id : null);
        doc.put("accountName", account != null ? account.name : null);
        doc.put("accountMode", account != null ? account.mode : null);
        doc.put("collectedAt", timestamp(collectedAt));

        try {
            collectionRents().document(String.valueOf(dueRent.id)).set(doc).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing collection record", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to write collection record", e);
        }
    }

    private void logCollectionTransaction(DueRentPayload dueRent, BigDecimal deltaPaid, LocalDate transactionDate) {
        if (deltaPaid == null || deltaPaid.compareTo(ZERO) <= 0) {
            return;
        }
        OffsetDateTime txAt = OffsetDateTime.now();
        Map<String, Object> doc = new HashMap<>();
        doc.put("dueRentId", dueRent.id);
        doc.put("tenantId", dueRent.tenantId);
        doc.put("tenantName", dueRent.tenantName);
        doc.put("roomNumber", dueRent.roomNumber);
        doc.put("paidAmount", deltaPaid.toPlainString());
        doc.put("accountId", dueRent.accountId);
        doc.put("accountName", dueRent.accountName);
        doc.put("transactionAt", timestamp(txAt));
        try {
            collectionTransactions().document().set(doc).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing transaction", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to write transaction", e);
        }
    }

    private void syncTenantDueAndPayment(TenantSnapshot tenant, DueRentPayload dueRent) {
        BigDecimal balance = dueRent.dueAmount.subtract(dueRent.paidAmount).max(ZERO);
        String paymentStatus = balance.compareTo(ZERO) == 0
                ? PaymentStatus.ON_TIME.name()
                : (dueRent.paidAmount.compareTo(ZERO) > 0 ? PaymentStatus.PARTIAL.name() : PaymentStatus.DUE.name());
        Map<String, Object> update = new HashMap<>();
        update.put("rentPaidAmount", dueRent.paidAmount.toPlainString());
        update.put("rentDueAmount", balance.toPlainString());
        update.put("paymentStatus", paymentStatus);
        try {
            tenant.reference.set(update, SetOptions.merge()).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while updating tenant due", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to update tenant due", e);
        }
    }

    private List<CollectionPayload> getCollectionPayloads(LocalDate start, LocalDate end) {
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime startTs = start.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime endTs = end.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        try {
            QuerySnapshot snapshot = collectionRents()
                    .whereGreaterThanOrEqualTo("collectedAt", timestamp(startTs))
                    .whereLessThan("collectedAt", timestamp(endTs))
                    .get()
                    .get();
            List<CollectionPayload> items = new ArrayList<>(snapshot.size());
            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                items.add(toCollectionPayload(doc));
            }
            return items;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading collection data", e);
        } catch (ExecutionException e) {
            if (isQuotaExceeded(e)) {
                log.warn("Firestore quota exceeded while reading collection data. Returning empty list.");
                return List.of();
            }
            throw new IllegalStateException("Failed to read collection data", e);
        }
    }

    private List<DueRentPayload> getDueRentPayloads(LocalDate start, LocalDate end) {
        try {
            QuerySnapshot snapshot = dueRents()
                    .whereGreaterThanOrEqualTo("billingMonth", start.toString())
                    .whereLessThanOrEqualTo("billingMonth", end.toString())
                    .get()
                    .get();
            List<DueRentPayload> items = new ArrayList<>(snapshot.size());
            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                items.add(toDueRentPayload(doc));
            }
            return items;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading due rent data", e);
        } catch (ExecutionException e) {
            if (isQuotaExceeded(e)) {
                log.warn("Firestore quota exceeded while reading due rent data. Returning empty list.");
                return List.of();
            }
            throw new IllegalStateException("Failed to read due rent data", e);
        }
    }

    private boolean isQuotaExceeded(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof StatusRuntimeException statusError
                    && statusError.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private List<TenantSnapshot> getRegularJoiningTenants(LocalDate start, LocalDate end) {
        try {
            QuerySnapshot snapshot = tenants().get().get();
            List<TenantSnapshot> items = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                TenantSnapshot tenant = toTenantSnapshot(doc);
                boolean dailyAccommodation = Boolean.TRUE.equals(doc.getBoolean("dailyAccommodation"));
                if (dailyAccommodation || tenant.joiningDate == null) {
                    continue;
                }
                if (tenant.joiningDate.isBefore(start) || tenant.joiningDate.isAfter(end)) {
                    continue;
                }
                items.add(tenant);
            }
            return items;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading joining tenants", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to read joining tenants", e);
        }
    }

    private List<TenantSnapshot> getDailyCollectionTenants(LocalDate start, LocalDate end, Long accountId) {
        try {
            QuerySnapshot snapshot = tenants().whereEqualTo("dailyAccommodation", true).get().get();
            List<TenantSnapshot> items = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                TenantSnapshot tenant = toTenantSnapshot(doc);
                if (nvl(tenant.dailyCollectionAmount).compareTo(ZERO) <= 0) {
                    continue;
                }
                if (tenant.dailyCollectionTransactionDate == null) {
                    continue;
                }
                if (tenant.dailyCollectionTransactionDate.isBefore(start) || tenant.dailyCollectionTransactionDate.isAfter(end)) {
                    continue;
                }
                if (accountId != null && !Objects.equals(accountId, tenant.dailyCollectionAccountId)) {
                    continue;
                }
                items.add(tenant);
            }
            return items;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading daily collections", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to read daily collections", e);
        }
    }

    private long countActiveTenants() {
        try {
            return tenants().whereEqualTo("active", true).get().get().size();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while counting active tenants", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to count active tenants", e);
        }
    }

    private LocalDate normalizeStart(LocalDate from) {
        LocalDate now = LocalDate.now();
        return from == null ? YearMonth.from(now).atDay(1) : YearMonth.from(from).atDay(1);
    }

    private LocalDate normalizeEnd(LocalDate to) {
        LocalDate now = LocalDate.now();
        return to == null ? YearMonth.from(now).atEndOfMonth() : YearMonth.from(to).atEndOfMonth();
    }

    private RentRecordStatus resolveStatus(BigDecimal due, BigDecimal paid) {
        if (paid.compareTo(due) >= 0) {
            return RentRecordStatus.PAID;
        }
        if (paid.compareTo(ZERO) <= 0) {
            return RentRecordStatus.DUE;
        }
        return RentRecordStatus.PARTIAL;
    }

    private RentRecordResponse toResponse(DueRentPayload payload) {
        RentRecordResponse response = new RentRecordResponse();
        response.setId(payload.id);
        response.setTenantId(payload.tenantId);
        response.setTenantName(payload.tenantName);
        response.setRoomNumber(payload.roomNumber);
        response.setBillingMonth(payload.billingMonth);
        response.setTransactionAt(payload.updatedAt);
        response.setDueAmount(payload.dueAmount);
        response.setPaidAmount(payload.paidAmount);
        response.setStatus(payload.status);
        response.setAccountId(payload.accountId);
        response.setAccountName(payload.accountName);
        return response;
    }

    private RentRecordResponse toCollectedResponse(CollectionPayload payload) {
        RentRecordResponse response = new RentRecordResponse();
        response.setId(payload.dueRentId);
        response.setTenantId(payload.tenantId);
        response.setTenantName(payload.tenantName);
        response.setRoomNumber(payload.roomNumber);
        response.setBillingMonth(payload.billingMonth);
        response.setTransactionAt(payload.collectedAt);
        response.setDueAmount(payload.dueAmount);
        response.setPaidAmount(payload.collectedAmount);
        response.setStatus(RentRecordStatus.PAID);
        response.setAccountId(payload.accountId);
        response.setAccountName(payload.accountName);
        return response;
    }

    private RentTransactionResponse toTransactionResponse(DocumentSnapshot snapshot) {
        RentTransactionResponse row = new RentTransactionResponse();
        row.setId(readLong(snapshot.get("id"), snapshot.getId()));
        row.setDueRentId(readLong(snapshot.get("dueRentId"), null));
        row.setTenantId(readLong(snapshot.get("tenantId"), null));
        row.setTenantName(asString(snapshot.get("tenantName")));
        row.setRoomNumber(asString(snapshot.get("roomNumber")));
        row.setPaidAmount(toBigDecimal(snapshot.get("paidAmount")));
        row.setAccountId(readLong(snapshot.get("accountId"), null));
        row.setAccountName(asString(snapshot.get("accountName")));
        row.setTransactionAt(toOffsetDateTime(snapshot.get("transactionAt")));
        return row;
    }

    private DueRentPayload toDueRentPayload(DocumentSnapshot snapshot) {
        DueRentPayload payload = new DueRentPayload();
        payload.id = readLong(snapshot.get("id"), snapshot.getId());
        payload.tenantId = readLong(snapshot.get("tenantId"), null);
        payload.tenantName = asString(snapshot.get("tenantName"));
        payload.roomNumber = asString(snapshot.get("roomNumber"));
        payload.billingMonth = parseDate(snapshot.get("billingMonth"));
        payload.dueAmount = toBigDecimal(snapshot.get("dueAmount"));
        payload.paidAmount = toBigDecimal(snapshot.get("paidAmount"));
        payload.status = RentRecordStatus.valueOf(asString(snapshot.get("status")));
        payload.accountId = readLong(snapshot.get("accountId"), null);
        payload.accountName = asString(snapshot.get("accountName"));
        payload.updatedAt = toOffsetDateTime(snapshot.get("updatedAt"));
        return payload;
    }

    private CollectionPayload toCollectionPayload(DocumentSnapshot snapshot) {
        CollectionPayload payload = new CollectionPayload();
        payload.id = readLong(snapshot.get("id"), snapshot.getId());
        payload.dueRentId = readLong(snapshot.get("dueRentId"), snapshot.getId());
        payload.tenantId = readLong(snapshot.get("tenantId"), null);
        payload.tenantName = asString(snapshot.get("tenantName"));
        payload.roomNumber = asString(snapshot.get("roomNumber"));
        payload.billingMonth = parseDate(snapshot.get("billingMonth"));
        payload.dueAmount = toBigDecimal(snapshot.get("dueAmount"));
        payload.collectedAmount = toBigDecimal(snapshot.get("collectedAmount"));
        payload.accountId = readLong(snapshot.get("accountId"), null);
        payload.accountName = asString(snapshot.get("accountName"));
        payload.accountMode = asString(snapshot.get("accountMode"));
        payload.collectedAt = toOffsetDateTime(snapshot.get("collectedAt"));
        return payload;
    }

    private TenantSnapshot toTenantSnapshot(DocumentSnapshot snapshot) {
        TenantSnapshot tenant = new TenantSnapshot();
        tenant.id = readLong(snapshot.get("id"), snapshot.getId());
        tenant.fullName = asString(snapshot.get("fullName"));
        tenant.roomNumber = asString(snapshot.get("roomNumber"));
        tenant.rentDueAmount = toBigDecimal(snapshot.get("rentDueAmount"));
        tenant.rentPaidAmount = toBigDecimal(snapshot.get("rentPaidAmount"));
        tenant.deposit = toBigDecimal(snapshot.get("deposit"));
        tenant.depositPaidAmount = toBigDecimal(snapshot.get("depositPaidAmount"));
        tenant.dailyCollectionAmount = toBigDecimal(snapshot.get("dailyCollectionAmount"));
        tenant.dailyCollectionAccountId = readLong(snapshot.get("dailyCollectionAccountId"), null);
        tenant.dailyCollectionAccountName = asString(snapshot.get("dailyCollectionAccountName"));
        tenant.dailyCollectionTransactionDate = parseDate(snapshot.get("dailyCollectionTransactionDate"));
        tenant.joiningDate = parseDate(snapshot.get("joiningDate"));
        return tenant;
    }

    private OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return OffsetDateTime.ofInstant(timestamp.toDate().toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof String text && !text.isBlank()) {
            return OffsetDateTime.parse(text);
        }
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private Timestamp timestamp(OffsetDateTime value) {
        Instant instant = (value == null ? OffsetDateTime.now(ZoneOffset.UTC) : value).toInstant();
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long readLong(Object value, String fallback) {
        try {
            if (value == null) {
                return fallback == null ? null : Long.parseLong(fallback);
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return ZERO;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return null;
        }
        return LocalDate.parse(text);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? ZERO : value;
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
        static ExportRow regular(CollectionPayload record) {
            return new ExportRow(
                    "REGULAR_RENT",
                    record.collectedAt != null ? record.collectedAt.toString() : "",
                    record.tenantName,
                    record.roomNumber,
                    record.billingMonth != null ? record.billingMonth.toString() : "",
                    record.collectedAmount != null ? record.collectedAmount.toPlainString() : "0",
                    record.accountName,
                    record.accountMode
            );
        }

        static ExportRow daily(TenantSnapshot tenant) {
            return new ExportRow(
                    "DAILY_COLLECTION",
                    tenant.dailyCollectionTransactionDate != null
                            ? tenant.dailyCollectionTransactionDate.atStartOfDay().atOffset(ZoneOffset.UTC).toString()
                            : "",
                    tenant.fullName,
                    tenant.roomNumber,
                    tenant.dailyCollectionTransactionDate != null
                            ? YearMonth.from(tenant.dailyCollectionTransactionDate).atDay(1).toString()
                            : "",
                    tenant.dailyCollectionAmount != null ? tenant.dailyCollectionAmount.toPlainString() : "0",
                    tenant.dailyCollectionAccountName,
                    ""
            );
        }
    }

    private static final class DueRentPayload {
        private Long id;
        private Long tenantId;
        private String tenantName;
        private String roomNumber;
        private LocalDate billingMonth;
        private BigDecimal dueAmount = ZERO;
        private BigDecimal paidAmount = ZERO;
        private RentRecordStatus status = RentRecordStatus.DUE;
        private Long accountId;
        private String accountName;
        private OffsetDateTime updatedAt;
    }

    private static final class CollectionPayload {
        private Long id;
        private Long dueRentId;
        private Long tenantId;
        private String tenantName;
        private String roomNumber;
        private LocalDate billingMonth;
        private BigDecimal dueAmount = ZERO;
        private BigDecimal collectedAmount = ZERO;
        private Long accountId;
        private String accountName;
        private String accountMode;
        private OffsetDateTime collectedAt;
    }

    private static final class TenantSnapshot {
        private Long id;
        private String fullName;
        private String roomNumber;
        private BigDecimal rentDueAmount;
        private BigDecimal rentPaidAmount;
        private BigDecimal deposit;
        private BigDecimal depositPaidAmount;
        private BigDecimal dailyCollectionAmount;
        private Long dailyCollectionAccountId;
        private String dailyCollectionAccountName;
        private LocalDate dailyCollectionTransactionDate;
        private LocalDate joiningDate;
        private DocumentReference reference;
    }

    private static final class AccountSnapshot {
        private Long id;
        private String name;
        private String mode;
    }
}
