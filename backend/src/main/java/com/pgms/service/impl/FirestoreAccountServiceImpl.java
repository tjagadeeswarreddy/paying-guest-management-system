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
import com.google.cloud.firestore.WriteBatch;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import com.pgms.dto.AccountRequest;
import com.pgms.dto.AccountResponse;
import com.pgms.exception.BadRequestException;
import com.pgms.exception.ResourceNotFoundException;
import com.pgms.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

@Service
@ConditionalOnProperty(name = "app.data-provider", havingValue = "firebase")
public class FirestoreAccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(FirestoreAccountServiceImpl.class);
    private static final String ACCOUNTS_COLLECTION = "accounts";
    private static final String TENANTS_COLLECTION = "tenants";
    private static final String EXPENSES_COLLECTION = "expenses";
    private static final String META_COLLECTION = "_meta";
    private static final String ACCOUNT_COUNTER_DOC = "accountSeq";

    private final Firestore firestore;

    public FirestoreAccountServiceImpl(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public AccountResponse createAccount(AccountRequest request) {
        String name = normalize(request.getName());
        String mode = normalize(request.getMode());
        validateAccountNameUnique(name, null);
        Long accountId = nextAccountId();

        AccountPayload payload = new AccountPayload();
        payload.id = accountId;
        payload.name = name;
        payload.mode = mode;
        writeAccount(payload);
        return toResponse(payload);
    }

    @Override
    public AccountResponse updateAccount(Long accountId, AccountRequest request) {
        AccountPayload payload = getAccountPayload(accountId);
        String name = normalize(request.getName());
        String mode = normalize(request.getMode());
        validateAccountNameUnique(name, accountId);
        payload.name = name;
        payload.mode = mode;
        writeAccount(payload);
        return toResponse(payload);
    }

    @Override
    public void deleteAccount(Long accountId) {
        getAccountPayload(accountId);
        clearTenantAccountReferences(accountId);
        clearExpenseAccountReferences(accountId);
        try {
            accounts().document(String.valueOf(accountId)).delete().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while deleting account", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to delete account", e);
        }
    }

    @Override
    public List<AccountResponse> getAccounts() {
        try {
            QuerySnapshot snapshot = accounts().orderBy("nameUpper", Query.Direction.ASCENDING).get().get();
            List<AccountResponse> responses = new ArrayList<>(snapshot.size());
            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                responses.add(toResponse(toPayload(doc)));
            }
            return responses;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying accounts", e);
        } catch (ExecutionException e) {
            if (isQuotaExceeded(e)) {
                log.warn("Firestore quota exceeded while querying accounts. Returning empty account list.");
                return List.of();
            }
            throw new IllegalStateException("Failed to query accounts", e);
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

    private CollectionReference accounts() {
        return firestore.collection(ACCOUNTS_COLLECTION);
    }

    private CollectionReference tenants() {
        return firestore.collection(TENANTS_COLLECTION);
    }

    private CollectionReference expenses() {
        return firestore.collection(EXPENSES_COLLECTION);
    }

    private Long nextAccountId() {
        try {
            return firestore.runTransaction(transaction -> {
                DocumentReference counterRef = firestore.collection(META_COLLECTION).document(ACCOUNT_COUNTER_DOC);
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
            throw new IllegalStateException("Interrupted while generating account id", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to generate account id", e);
        }
    }

    private AccountPayload getAccountPayload(Long accountId) {
        try {
            DocumentSnapshot snapshot = accounts().document(String.valueOf(accountId)).get().get();
            if (!snapshot.exists()) {
                throw new ResourceNotFoundException("Account not found with id: " + accountId);
            }
            return toPayload(snapshot);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading account", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to read account", e);
        }
    }

    private void validateAccountNameUnique(String name, Long currentAccountId) {
        try {
            QuerySnapshot snapshot = accounts()
                    .whereEqualTo("nameUpper", name.toUpperCase(Locale.ROOT))
                    .get()
                    .get();
            for (DocumentSnapshot existing : snapshot.getDocuments()) {
                Long existingId = readLong(existing.get("id"), existing.getId());
                if (currentAccountId == null || !Objects.equals(existingId, currentAccountId)) {
                    throw new BadRequestException("Account already exists: " + name);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while validating account", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to validate account", e);
        }
    }

    private void writeAccount(AccountPayload payload) {
        Map<String, Object> document = new HashMap<>();
        document.put("id", payload.id);
        document.put("name", payload.name);
        document.put("nameUpper", payload.name.toUpperCase(Locale.ROOT));
        document.put("mode", payload.mode);
        try {
            accounts().document(String.valueOf(payload.id)).set(document).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing account", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to write account", e);
        }
    }

    private void clearTenantAccountReferences(Long accountId) {
        try {
            QuerySnapshot dailyRefDocs = tenants().whereEqualTo("dailyCollectionAccountId", accountId).get().get();
            QuerySnapshot joiningRefDocs = tenants().whereEqualTo("joiningCollectionAccountId", accountId).get().get();

            WriteBatch batch = firestore.batch();
            for (DocumentSnapshot doc : dailyRefDocs.getDocuments()) {
                batch.update(doc.getReference(), "dailyCollectionAccountId", null);
            }
            for (DocumentSnapshot doc : joiningRefDocs.getDocuments()) {
                batch.update(doc.getReference(), "joiningCollectionAccountId", null);
            }
            batch.commit().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while clearing account references", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to clear account references", e);
        }
    }

    private void clearExpenseAccountReferences(Long accountId) {
        try {
            QuerySnapshot expenseDocs = expenses().whereEqualTo("accountId", accountId).get().get();
            WriteBatch batch = firestore.batch();
            for (DocumentSnapshot doc : expenseDocs.getDocuments()) {
                batch.update(doc.getReference(), "accountId", null);
                batch.update(doc.getReference(), "accountName", null);
            }
            batch.commit().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while clearing expense account references", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to clear expense account references", e);
        }
    }

    private AccountPayload toPayload(DocumentSnapshot snapshot) {
        AccountPayload payload = new AccountPayload();
        payload.id = readLong(snapshot.get("id"), snapshot.getId());
        payload.name = String.valueOf(snapshot.get("name"));
        payload.mode = String.valueOf(snapshot.get("mode"));
        return payload;
    }

    private AccountResponse toResponse(AccountPayload payload) {
        AccountResponse response = new AccountResponse();
        response.setId(payload.id);
        response.setName(payload.name);
        response.setMode(payload.mode);
        return response;
    }

    private Long readLong(Object value, String fallbackId) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            return Long.parseLong(String.valueOf(value));
        }
        return Long.parseLong(fallbackId);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class AccountPayload {
        private Long id;
        private String name;
        private String mode;
    }
}
