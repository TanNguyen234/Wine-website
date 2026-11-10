package com.strongwine.strongwine.dto;

import java.time.LocalDate;

/**
 * Filter criteria for revenue report generation
 */
public class RevenueReportCriteria {

    private Integer days;
    private LocalDate fromDate;
    private LocalDate toDate;

    public RevenueReportCriteria() {
    }

    public RevenueReportCriteria(Integer days, LocalDate fromDate, LocalDate toDate) {
        this.days = days;
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }
}
