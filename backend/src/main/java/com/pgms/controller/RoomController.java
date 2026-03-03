package com.pgms.controller;

import com.pgms.config.CacheNames;
import com.pgms.dto.RoomRequest;
import com.pgms.dto.RoomResponse;
import com.pgms.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    @Cacheable(cacheNames = CacheNames.ROOMS)
    public ResponseEntity<List<RoomResponse>> getRooms() {
        return ResponseEntity.ok(roomService.getRooms());
    }

    @PostMapping
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.ROOMS, allEntries = true)
    })
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(request));
    }

    @PutMapping("/{roomId}")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.ROOMS, allEntries = true)
    })
    public ResponseEntity<RoomResponse> updateRoom(@PathVariable Long roomId, @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.ok(roomService.updateRoom(roomId, request));
    }

    @DeleteMapping("/{roomId}")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.ROOMS, allEntries = true)
    })
    public ResponseEntity<Void> deleteRoom(@PathVariable Long roomId) {
        roomService.deleteRoom(roomId);
        return ResponseEntity.noContent().build();
    }
}
