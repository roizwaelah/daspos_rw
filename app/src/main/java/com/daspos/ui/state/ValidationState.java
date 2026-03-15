package com.daspos.ui.state;

public class ValidationState {
    private final String code;
    private final String message;

    public ValidationState(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static ValidationState empty() {
        return new ValidationState("", "");
    }
}
