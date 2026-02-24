package com.pgms.mapper;

import com.pgms.dto.TenantRequest;
import com.pgms.dto.TenantResponse;
import com.pgms.entity.Tenant;

public final class TenantMapper {

    private TenantMapper() {
    }

    public static Tenant toEntity(TenantRequest request) {
        Tenant tenant = new Tenant();
        updateEntity(tenant, request);
        return tenant;
    }

    public static void updateEntity(Tenant tenant, TenantRequest request) {
        tenant.setFullName(request.getFullName());
        tenant.setTenantPhoneNumber(request.getTenantPhoneNumber());
        tenant.setDailyAccommodation(request.isDailyAccommodation());
        tenant.setDailyFoodOption(request.getDailyFoodOption());
        tenant.setDailyCollectionAmount(request.getDailyCollectionAmount());
        tenant.setDailyCollectionTransactionDate(request.getDailyCollectionTransactionDate());
        tenant.setDailyCollectionAccount(null);
        tenant.setDailyStayDays(request.getDailyStayDays());
        tenant.setRoomNumber(request.getRoomNumber());
        tenant.setRent(request.getRent());
        tenant.setDeposit(request.getDeposit());
        tenant.setJoiningDate(request.getJoiningDate());
        tenant.setEmergencyContactNumber(request.getEmergencyContactNumber());
        tenant.setEmergencyContactRelationship(request.getEmergencyContactRelationship());
        tenant.setSharing(request.getSharing());
        tenant.setPaymentStatus(request.getPaymentStatus());
        tenant.setCompanyName(request.getCompanyName());
        tenant.setCompanyAddress(request.getCompanyAddress());
        tenant.setRentDueAmount(request.getRentDueAmount());
        tenant.setRentPaidAmount(request.getRentPaidAmount());
        tenant.setDepositPaidAmount(request.getDepositPaidAmount());
        tenant.setJoiningCollectionAccount(null);
        tenant.setVerificationStatus(request.getVerificationStatus());
    }

    public static TenantResponse toResponse(Tenant tenant) {
        TenantResponse response = new TenantResponse();
        response.setId(tenant.getId());
        response.setFullName(tenant.getFullName());
        response.setTenantPhoneNumber(tenant.getTenantPhoneNumber());
        response.setDailyAccommodation(tenant.isDailyAccommodation());
        response.setDailyFoodOption(tenant.getDailyFoodOption());
        response.setDailyCollectionAmount(tenant.getDailyCollectionAmount());
        response.setDailyCollectionTransactionDate(tenant.getDailyCollectionTransactionDate());
        response.setDailyCollectionAccountId(
                tenant.getDailyCollectionAccount() != null ? tenant.getDailyCollectionAccount().getId() : null
        );
        response.setDailyCollectionAccountName(
                tenant.getDailyCollectionAccount() != null ? tenant.getDailyCollectionAccount().getName() : null
        );
        response.setDailyStayDays(tenant.getDailyStayDays());
        response.setRoomNumber(tenant.getRoomNumber());
        response.setRent(tenant.getRent());
        response.setDeposit(tenant.getDeposit());
        response.setJoiningDate(tenant.getJoiningDate());
        response.setEmergencyContactNumber(tenant.getEmergencyContactNumber());
        response.setEmergencyContactRelationship(tenant.getEmergencyContactRelationship());
        response.setSharing(tenant.getSharing());
        response.setPaymentStatus(tenant.getPaymentStatus());
        response.setCompanyName(tenant.getCompanyName());
        response.setCompanyAddress(tenant.getCompanyAddress());
        response.setRentDueAmount(tenant.getRentDueAmount());
        response.setRentPaidAmount(tenant.getRentPaidAmount());
        response.setDepositPaidAmount(tenant.getDepositPaidAmount());
        response.setJoiningCollectionAccountId(
                tenant.getJoiningCollectionAccount() != null ? tenant.getJoiningCollectionAccount().getId() : null
        );
        response.setJoiningCollectionAccountName(
                tenant.getJoiningCollectionAccount() != null ? tenant.getJoiningCollectionAccount().getName() : null
        );
        response.setVerificationStatus(tenant.getVerificationStatus());
        response.setActive(tenant.isActive());
        response.setCheckoutDate(tenant.getCheckoutDate());
        response.setCreatedAt(tenant.getCreatedAt());
        return response;
    }
}
