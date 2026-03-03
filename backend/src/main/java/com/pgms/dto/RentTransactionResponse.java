package com.pgms.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class RentTransactionResponse {
    private Long id;
    private Long dueRentId;
    private Long tenantId;
    private String tenantName;
    private String roomNumber;
    private BigDecimal paidAmount;
    private Long accountId;
    private String accountName;
    private OffsetDateTime transactionAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDueRentId() {
        return dueRentId;
    }

    public void setDueRentId(Long dueRentId) {
        this.dueRentId = dueRentId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public OffsetDateTime getTransactionAt() {
        return transactionAt;
    }

    public void setTransactionAt(OffsetDateTime transactionAt) {
        this.transactionAt = transactionAt;
    }
}
