package com.daspos.ui.state;

public class TransactionUiEffect {
    public enum Type {
        NONE,
        SHOW_MESSAGE,
        NAVIGATE_TO_RECEIPT
    }

    private final Type type;
    private final String message;
    private final String transactionId;

    public TransactionUiEffect(Type type, String message) {
        this(type, message, null);
    }

    public TransactionUiEffect(Type type, String message, String transactionId) {
        this.type = type;
        this.message = message;
        this.transactionId = transactionId;
    }

    public Type getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public static TransactionUiEffect showMessage(String message) {
        return new TransactionUiEffect(Type.SHOW_MESSAGE, message);
    }

    public static TransactionUiEffect navigateToReceipt(String message, String transactionId) {
        return new TransactionUiEffect(Type.NAVIGATE_TO_RECEIPT, message, transactionId);
    }
}
