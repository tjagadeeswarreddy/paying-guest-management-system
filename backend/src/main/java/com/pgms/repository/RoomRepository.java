package com.pgms.repository;

import com.pgms.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    boolean existsByRoomNumberIgnoreCase(String roomNumber);
    boolean existsByRoomNumberIgnoreCaseAndIdNot(String roomNumber, Long id);
    List<Room> findAllByOrderByRoomNumberAsc();
}
