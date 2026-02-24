package com.pgms.dto;

import java.math.BigDecimal;

public class DashboardSummaryResponse {
    private BigDecimal totalRentCollection;
    private BigDecimal totalDueAmount;
    private BigDecimal totalPendingCollection;
    private long activeTenants;

    public BigDecimal getTotalRentCollection() {
        return totalRentCollection;
    }

    public void setTotalRentCollection(BigDecimal totalRentCollection) {
        this.totalRentCollection = totalRentCollection;
    }

    public BigDecimal getTotalDueAmount() {
        return totalDueAmount;
    }

    public void setTotalDueAmount(BigDecimal totalDueAmount) {
        this.totalDueAmount = totalDueAmount;
    }

    public BigDecimal getTotalPendingCollection() {
        return totalPendingCollection;
    }

    public void setTotalPendingCollection(BigDecimal totalPendingCollection) {
        this.totalPendingCollection = totalPendingCollection;
    }

    public long getActiveTenants() {
        return activeTenants;
    }

    public void setActiveTenants(long activeTenants) {
        this.activeTenants = activeTenants;
    }
}
