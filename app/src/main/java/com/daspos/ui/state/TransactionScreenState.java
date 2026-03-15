package com.daspos.ui.state;

public class TransactionScreenState {
    public enum CheckoutStatus {
        IDLE,
        PROCESSING,
        SUCCESS,
        ERROR
    }

    private final boolean cartEmpty;
    private final double total;
    private final double pay;
    private final double change;
    private final CheckoutStatus checkoutStatus;
    private final String message;

    public TransactionScreenState(boolean cartEmpty, double total, double pay, double change,
                                  CheckoutStatus checkoutStatus, String message) {
        this.cartEmpty = cartEmpty;
        this.total = total;
        this.pay = pay;
        this.change = change;
        this.checkoutStatus = checkoutStatus;
        this.message = message;
    }

    public boolean isCartEmpty() { return cartEmpty; }
    public double getTotal() { return total; }
    public double getPay() { return pay; }
    public double getChange() { return change; }
    public CheckoutStatus getCheckoutStatus() { return checkoutStatus; }
    public String getMessage() { return message; }

    public TransactionScreenState withCheckout(CheckoutStatus status, String message) {
        return new TransactionScreenState(cartEmpty, total, pay, change, status, message);
    }

    public static TransactionScreenState of(boolean cartEmpty, double total, double pay, double change) {
        return new TransactionScreenState(cartEmpty, total, pay, change, CheckoutStatus.IDLE, "");
    }

    public static TransactionScreenState empty() {
        return of(true, 0, 0, 0);
    }
}
