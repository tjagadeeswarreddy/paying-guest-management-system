package com.pgms.service.impl;

import com.pgms.dto.RoomRequest;
import com.pgms.dto.RoomResponse;
import com.pgms.entity.Room;
import com.pgms.exception.BadRequestException;
import com.pgms.exception.ResourceNotFoundException;
import com.pgms.repository.RoomRepository;
import com.pgms.service.RoomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;

    public RoomServiceImpl(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Override
    public RoomResponse createRoom(RoomRequest request) {
        String roomNumber = normalizeRoomNumber(request.getRoomNumber());
        if (roomRepository.existsByRoomNumberIgnoreCase(roomNumber)) {
            throw new BadRequestException("Room already exists: " + roomNumber);
        }
        Room room = new Room();
        room.setRoomNumber(roomNumber);
        room.setBedCapacity(request.getBedCapacity());
        return toResponse(roomRepository.save(room));
    }

    @Override
    public RoomResponse updateRoom(Long roomId, RoomRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));

        String roomNumber = normalizeRoomNumber(request.getRoomNumber());
        if (roomRepository.existsByRoomNumberIgnoreCaseAndIdNot(roomNumber, roomId)) {
            throw new BadRequestException("Room already exists: " + roomNumber);
        }

        room.setRoomNumber(roomNumber);
        room.setBedCapacity(request.getBedCapacity());
        return toResponse(roomRepository.save(room));
    }

    @Override
    public void deleteRoom(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + roomId));
        roomRepository.delete(room);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getRooms() {
        return roomRepository.findAllByOrderByRoomNumberAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    private String normalizeRoomNumber(String roomNumber) {
        return roomNumber == null ? "" : roomNumber.trim().toUpperCase();
    }

    private RoomResponse toResponse(Room room) {
        RoomResponse response = new RoomResponse();
        response.setId(room.getId());
        response.setRoomNumber(room.getRoomNumber());
        response.setBedCapacity(room.getBedCapacity());
        return response;
    }
}
