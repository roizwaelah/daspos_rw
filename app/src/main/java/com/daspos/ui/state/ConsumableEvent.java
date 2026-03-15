package com.daspos.ui.state;

public class ConsumableEvent<T> {
    private final T value;
    private boolean consumed = false;

    public ConsumableEvent(T value) {
        this.value = value;
    }

    public T consume() {
        if (consumed) return null;
        consumed = true;
        return value;
    }

    public T peek() {
        return value;
    }
}
