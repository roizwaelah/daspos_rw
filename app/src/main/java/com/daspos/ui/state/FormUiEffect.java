package com.daspos.ui.state;

public class FormUiEffect {
    public enum Type {
        NONE,
        SHOW_MESSAGE,
        CLOSE_SCREEN
    }

    private final Type type;
    private final String message;

    public FormUiEffect(Type type, String message) {
        this.type = type;
        this.message = message;
    }

    public Type getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public static FormUiEffect showMessage(String message) {
        return new FormUiEffect(Type.SHOW_MESSAGE, message);
    }

    public static FormUiEffect closeScreen(String message) {
        return new FormUiEffect(Type.CLOSE_SCREEN, message);
    }
}
