package com.pgms.service;

import com.pgms.dto.DashboardSummaryResponse;
import com.pgms.dto.RentRecordRequest;
import com.pgms.dto.RentRecordResponse;
import com.pgms.dto.RentRecordUpdateRequest;

import java.time.LocalDate;
import java.util.List;

public interface RentService {
    RentRecordResponse upsertRentRecord(RentRecordRequest request);
    RentRecordResponse updateRentRecord(Long recordId, RentRecordUpdateRequest request);
    void deleteRentRecord(Long recordId);
    void deleteCollectedRecord(Long recordId);
    RentRecordResponse markAsPaid(Long recordId);
    List<RentRecordResponse> getDueRentRecords(LocalDate from, LocalDate to);
    List<RentRecordResponse> getCollectedRentRecords(LocalDate from, LocalDate to);
    byte[] exportCollectionReportCsv(LocalDate from, LocalDate to, Long accountId);
    DashboardSummaryResponse getDashboardSummary(LocalDate from, LocalDate to);
}
