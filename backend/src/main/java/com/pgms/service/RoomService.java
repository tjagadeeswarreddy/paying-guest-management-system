package com.pgms.service;

import com.pgms.dto.RoomRequest;
import com.pgms.dto.RoomResponse;

import java.util.List;

public interface RoomService {
    RoomResponse createRoom(RoomRequest request);
    RoomResponse updateRoom(Long roomId, RoomRequest request);
    void deleteRoom(Long roomId);
    List<RoomResponse> getRooms();
}
