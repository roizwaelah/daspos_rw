package com.daspos.ui.state;

import java.util.List;

public class ListUiState<T> {
    public enum Status {
        IDLE,
        LOADING,
        SUCCESS,
        EMPTY,
        ERROR
    }

    private final Status status;
    private final List<T> data;
    private final String message;

    public ListUiState(Status status, List<T> data, String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    public Status getStatus() {
        return status;
    }

    public List<T> getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public static <T> ListUiState<T> loading() {
        return new ListUiState<>(Status.LOADING, null, "");
    }

    public static <T> ListUiState<T> success(List<T> data) {
        return new ListUiState<>(Status.SUCCESS, data, "");
    }

    public static <T> ListUiState<T> empty(String message) {
        return new ListUiState<>(Status.EMPTY, null, message);
    }

    public static <T> ListUiState<T> error(String message) {
        return new ListUiState<>(Status.ERROR, null, message);
    }
}
