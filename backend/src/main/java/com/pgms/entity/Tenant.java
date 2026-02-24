package com.pgms.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(length = 10)
    private String tenantPhoneNumber;

    @Column
    private Boolean dailyAccommodation;

    @Enumerated(EnumType.STRING)
    private DailyFoodOption dailyFoodOption;

    @Column(precision = 12, scale = 2)
    private BigDecimal dailyCollectionAmount = BigDecimal.ZERO;

    @Column
    private LocalDate dailyCollectionTransactionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_collection_account_id")
    private Account dailyCollectionAccount;

    @Column
    private Integer dailyStayDays;

    @Column(nullable = false)
    private String roomNumber;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal rent;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deposit;

    @Column(nullable = false)
    private LocalDate joiningDate;

    @Column
    private String emergencyContactNumber;

    @Column
    private String emergencyContactRelationship;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SharingType sharing;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    private String companyName;

    private String companyAddress;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal rentDueAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal rentPaidAmount = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal depositPaidAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "joining_collection_account_id")
    private Account joiningCollectionAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus;

    @Column(nullable = false)
    private boolean active = true;

    private LocalDate lastDueGeneratedFor;
    private LocalDate checkoutDate;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getTenantPhoneNumber() {
        return tenantPhoneNumber;
    }

    public void setTenantPhoneNumber(String tenantPhoneNumber) {
        this.tenantPhoneNumber = tenantPhoneNumber;
    }

    public boolean isDailyAccommodation() {
        return Boolean.TRUE.equals(dailyAccommodation);
    }

    public void setDailyAccommodation(boolean dailyAccommodation) {
        this.dailyAccommodation = dailyAccommodation;
    }

    public DailyFoodOption getDailyFoodOption() {
        return dailyFoodOption;
    }

    public void setDailyFoodOption(DailyFoodOption dailyFoodOption) {
        this.dailyFoodOption = dailyFoodOption;
    }

    public BigDecimal getDailyCollectionAmount() {
        return dailyCollectionAmount;
    }

    public void setDailyCollectionAmount(BigDecimal dailyCollectionAmount) {
        this.dailyCollectionAmount = dailyCollectionAmount;
    }

    public LocalDate getDailyCollectionTransactionDate() {
        return dailyCollectionTransactionDate;
    }

    public void setDailyCollectionTransactionDate(LocalDate dailyCollectionTransactionDate) {
        this.dailyCollectionTransactionDate = dailyCollectionTransactionDate;
    }

    public Account getDailyCollectionAccount() {
        return dailyCollectionAccount;
    }

    public void setDailyCollectionAccount(Account dailyCollectionAccount) {
        this.dailyCollectionAccount = dailyCollectionAccount;
    }

    public Integer getDailyStayDays() {
        return dailyStayDays;
    }

    public void setDailyStayDays(Integer dailyStayDays) {
        this.dailyStayDays = dailyStayDays;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public BigDecimal getRent() {
        return rent;
    }

    public void setRent(BigDecimal rent) {
        this.rent = rent;
    }

    public BigDecimal getDeposit() {
        return deposit;
    }

    public void setDeposit(BigDecimal deposit) {
        this.deposit = deposit;
    }

    public LocalDate getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(LocalDate joiningDate) {
        this.joiningDate = joiningDate;
    }

    public String getEmergencyContactNumber() {
        return emergencyContactNumber;
    }

    public void setEmergencyContactNumber(String emergencyContactNumber) {
        this.emergencyContactNumber = emergencyContactNumber;
    }

    public String getEmergencyContactRelationship() {
        return emergencyContactRelationship;
    }

    public void setEmergencyContactRelationship(String emergencyContactRelationship) {
        this.emergencyContactRelationship = emergencyContactRelationship;
    }

    public SharingType getSharing() {
        return sharing;
    }

    public void setSharing(SharingType sharing) {
        this.sharing = sharing;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCompanyAddress() {
        return companyAddress;
    }

    public void setCompanyAddress(String companyAddress) {
        this.companyAddress = companyAddress;
    }

    public BigDecimal getRentDueAmount() {
        return rentDueAmount;
    }

    public void setRentDueAmount(BigDecimal rentDueAmount) {
        this.rentDueAmount = rentDueAmount;
    }

    public BigDecimal getRentPaidAmount() {
        return rentPaidAmount;
    }

    public void setRentPaidAmount(BigDecimal rentPaidAmount) {
        this.rentPaidAmount = rentPaidAmount;
    }

    public BigDecimal getDepositPaidAmount() {
        return depositPaidAmount;
    }

    public void setDepositPaidAmount(BigDecimal depositPaidAmount) {
        this.depositPaidAmount = depositPaidAmount;
    }

    public Account getJoiningCollectionAccount() {
        return joiningCollectionAccount;
    }

    public void setJoiningCollectionAccount(Account joiningCollectionAccount) {
        this.joiningCollectionAccount = joiningCollectionAccount;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDate getLastDueGeneratedFor() {
        return lastDueGeneratedFor;
    }

    public void setLastDueGeneratedFor(LocalDate lastDueGeneratedFor) {
        this.lastDueGeneratedFor = lastDueGeneratedFor;
    }

    public LocalDate getCheckoutDate() {
        return checkoutDate;
    }

    public void setCheckoutDate(LocalDate checkoutDate) {
        this.checkoutDate = checkoutDate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
