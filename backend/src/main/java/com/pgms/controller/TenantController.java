package com.pgms.controller;

import com.pgms.dto.TenantRequest;
import com.pgms.dto.TenantResponse;
import com.pgms.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public ResponseEntity<List<TenantResponse>> getAllTenants() {
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    @GetMapping("/active")
    public ResponseEntity<List<TenantResponse>> getActiveTenants() {
        return ResponseEntity.ok(tenantService.getActiveTenants());
    }

    @GetMapping("/daily")
    public ResponseEntity<List<TenantResponse>> getDailyTenants() {
        return ResponseEntity.ok(tenantService.getDailyTenants());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody TenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.createTenant(request));
    }

    @PutMapping(value = "/{tenantId:\\d+}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable Long tenantId,
            @Valid @RequestBody TenantRequest request
    ) {
        return ResponseEntity.ok(tenantService.updateTenant(tenantId, request));
    }

    @PatchMapping("/{tenantId:\\d+}/checkout")
    public ResponseEntity<TenantResponse> checkoutTenant(@PathVariable Long tenantId) {
        return ResponseEntity.ok(tenantService.checkoutTenant(tenantId));
    }

    @DeleteMapping("/{tenantId:\\d+}/daily-collection")
    public ResponseEntity<TenantResponse> clearDailyCollection(@PathVariable Long tenantId) {
        return ResponseEntity.ok(tenantService.clearDailyCollection(tenantId));
    }

    @DeleteMapping("/{tenantId:\\d+}")
    public ResponseEntity<Void> deleteTenant(@PathVariable Long tenantId) {
        tenantService.deleteTenant(tenantId);
        return ResponseEntity.noContent().build();
    }
}
