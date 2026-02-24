package com.pgms.dto;

import com.pgms.entity.DailyFoodOption;
import com.pgms.entity.PaymentStatus;
import com.pgms.entity.SharingType;
import com.pgms.entity.VerificationStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TenantRequest {

    @NotBlank
    private String fullName;

    @NotBlank
    @Pattern(regexp = "^\\d{10}$", message = "Tenant phone number must be exactly 10 digits")
    private String tenantPhoneNumber;

    private boolean dailyAccommodation;

    private DailyFoodOption dailyFoodOption;

    @DecimalMin("0.0")
    private BigDecimal dailyCollectionAmount;

    private LocalDate dailyCollectionTransactionDate;

    private Long dailyCollectionAccountId;

    @Min(value = 1, message = "Stay days must be at least 1")
    private Integer dailyStayDays;

    @NotBlank
    private String roomNumber;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal rent;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal deposit;

    @NotNull
    private LocalDate joiningDate;

    @Pattern(regexp = "^$|^\\d{10}$", message = "Emergency contact number must be exactly 10 digits")
    private String emergencyContactNumber;

    private String emergencyContactRelationship;

    @NotNull
    private SharingType sharing;

    @NotNull
    private PaymentStatus paymentStatus;

    private String companyName;

    private String companyAddress;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal rentDueAmount;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal rentPaidAmount;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal depositPaidAmount;

    private Long joiningCollectionAccountId;

    @NotNull
    private VerificationStatus verificationStatus;

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
        return dailyAccommodation;
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

    public Long getDailyCollectionAccountId() {
        return dailyCollectionAccountId;
    }

    public void setDailyCollectionAccountId(Long dailyCollectionAccountId) {
        this.dailyCollectionAccountId = dailyCollectionAccountId;
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

    public Long getJoiningCollectionAccountId() {
        return joiningCollectionAccountId;
    }

    public void setJoiningCollectionAccountId(Long joiningCollectionAccountId) {
        this.joiningCollectionAccountId = joiningCollectionAccountId;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }
}
