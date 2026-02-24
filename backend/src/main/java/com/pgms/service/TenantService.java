package com.pgms.service;

import com.pgms.dto.TenantRequest;
import com.pgms.dto.TenantResponse;

import java.util.List;

public interface TenantService {
    TenantResponse createTenant(TenantRequest request);
    TenantResponse updateTenant(Long tenantId, TenantRequest request);
    TenantResponse checkoutTenant(Long tenantId);
    TenantResponse clearDailyCollection(Long tenantId);
    void deleteTenant(Long tenantId);
    List<TenantResponse> getActiveTenants();
    List<TenantResponse> getDailyTenants();
    List<TenantResponse> getAllTenants();
}
