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
import com.pgms.dto.ExpenseRequest;
import com.pgms.dto.ExpenseResponse;
import com.pgms.exception.ResourceNotFoundException;
import com.pgms.service.ExpenseService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@ConditionalOnProperty(name = "app.data-provider", havingValue = "firebase")
public class FirestoreExpenseServiceImpl implements ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(FirestoreExpenseServiceImpl.class);
    private static final String EXPENSES_COLLECTION = "expenses";
    private static final String ACCOUNTS_COLLECTION = "accounts";
    private static final String META_COLLECTION = "_meta";
    private static final String EXPENSE_COUNTER_DOC = "expenseSeq";

    private final Firestore firestore;

    public FirestoreExpenseServiceImpl(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public ExpenseResponse createExpense(ExpenseRequest request) {
        ExpensePayload payload = new ExpensePayload();
        payload.id = nextExpenseId();
        applyRequest(payload, request);
        payload.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        payload.updatedAt = payload.createdAt;
        writeExpense(payload);
        return toResponse(payload);
    }

    @Override
    public ExpenseResponse updateExpense(Long expenseId, ExpenseRequest request) {
        ExpensePayload payload = getExpensePayload(expenseId);
        applyRequest(payload, request);
        payload.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        writeExpense(payload);
        return toResponse(payload);
    }

    @Override
    public void deleteExpense(Long expenseId) {
        getExpensePayload(expenseId);
        try {
            expenses().document(String.valueOf(expenseId)).delete().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while deleting expense", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to delete expense", e);
        }
    }

    @Override
    public List<ExpenseResponse> getExpenses() {
        try {
            QuerySnapshot snapshot = expenses().get().get();
            List<ExpenseResponse> rows = new ArrayList<>(snapshot.size());
            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                rows.add(toResponse(toPayload(doc)));
            }
            rows.sort((a, b) -> {
                int dateCompare = String.valueOf(b.getTransactionDate()).compareTo(String.valueOf(a.getTransactionDate()));
                if (dateCompare != 0) return dateCompare;
                return Long.compare(b.getId() == null ? 0L : b.getId(), a.getId() == null ? 0L : a.getId());
            });
            return rows;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying expenses", e);
        } catch (ExecutionException e) {
            if (isQuotaExceeded(e)) {
                log.warn("Firestore quota exceeded while querying expenses. Returning empty expenses list.");
                return List.of();
            }
            throw new IllegalStateException("Failed to query expenses", e);
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

    private CollectionReference expenses() {
        return firestore.collection(EXPENSES_COLLECTION);
    }

    private CollectionReference accounts() {
        return firestore.collection(ACCOUNTS_COLLECTION);
    }

    private Long nextExpenseId() {
        try {
            return firestore.runTransaction(transaction -> {
                DocumentReference counterRef = firestore.collection(META_COLLECTION).document(EXPENSE_COUNTER_DOC);
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
            throw new IllegalStateException("Interrupted while generating expense id", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to generate expense id", e);
        }
    }

    private ExpensePayload getExpensePayload(Long expenseId) {
        try {
            DocumentSnapshot snapshot = expenses().document(String.valueOf(expenseId)).get().get();
            if (!snapshot.exists()) {
                throw new ResourceNotFoundException("Expense not found with id: " + expenseId);
            }
            return toPayload(snapshot);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading expense", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to read expense", e);
        }
    }

    private void applyRequest(ExpensePayload payload, ExpenseRequest request) {
        payload.description = request.getDescription() == null ? "" : request.getDescription().trim();
        payload.amount = request.getAmount() == null ? BigDecimal.ZERO : request.getAmount().max(BigDecimal.ZERO);
        payload.transactionDate = request.getTransactionDate();
        payload.tag = request.getTag() == null ? null : request.getTag().trim();
        payload.accountId = request.getAccountId();
        payload.accountName = resolveAccountName(payload.accountId);
    }

    private String resolveAccountName(Long accountId) {
        if (accountId == null) return null;
        try {
            DocumentSnapshot snapshot = accounts().document(String.valueOf(accountId)).get().get();
            if (!snapshot.exists()) {
                throw new ResourceNotFoundException("Account not found with id: " + accountId);
            }
            Object name = snapshot.get("name");
            return name == null ? null : String.valueOf(name);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading account", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to read account", e);
        }
    }

    private void writeExpense(ExpensePayload payload) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", payload.id);
        doc.put("description", payload.description);
        doc.put("amount", payload.amount.toPlainString());
        doc.put("transactionDate", payload.transactionDate != null ? payload.transactionDate.toString() : null);
        doc.put("tag", payload.tag);
        doc.put("accountId", payload.accountId);
        doc.put("accountName", payload.accountName);
        doc.put("createdAt", timestamp(payload.createdAt));
        doc.put("updatedAt", timestamp(payload.updatedAt));
        try {
            expenses().document(String.valueOf(payload.id)).set(doc).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing expense", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to write expense", e);
        }
    }

    private ExpensePayload toPayload(DocumentSnapshot snapshot) {
        ExpensePayload payload = new ExpensePayload();
        payload.id = readLong(snapshot.get("id"), snapshot.getId());
        payload.description = asString(snapshot.get("description"));
        payload.amount = toBigDecimal(snapshot.get("amount"));
        payload.transactionDate = parseDate(snapshot.get("transactionDate"));
        payload.tag = asString(snapshot.get("tag"));
        payload.accountId = readLong(snapshot.get("accountId"), null);
        payload.accountName = asString(snapshot.get("accountName"));
        payload.createdAt = toOffsetDateTime(snapshot.get("createdAt"));
        payload.updatedAt = toOffsetDateTime(snapshot.get("updatedAt"));
        return payload;
    }

    private ExpenseResponse toResponse(ExpensePayload payload) {
        ExpenseResponse response = new ExpenseResponse();
        response.setId(payload.id);
        response.setDescription(payload.description);
        response.setAmount(payload.amount);
        response.setTransactionDate(payload.transactionDate);
        response.setTag(payload.tag);
        response.setAccountId(payload.accountId);
        response.setAccountName(payload.accountName);
        response.setCreatedAt(payload.createdAt);
        response.setUpdatedAt(payload.updatedAt);
        return response;
    }

    private Timestamp timestamp(OffsetDateTime value) {
        OffsetDateTime safe = value == null ? OffsetDateTime.now(ZoneOffset.UTC) : value;
        return Timestamp.ofTimeSecondsAndNanos(safe.toEpochSecond(), safe.getNano());
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
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(String.valueOf(value));
    }

    private LocalDate parseDate(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value);
        return text.isBlank() ? null : LocalDate.parse(text);
    }

    private static final class ExpensePayload {
        private Long id;
        private String description;
        private BigDecimal amount = BigDecimal.ZERO;
        private LocalDate transactionDate;
        private String tag;
        private Long accountId;
        private String accountName;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
    }
}
