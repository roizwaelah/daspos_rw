package com.daspos.model;

import java.util.Locale;

public final class SalesUnit {
    public static final String ECER = "ecer";
    public static final String RENTENG = "renteng";
    public static final String PAK = "pak";
    public static final String KARTON = "karton";

    private SalesUnit() {
    }

    public static String normalize(String unit) {
        String value = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        if (RENTENG.equals(value)) return RENTENG;
        if (PAK.equals(value)) return PAK;
        if (KARTON.equals(value)) return KARTON;
        return ECER;
    }

    public static int defaultFactor(String unit) {
        String normalized = normalize(unit);
        if (RENTENG.equals(normalized)) return 10;
        if (PAK.equals(normalized)) return 20;
        if (KARTON.equals(normalized)) return 100;
        return 1;
    }

    public static String displayName(String unit) {
        String normalized = normalize(unit);
        if (RENTENG.equals(normalized)) return "RENTENG";
        if (PAK.equals(normalized)) return "PAK";
        if (KARTON.equals(normalized)) return "KARTON";
        return "ECER";
    }
}

