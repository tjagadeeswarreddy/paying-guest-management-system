package com.pgms.service.impl;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.pgms.dto.TenantRequest;
import com.pgms.dto.TenantResponse;
import com.pgms.entity.DailyFoodOption;
import com.pgms.entity.PaymentStatus;
import com.pgms.entity.SharingType;
import com.pgms.entity.VerificationStatus;
import com.pgms.exception.BadRequestException;
import com.pgms.exception.ResourceNotFoundException;
import com.pgms.service.TenantService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

@Service
@ConditionalOnProperty(name = "app.data-provider", havingValue = "firebase")
public class FirestoreTenantServiceImpl implements TenantService {

    private static final String TENANTS_COLLECTION = "tenants";
    private static final String META_COLLECTION = "_meta";
    private static final String TENANT_COUNTER_DOC = "tenantSeq";
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final Firestore firestore;

    public FirestoreTenantServiceImpl(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public TenantResponse createTenant(TenantRequest request) {
        Long tenantId = nextTenantId();
        TenantPayload payload = fromRequest(request);
        payload.documentId = String.valueOf(tenantId);
        payload.id = tenantId;
        payload.active = true;
        payload.checkoutDate = null;
        payload.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        payload.updatedAt = payload.createdAt;
        normalizeIdentityAndContacts(payload);
        validateUniqueName(payload.fullName, null);
        normalizeFinancials(payload);
        writeTenant(payload);
        return toResponse(payload);
    }

    @Override
    public TenantResponse updateTenant(Long tenantId, TenantRequest request) {
        TenantPayload existing = getTenantPayload(tenantId);
        TenantPayload payload = fromRequest(request);
        payload.documentId = existing.documentId;
        payload.id = tenantId;
        payload.active = existing.active;
        payload.checkoutDate = existing.checkoutDate;
        payload.createdAt = existing.createdAt;
        payload.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        normalizeIdentityAndContacts(payload);
        validateUniqueName(payload.fullName, tenantId);
        normalizeFinancials(payload);
        writeTenant(payload);
        return toResponse(payload);
    }

    @Override
    public TenantResponse checkoutTenant(Long tenantId) {
        TenantPayload payload = getTenantPayload(tenantId);
        payload.active = false;
        payload.checkoutDate = LocalDate.now();
        payload.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        writeTenant(payload);
        return toResponse(payload);
    }

    @Override
    public TenantResponse clearDailyCollection(Long tenantId) {
        TenantPayload payload = getTenantPayload(tenantId);
        if (!payload.dailyAccommodation) {
            throw new BadRequestException("Daily collection can be deleted only for daily accommodation tenants.");
        }
        payload.dailyCollectionAmount = ZERO;
        payload.dailyCollectionTransactionDate = null;
        payload.dailyCollectionAccountId = null;
        payload.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        writeTenant(payload);
        return toResponse(payload);
    }

    @Override
    public void deleteTenant(Long tenantId) {
        TenantPayload payload = getTenantPayload(tenantId);
        payload.active = false;
        payload.checkoutDate = LocalDate.now();
        payload.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        writeTenant(payload);
    }

    @Override
    public TenantResponse restoreTenant(Long tenantId) {
        TenantPayload payload = getTenantPayload(tenantId);
        payload.active = true;
        payload.checkoutDate = null;
        payload.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        writeTenant(payload);
        return toResponse(payload);
    }

    @Override
    public void permanentlyDeleteTenant(Long tenantId) {
        TenantPayload payload = getTenantPayload(tenantId);
        try {
            String docId = payload.documentId != null ? payload.documentId : String.valueOf(payload.id);
            tenants().document(docId).delete().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while deleting tenant", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to delete tenant", e);
        }
    }

