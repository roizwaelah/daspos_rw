package com.daspos.ui.state;

import com.daspos.model.ReportItem;

import java.util.List;

public class ReportUiState {
    public enum Status {
        IDLE,
        LOADING,
        SUCCESS,
        EMPTY,
        ERROR
    }

    private final Status status;
    private final List<ReportItem> items;
    private final int count;
    private final double income;
    private final String message;

    public ReportUiState(Status status, List<ReportItem> items, int count, double income, String message) {
        this.status = status;
        this.items = items;
        this.count = count;
        this.income = income;
        this.message = message;
    }

    public Status getStatus() { return status; }
    public List<ReportItem> getItems() { return items; }
    public int getCount() { return count; }
    public double getIncome() { return income; }
    public String getMessage() { return message; }

    public static ReportUiState loading() {
        return new ReportUiState(Status.LOADING, null, 0, 0, "");
    }

    public static ReportUiState success(List<ReportItem> items, int count, double income) {
        return new ReportUiState(Status.SUCCESS, items, count, income, "");
    }

    public static ReportUiState empty(String message) {
        return new ReportUiState(Status.EMPTY, null, 0, 0, message);
    }

    public static ReportUiState error(String message) {
        return new ReportUiState(Status.ERROR, null, 0, 0, message);
    }
}
