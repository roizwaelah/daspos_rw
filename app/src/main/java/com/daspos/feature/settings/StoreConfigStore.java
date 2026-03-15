package com.daspos.feature.settings;

import android.content.Context;
import android.content.SharedPreferences;

public class StoreConfigStore {
    private static final String PREF = "store_config";
    private static final String KEY_NAME = "store_name";
    private static final String KEY_ADDRESS = "store_address";
    private static final String KEY_PHONE = "store_phone";
    private static final String KEY_EMAIL = "store_email";
    private static final String KEY_HAS_LOGO = "has_logo";
    private static final String KEY_LOGO_URI = "logo_uri";
    private static final String DEFAULT_STORE_NAME = "DasPos Store";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String getStoreName(Context context) {
        return prefs(context).getString(KEY_NAME, DEFAULT_STORE_NAME);
    }

    public static String getAddress(Context context) {
        return prefs(context).getString(KEY_ADDRESS, "");
    }

    public static String getPhone(Context context) {
        return prefs(context).getString(KEY_PHONE, "");
    }

    public static String getEmail(Context context) {
        return prefs(context).getString(KEY_EMAIL, "");
    }

    public static boolean hasLogo(Context context) {
        return prefs(context).getBoolean(KEY_HAS_LOGO, false);
    }

    public static String getLogoUri(Context context) {
        return prefs(context).getString(KEY_LOGO_URI, "");
    }

    public static void save(Context context, String storeName, String address, String phone, String email) {
        prefs(context).edit()
                .putString(KEY_NAME, storeName == null || storeName.trim().isEmpty() ? DEFAULT_STORE_NAME : storeName.trim())
                .putString(KEY_ADDRESS, address == null ? "" : address.trim())
                .putString(KEY_PHONE, phone == null ? "" : phone.trim())
                .putString(KEY_EMAIL, email == null ? "" : email.trim())
                .apply();
    }

    public static void saveLogoUri(Context context, String logoUri) {
        prefs(context).edit()
                .putString(KEY_LOGO_URI, logoUri == null ? "" : logoUri)
                .putBoolean(KEY_HAS_LOGO, logoUri != null && !logoUri.trim().isEmpty())
                .apply();
    }

    public static void clearLogo(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_HAS_LOGO, false)
                .putString(KEY_LOGO_URI, "")
                .apply();
    }

    public static void setHasLogo(Context context, boolean hasLogo) {
        prefs(context).edit().putBoolean(KEY_HAS_LOGO, hasLogo).apply();
    }
}
