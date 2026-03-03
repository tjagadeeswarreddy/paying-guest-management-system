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
import com.pgms.dto.RoomRequest;
import com.pgms.dto.RoomResponse;
import com.pgms.exception.BadRequestException;
import com.pgms.exception.ResourceNotFoundException;
import com.pgms.service.RoomService;
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
public class FirestoreRoomServiceImpl implements RoomService {

    private static final String ROOMS_COLLECTION = "rooms";
    private static final String META_COLLECTION = "_meta";
    private static final String ROOM_COUNTER_DOC = "roomSeq";

    private final Firestore firestore;

    public FirestoreRoomServiceImpl(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public RoomResponse createRoom(RoomRequest request) {
        String roomNumber = normalizeRoomNumber(request.getRoomNumber());
        validateRoomNumberUnique(roomNumber, null);
        Long roomId = nextRoomId();

        RoomPayload payload = new RoomPayload();
        payload.id = roomId;
        payload.roomNumber = roomNumber;
        payload.bedCapacity = request.getBedCapacity();
        writeRoom(payload);
        return toResponse(payload);
    }

    @Override
    public RoomResponse updateRoom(Long roomId, RoomRequest request) {
        RoomPayload existing = getRoomPayload(roomId);
        String roomNumber = normalizeRoomNumber(request.getRoomNumber());
        validateRoomNumberUnique(roomNumber, roomId);

        existing.roomNumber = roomNumber;
        existing.bedCapacity = request.getBedCapacity();
        writeRoom(existing);
        return toResponse(existing);
    }

    @Override
    public void deleteRoom(Long roomId) {
        getRoomPayload(roomId);
        try {
            rooms().document(String.valueOf(roomId)).delete().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while deleting room", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to delete room", e);
        }
    }

    @Override
    public List<RoomResponse> getRooms() {
        try {
            QuerySnapshot snapshot = rooms().orderBy("roomNumberUpper", Query.Direction.ASCENDING).get().get();
            List<RoomResponse> responses = new ArrayList<>(snapshot.size());
            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                responses.add(toResponse(toPayload(doc)));
            }
            return responses;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying rooms", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to query rooms", e);
        }
    }

    private CollectionReference rooms() {
        return firestore.collection(ROOMS_COLLECTION);
    }

    private Long nextRoomId() {
        try {
            return firestore.runTransaction(transaction -> {
                DocumentReference counterRef = firestore.collection(META_COLLECTION).document(ROOM_COUNTER_DOC);
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
            throw new IllegalStateException("Interrupted while generating room id", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to generate room id", e);
        }
    }

    private RoomPayload getRoomPayload(Long roomId) {
        try {
            DocumentSnapshot snapshot = rooms().document(String.valueOf(roomId)).get().get();
            if (!snapshot.exists()) {
                throw new ResourceNotFoundException("Room not found with id: " + roomId);
            }
            return toPayload(snapshot);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading room", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to read room", e);
        }
    }

    private void validateRoomNumberUnique(String roomNumber, Long currentRoomId) {
        try {
            QuerySnapshot snapshot = rooms()
                    .whereEqualTo("roomNumberUpper", roomNumber.toUpperCase(Locale.ROOT))
                    .get()
                    .get();
            for (DocumentSnapshot existing : snapshot.getDocuments()) {
                Long existingId = readLong(existing.get("id"), existing.getId());
                if (currentRoomId == null || !Objects.equals(existingId, currentRoomId)) {
                    throw new BadRequestException("Room already exists: " + roomNumber);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while validating room", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to validate room", e);
        }
    }

    private void writeRoom(RoomPayload payload) {
        Map<String, Object> document = new HashMap<>();
        document.put("id", payload.id);
        document.put("roomNumber", payload.roomNumber);
        document.put("roomNumberUpper", payload.roomNumber.toUpperCase(Locale.ROOT));
        document.put("bedCapacity", payload.bedCapacity);
        try {
            rooms().document(String.valueOf(payload.id)).set(document).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while writing room", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to write room", e);
        }
    }

    private RoomPayload toPayload(DocumentSnapshot snapshot) {
        RoomPayload payload = new RoomPayload();
        payload.id = readLong(snapshot.get("id"), snapshot.getId());
        payload.roomNumber = String.valueOf(snapshot.get("roomNumber"));
        payload.bedCapacity = ((Number) snapshot.get("bedCapacity")).intValue();
        return payload;
    }

    private RoomResponse toResponse(RoomPayload payload) {
        RoomResponse response = new RoomResponse();
        response.setId(payload.id);
        response.setRoomNumber(payload.roomNumber);
        response.setBedCapacity(payload.bedCapacity);
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

    private String normalizeRoomNumber(String roomNumber) {
        return roomNumber == null ? "" : roomNumber.trim().toUpperCase(Locale.ROOT);
    }

    private static final class RoomPayload {
        private Long id;
        private String roomNumber;
        private Integer bedCapacity;
    }
}