    @Override
    public List<TenantResponse> getActiveTenants() {
        return getAllTenantPayloads().stream()
                .filter(payload -> payload.active && !payload.dailyAccommodation)
                .sorted(Comparator.comparing((TenantPayload payload) -> payload.createdAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<TenantResponse> getDailyTenants() {
        return getAllTenantPayloads().stream()
                .filter(payload -> payload.active && payload.dailyAccommodation)
                .sorted(Comparator.comparing((TenantPayload payload) -> payload.createdAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<TenantResponse> getDeletedTenants() {
        return getAllTenantPayloads().stream()
                .filter(payload -> !payload.active)
                .sorted(Comparator.comparing((TenantPayload payload) -> payload.createdAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<TenantResponse> getAllTenants() {
        return getAllTenantPayloads().stream()
                .sorted(Comparator.comparing((TenantPayload payload) -> payload.createdAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    private CollectionReference tenants() {
        return firestore.collection(TENANTS_COLLECTION);
    }

    private Long nextTenantId() {
        try {
            return firestore.runTransaction(transaction -> {
                DocumentReference counterRef = firestore.collection(META_COLLECTION).document(TENANT_COUNTER_DOC);
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
            throw new IllegalStateException("Interrupted while generating tenant id", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to generate tenant id", e);
        }
    }

    private TenantPayload getTenantPayload(Long tenantId) {
        DocumentSnapshot snapshot = getTenantDocumentSnapshot(tenantId);
        TenantPayload payload = toPayload(snapshot);
        if (payload.id == null) {
            payload.id = tenantId;
        }
        return payload;
    }

    private DocumentSnapshot getTenantDocumentSnapshot(Long tenantId) {
        try {
            DocumentSnapshot snapshot = tenants().document(String.valueOf(tenantId)).get().get();
            if (snapshot.exists()) {
                return snapshot;
            }

            QuerySnapshot byIdField = tenants()
                    .whereEqualTo("id", tenantId)
                    .limit(1)
                    .get()
                    .get();
            if (!byIdField.isEmpty()) {
                return byIdField.getDocuments().get(0);
            }

            throw new ResourceNotFoundException("Tenant not found with id: " + tenantId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading tenant", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to read tenant", e);
        }
    }

    private void writeTenant(TenantPayload payload) {
        try {
            String docId = payload.documentId != null ? payload.documentId : String.valueOf(payload.id);
            tenants().document(docId).set(toDocument(payload)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing tenant", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to write tenant", e);
        }
    }

    private List<TenantResponse> queryTenants(Query query) {
        try {
            ApiFuture<QuerySnapshot> future = query.get();
            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            List<TenantResponse> responses = new ArrayList<>(docs.size());
            for (QueryDocumentSnapshot doc : docs) {
                responses.add(toResponse(toPayload(doc)));
            }
            return responses;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying tenants", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to query tenants", e);
        }
    }

    private List<TenantPayload> getAllTenantPayloads() {
        try {
            List<QueryDocumentSnapshot> docs = tenants().get().get().getDocuments();
            List<TenantPayload> payloads = new ArrayList<>(docs.size());
            for (QueryDocumentSnapshot doc : docs) {
                TenantPayload payload = toPayload(doc);
                if (payload.id == null || payload.fullName == null || payload.fullName.isBlank()) {
                    continue;
                }
                payloads.add(payload);
            }
            return payloads;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying tenants", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to query tenants", e);
        }
    }

    private TenantPayload fromRequest(TenantRequest request) {
        TenantPayload payload = new TenantPayload();
        payload.fullName = request.getFullName();
        payload.tenantPhoneNumber = request.getTenantPhoneNumber();
        payload.dailyAccommodation = request.isDailyAccommodation();
        payload.dailyFoodOption = request.getDailyFoodOption();
        payload.dailyCollectionAmount = nvl(request.getDailyCollectionAmount());
        payload.dailyCollectionTransactionDate = request.getDailyCollectionTransactionDate();
        payload.dailyCollectionAccountId = request.getDailyCollectionAccountId();
        payload.dailyStayDays = request.getDailyStayDays();
        payload.roomNumber = request.getRoomNumber();
        payload.rent = nvl(request.getRent());
        payload.deposit = nvl(request.getDeposit());
        payload.joiningDate = request.getJoiningDate();
        payload.emergencyContactNumber = request.getEmergencyContactNumber();
        payload.emergencyContactRelationship = request.getEmergencyContactRelationship();
        payload.sharing = request.getSharing();
        payload.paymentStatus = request.getPaymentStatus();
        payload.companyName = request.getCompanyName();
        payload.companyAddress = request.getCompanyAddress();
        payload.rentDueAmount = nvl(request.getRentDueAmount());
        payload.rentPaidAmount = nvl(request.getRentPaidAmount());
        payload.depositPaidAmount = nvl(request.getDepositPaidAmount());
        payload.joiningCollectionAccountId = request.getJoiningCollectionAccountId();
        payload.verificationStatus = request.getVerificationStatus();
        return payload;
    }

    private Map<String, Object> toDocument(TenantPayload payload) {
        Map<String, Object> document = new HashMap<>();
        document.put("id", payload.id);
        document.put("fullName", payload.fullName);
        document.put("fullNameLower", payload.fullName == null ? null : payload.fullName.toLowerCase(Locale.ROOT));
        document.put("tenantPhoneNumber", payload.tenantPhoneNumber);
        document.put("dailyAccommodation", payload.dailyAccommodation);
        document.put("dailyFoodOption", enumName(payload.dailyFoodOption));
        document.put("dailyCollectionAmount", payload.dailyCollectionAmount.toPlainString());
        document.put("dailyCollectionTransactionDate", date(payload.dailyCollectionTransactionDate));
        document.put("dailyCollectionAccountId", payload.dailyCollectionAccountId);
        document.put("dailyStayDays", payload.dailyStayDays);
        document.put("roomNumber", payload.roomNumber);
        document.put("rent", payload.rent.toPlainString());
        document.put("deposit", payload.deposit.toPlainString());
        document.put("joiningDate", date(payload.joiningDate));
        document.put("emergencyContactNumber", payload.emergencyContactNumber);
        document.put("emergencyContactRelationship", payload.emergencyContactRelationship);
        document.put("sharing", enumName(payload.sharing));
        document.put("paymentStatus", enumName(payload.paymentStatus));
        document.put("companyName", payload.companyName);
        document.put("companyAddress", payload.companyAddress);
        document.put("rentDueAmount", payload.rentDueAmount.toPlainString());
        document.put("rentPaidAmount", payload.rentPaidAmount.toPlainString());
        document.put("depositPaidAmount", payload.depositPaidAmount.toPlainString());
        document.put("joiningCollectionAccountId", payload.joiningCollectionAccountId);
        document.put("verificationStatus", enumName(payload.verificationStatus));
        document.put("active", payload.active);
        document.put("checkoutDate", date(payload.checkoutDate));
        document.put("createdAt", timestamp(payload.createdAt));
        document.put("updatedAt", timestamp(payload.updatedAt));
        return document;
    }

    private TenantPayload toPayload(DocumentSnapshot snapshot) {
        TenantPayload payload = new TenantPayload();
        payload.documentId = snapshot.getId();
        payload.id = readLong(snapshot.get("id"));
        if (payload.id == null) {
            payload.id = readLong(snapshot.getId());
        }
        payload.fullName = readString(snapshot.get("fullName"));
        payload.tenantPhoneNumber = readString(snapshot.get("tenantPhoneNumber"));
        payload.dailyAccommodation = Boolean.TRUE.equals(snapshot.getBoolean("dailyAccommodation"));
        payload.dailyFoodOption = readEnum(DailyFoodOption.class, snapshot.get("dailyFoodOption"));
        payload.dailyCollectionAmount = readBigDecimal(snapshot.get("dailyCollectionAmount"));
        payload.dailyCollectionTransactionDate = readLocalDate(snapshot.get("dailyCollectionTransactionDate"));
        payload.dailyCollectionAccountId = readLong(snapshot.get("dailyCollectionAccountId"));
        payload.dailyStayDays = readInteger(snapshot.get("dailyStayDays"));
        payload.roomNumber = readString(snapshot.get("roomNumber"));
        payload.rent = readBigDecimal(snapshot.get("rent"));
        payload.deposit = readBigDecimal(snapshot.get("deposit"));
        payload.joiningDate = readLocalDate(snapshot.get("joiningDate"));
        payload.emergencyContactNumber = readString(snapshot.get("emergencyContactNumber"));
        payload.emergencyContactRelationship = readString(snapshot.get("emergencyContactRelationship"));
        payload.sharing = readEnum(SharingType.class, snapshot.get("sharing"));
        payload.paymentStatus = readEnum(PaymentStatus.class, snapshot.get("paymentStatus"));
        payload.companyName = readString(snapshot.get("companyName"));
        payload.companyAddress = readString(snapshot.get("companyAddress"));
        payload.rentDueAmount = readBigDecimal(snapshot.get("rentDueAmount"));
        payload.rentPaidAmount = readBigDecimal(snapshot.get("rentPaidAmount"));
        payload.depositPaidAmount = readBigDecimal(snapshot.get("depositPaidAmount"));
        payload.joiningCollectionAccountId = readLong(snapshot.get("joiningCollectionAccountId"));
        payload.verificationStatus = readEnum(VerificationStatus.class, snapshot.get("verificationStatus"));
        payload.active = Boolean.TRUE.equals(snapshot.getBoolean("active"));
        payload.checkoutDate = readLocalDate(snapshot.get("checkoutDate"));
        payload.createdAt = readOffsetDateTime(snapshot.get("createdAt"));
        payload.updatedAt = readOffsetDateTime(snapshot.get("updatedAt"));
        return payload;
    }

    private TenantResponse toResponse(TenantPayload payload) {
        TenantResponse response = new TenantResponse();
        response.setId(payload.id);
        response.setFullName(payload.fullName);
        response.setTenantPhoneNumber(payload.tenantPhoneNumber);
        response.setDailyAccommodation(payload.dailyAccommodation);
        response.setDailyFoodOption(payload.dailyFoodOption);
        response.setDailyCollectionAmount(payload.dailyCollectionAmount);
        response.setDailyCollectionTransactionDate(payload.dailyCollectionTransactionDate);
        response.setDailyCollectionAccountId(payload.dailyCollectionAccountId);
        response.setDailyCollectionAccountName(null);
        response.setDailyStayDays(payload.dailyStayDays);
        response.setRoomNumber(payload.roomNumber);
        response.setRent(payload.rent);
        response.setDeposit(payload.deposit);
        response.setJoiningDate(payload.joiningDate);
        response.setEmergencyContactNumber(payload.emergencyContactNumber);
        response.setEmergencyContactRelationship(payload.emergencyContactRelationship);
        response.setSharing(payload.sharing);
        response.setPaymentStatus(payload.paymentStatus);
        response.setCompanyName(payload.companyName);
        response.setCompanyAddress(payload.companyAddress);
        response.setRentDueAmount(payload.rentDueAmount);
        response.setRentPaidAmount(payload.rentPaidAmount);
        response.setDepositPaidAmount(payload.depositPaidAmount);
        response.setJoiningCollectionAccountId(payload.joiningCollectionAccountId);
        response.setJoiningCollectionAccountName(null);
        response.setVerificationStatus(payload.verificationStatus);
        response.setActive(payload.active);
        response.setCheckoutDate(payload.checkoutDate);
        response.setCreatedAt(payload.createdAt);
        response.setUpdatedAt(payload.updatedAt);
        return response;
    }

    private void normalizeFinancials(TenantPayload payload) {
        if (payload.dailyAccommodation) {
            BigDecimal dailyCollection = nvl(payload.dailyCollectionAmount).max(ZERO);
            payload.dailyCollectionAmount = dailyCollection;
            if (dailyCollection.compareTo(ZERO) > 0) {
                payload.dailyCollectionTransactionDate = payload.dailyCollectionTransactionDate != null
                        ? payload.dailyCollectionTransactionDate
                        : (payload.joiningDate != null ? payload.joiningDate : LocalDate.now());
            } else {
                payload.dailyCollectionTransactionDate = null;
                payload.dailyCollectionAccountId = null;
            }
            payload.dailyStayDays = payload.dailyStayDays == null ? 1 : Math.max(payload.dailyStayDays, 1);
            payload.deposit = ZERO;
            payload.depositPaidAmount = ZERO;
            payload.rentPaidAmount = ZERO;
            payload.rentDueAmount = ZERO;
            payload.paymentStatus = PaymentStatus.ON_TIME;
            return;
        }

        BigDecimal rent = nvl(payload.rent);
        BigDecimal deposit = nvl(payload.deposit);
        BigDecimal rentPaid = nvl(payload.rentPaidAmount).min(rent).max(ZERO);
        BigDecimal depositPaid = nvl(payload.depositPaidAmount).min(deposit).max(ZERO);
        BigDecimal rentDue = rent.subtract(rentPaid).max(ZERO);

        payload.rent = rent;
        payload.deposit = deposit;
        payload.rentPaidAmount = rentPaid;
        payload.depositPaidAmount = depositPaid;
        if (rentPaid.add(depositPaid).compareTo(ZERO) <= 0) {
            payload.joiningCollectionAccountId = null;
        }
        payload.rentDueAmount = rentDue;
        payload.dailyStayDays = null;
        payload.paymentStatus = rentDue.compareTo(ZERO) == 0
                ? PaymentStatus.ON_TIME
                : (rentPaid.compareTo(ZERO) > 0 ? PaymentStatus.PARTIAL : PaymentStatus.DUE);
    }

    private void normalizeIdentityAndContacts(TenantPayload payload) {
        payload.fullName = trimToNull(payload.fullName);
        payload.tenantPhoneNumber = trimToNull(payload.tenantPhoneNumber);
        payload.emergencyContactRelationship = trimToEmpty(payload.emergencyContactRelationship);
        payload.emergencyContactNumber = trimToEmpty(payload.emergencyContactNumber);
        payload.companyName = trimToNull(payload.companyName);
        payload.companyAddress = trimToNull(payload.companyAddress);
        if (!payload.dailyAccommodation) {
            payload.dailyFoodOption = null;
            payload.dailyCollectionAmount = ZERO;
            payload.dailyCollectionTransactionDate = null;
            payload.dailyCollectionAccountId = null;
        }
        if (payload.dailyAccommodation) {
            payload.joiningCollectionAccountId = null;
        }
        if (payload.active) {
            payload.checkoutDate = null;
        }
    }

    private void validateUniqueName(String fullName, Long currentTenantId) {
        if (fullName == null) {
            throw new BadRequestException("Tenant full name is required.");
        }
        try {
            QuerySnapshot snapshot = tenants()
                    .whereEqualTo("active", true)
                    .whereEqualTo("fullNameLower", fullName.toLowerCase(Locale.ROOT))
                    .get()
                    .get();
            for (DocumentSnapshot existing : snapshot.getDocuments()) {
                Long existingId = readLong(existing.get("id"));
                if (currentTenantId == null || !Objects.equals(existingId, currentTenantId)) {
                    throw new BadRequestException("Tenant name already exists. Use a unique full name.");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during tenant validation", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to validate tenant uniqueness", e);
        }
    }

    private OffsetDateTime readOffsetDateTime(Object value) {
        if (value == null) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (value instanceof Timestamp timestamp) {
            return OffsetDateTime.ofInstant(timestamp.toDate().toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof String str && !str.isBlank()) {
            return OffsetDateTime.parse(str);
        }
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String readString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String date(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private Timestamp timestamp(OffsetDateTime value) {
        Instant instant = (value == null ? OffsetDateTime.now(ZoneOffset.UTC) : value).toInstant();
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    private Long readLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer readInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal readBigDecimal(Object value) {
        if (value == null) {
            return ZERO;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private LocalDate readLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return LocalDate.parse(stringValue);
        }
        return null;
    }

    private <E extends Enum<E>> E readEnum(Class<E> enumType, Object value) {
        if (value == null) {
            return null;
        }
        return Enum.valueOf(enumType, String.valueOf(value));
    }

    private static final class TenantPayload {
        private String documentId;
        private Long id;
        private String fullName;
        private String tenantPhoneNumber;
        private boolean dailyAccommodation;
        private DailyFoodOption dailyFoodOption;
        private BigDecimal dailyCollectionAmount;
        private LocalDate dailyCollectionTransactionDate;
        private Long dailyCollectionAccountId;
        private Integer dailyStayDays;
        private String roomNumber;
        private BigDecimal rent;
        private BigDecimal deposit;
        private LocalDate joiningDate;
        private String emergencyContactNumber;
        private String emergencyContactRelationship;
        private SharingType sharing;
        private PaymentStatus paymentStatus;
        private String companyName;
        private String companyAddress;
        private BigDecimal rentDueAmount;
        private BigDecimal rentPaidAmount;
        private BigDecimal depositPaidAmount;
        private Long joiningCollectionAccountId;
        private VerificationStatus verificationStatus;
        private boolean active;
        private LocalDate checkoutDate;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
    }
}
