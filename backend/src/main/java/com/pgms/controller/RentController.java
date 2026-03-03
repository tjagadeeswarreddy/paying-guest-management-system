package com.pgms.controller;

import com.pgms.config.CacheNames;
import com.pgms.dto.DashboardSummaryResponse;
import com.pgms.dto.RentRecordRequest;
import com.pgms.dto.RentRecordResponse;
import com.pgms.dto.RentRecordUpdateRequest;
import com.pgms.dto.RentTransactionResponse;
import com.pgms.service.RentService;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rents")
public class RentController {

    private final RentService rentService;

    public RentController(RentService rentService) {
        this.rentService = rentService;
    }

    @PostMapping
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.RENTS_DUE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ACTIVE, allEntries = true)
    })
    public ResponseEntity<RentRecordResponse> upsertRent(@Valid @RequestBody RentRecordRequest request) {
        return ResponseEntity.ok(rentService.upsertRentRecord(request));
    }

    @PutMapping("/{recordId}")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.RENTS_DUE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ACTIVE, allEntries = true)
    })
    public ResponseEntity<RentRecordResponse> updateRent(
            @PathVariable Long recordId,
            @Valid @RequestBody RentRecordUpdateRequest request
    ) {
        return ResponseEntity.ok(rentService.updateRentRecord(recordId, request));
    }

    @DeleteMapping("/{recordId}")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.RENTS_DUE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ACTIVE, allEntries = true)
    })
    public ResponseEntity<Void> deleteRent(@PathVariable Long recordId) {
        rentService.deleteRentRecord(recordId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/collected/{recordId}")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.RENTS_DUE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ACTIVE, allEntries = true)
    })
    public ResponseEntity<Void> deleteCollected(@PathVariable Long recordId) {
        rentService.deleteCollectedRecord(recordId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{recordId}/pay")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.RENTS_DUE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ACTIVE, allEntries = true)
    })
    public ResponseEntity<RentRecordResponse> markPaid(@PathVariable Long recordId) {
        return ResponseEntity.ok(rentService.markAsPaid(recordId));
    }

    @GetMapping("/due")
    @Cacheable(cacheNames = CacheNames.RENTS_DUE, key = "T(java.lang.String).valueOf(#from) + '|' + T(java.lang.String).valueOf(#to)")
    public ResponseEntity<List<RentRecordResponse>> getDueRents(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return ResponseEntity.ok(rentService.getDueRentRecords(from, to));
    }

    @GetMapping("/collected")
    @Cacheable(cacheNames = CacheNames.RENTS_COLLECTED, key = "T(java.lang.String).valueOf(#from) + '|' + T(java.lang.String).valueOf(#to)")
    public ResponseEntity<List<RentRecordResponse>> getCollectedRents(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return ResponseEntity.ok(rentService.getCollectedRentRecords(from, to));
    }

    @GetMapping("/{recordId}/transactions")
    public ResponseEntity<List<RentTransactionResponse>> getRentTransactions(@PathVariable Long recordId) {
        return ResponseEntity.ok(rentService.getRentTransactions(recordId));
    }

    @GetMapping(value = "/collected/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportCollectedRents(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long accountId
    ) {
        byte[] content = rentService.exportCollectionReportCsv(from, to, accountId);
        String filename = "collection-report.csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(content);
    }

    @GetMapping("/dashboard")
    @Cacheable(cacheNames = CacheNames.RENT_DASHBOARD, key = "T(java.lang.String).valueOf(#from) + '|' + T(java.lang.String).valueOf(#to)")
    public ResponseEntity<DashboardSummaryResponse> getDashboard(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return ResponseEntity.ok(rentService.getDashboardSummary(from, to));
    }
}
