package com.daspos.shared.util;

public class FormValidator {
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isPositiveOrZeroNumber(String value) {
        try {
            return Double.parseDouble(value) >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isPositiveOrZeroInt(String value) {
        try {
            return Integer.parseInt(value) >= 0;
        } catch (Exception e) {
            return false;
        }
    }
}
