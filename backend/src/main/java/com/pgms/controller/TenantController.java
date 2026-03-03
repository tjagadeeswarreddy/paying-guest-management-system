package com.pgms.controller;

import com.pgms.config.CacheNames;
import com.pgms.dto.TenantRequest;
import com.pgms.dto.TenantResponse;
import com.pgms.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @Cacheable(cacheNames = CacheNames.TENANTS_ALL)
    public ResponseEntity<List<TenantResponse>> getAllTenants() {
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    @GetMapping("/active")
    @Cacheable(cacheNames = CacheNames.TENANTS_ACTIVE)
    public ResponseEntity<List<TenantResponse>> getActiveTenants() {
        return ResponseEntity.ok(tenantService.getActiveTenants());
    }

    @GetMapping("/daily")
    @Cacheable(cacheNames = CacheNames.TENANTS_DAILY)
    public ResponseEntity<List<TenantResponse>> getDailyTenants() {
        return ResponseEntity.ok(tenantService.getDailyTenants());
    }

    @GetMapping("/deleted")
    @Cacheable(cacheNames = CacheNames.TENANTS_DELETED)
    public ResponseEntity<List<TenantResponse>> getDeletedTenants() {
        return ResponseEntity.ok(tenantService.getDeletedTenants());
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportTenantsCsv() {
        List<TenantResponse> tenants = tenantService.getAllTenants();
        StringBuilder csv = new StringBuilder();
        csv.append("Id,Full Name,Phone,Room,Joining Date,Rent,Deposit,Rent Paid,Deposit Paid,Rent Due,Payment Status,Verification Status,Active,Daily Accommodation,Company Name,Company Address\n");
        for (TenantResponse tenant : tenants) {
            csv.append(csvCell(tenant.getId())).append(',')
                    .append(csvCell(tenant.getFullName())).append(',')
                    .append(csvCell(tenant.getTenantPhoneNumber())).append(',')
                    .append(csvCell(tenant.getRoomNumber())).append(',')
                    .append(csvCell(tenant.getJoiningDate())).append(',')
                    .append(csvCell(tenant.getRent())).append(',')
                    .append(csvCell(tenant.getDeposit())).append(',')
                    .append(csvCell(tenant.getRentPaidAmount())).append(',')
                    .append(csvCell(tenant.getDepositPaidAmount())).append(',')
                    .append(csvCell(tenant.getRentDueAmount())).append(',')
                    .append(csvCell(tenant.getPaymentStatus())).append(',')
                    .append(csvCell(tenant.getVerificationStatus())).append(',')
                    .append(csvCell(tenant.isActive())).append(',')
                    .append(csvCell(tenant.isDailyAccommodation())).append(',')
                    .append(csvCell(tenant.getCompanyName())).append(',')
                    .append(csvCell(tenant.getCompanyAddress()))
                    .append('\n');
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tenants.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ACTIVE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_DAILY, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_DELETED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_DUE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true)
    })
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody TenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.createTenant(request));
    }

    @PutMapping(value = "/{tenantId:\\d+}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ACTIVE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_DAILY, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_DELETED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_DUE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true)
    })
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable Long tenantId,
            @Valid @RequestBody TenantRequest request
    ) {
        return ResponseEntity.ok(tenantService.updateTenant(tenantId, request));
    }

    @PatchMapping("/{tenantId:\\d+}/checkout")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ACTIVE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_DAILY, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_DELETED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_DUE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true)
    })
    public ResponseEntity<TenantResponse> checkoutTenant(@PathVariable Long tenantId) {
        return ResponseEntity.ok(tenantService.checkoutTenant(tenantId));
    }

    @DeleteMapping("/{tenantId:\\d+}/daily-collection")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ACTIVE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_DAILY, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_DELETED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true)
    })
    public ResponseEntity<TenantResponse> clearDailyCollection(@PathVariable Long tenantId) {
        return ResponseEntity.ok(tenantService.clearDailyCollection(tenantId));
    }

    @DeleteMapping("/{tenantId:\\d+}")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ACTIVE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_DAILY, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_DELETED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_DUE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true)
    })
    public ResponseEntity<Void> deleteTenant(@PathVariable Long tenantId) {
        tenantService.deleteTenant(tenantId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{tenantId:\\d+}/restore")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ACTIVE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_DAILY, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_DELETED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_DUE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true)
    })
    public ResponseEntity<TenantResponse> restoreTenant(@PathVariable Long tenantId) {
        return ResponseEntity.ok(tenantService.restoreTenant(tenantId));
    }

    @DeleteMapping("/{tenantId:\\d+}/permanent")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TENANTS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_ACTIVE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_DAILY, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TENANTS_DELETED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_DUE, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENTS_COLLECTED, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.RENT_DASHBOARD, allEntries = true)
    })
    public ResponseEntity<Void> permanentlyDeleteTenant(@PathVariable Long tenantId) {
        tenantService.permanentlyDeleteTenant(tenantId);
        return ResponseEntity.noContent().build();
    }

    private String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        return "\"" + text + "\"";
    }
}
