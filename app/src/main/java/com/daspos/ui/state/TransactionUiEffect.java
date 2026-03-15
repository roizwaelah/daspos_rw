package com.daspos.ui.state;

public class TransactionUiEffect {
    public enum Type {
        NONE,
        SHOW_MESSAGE,
        NAVIGATE_TO_RECEIPT
    }

    private final Type type;
    private final String message;

    public TransactionUiEffect(Type type, String message) {
        this.type = type;
        this.message = message;
    }

    public Type getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public static TransactionUiEffect showMessage(String message) {
        return new TransactionUiEffect(Type.SHOW_MESSAGE, message);
    }

    public static TransactionUiEffect navigateToReceipt(String message) {
        return new TransactionUiEffect(Type.NAVIGATE_TO_RECEIPT, message);
    }
}
