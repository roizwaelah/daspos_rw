package com.daspos.feature.auth;

import android.content.Context;
import android.content.SharedPreferences;

public final class AuthSessionStore {
    private static final String PREF = "auth_session";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLE = "role";
    private static final String KEY_LAST_BACKGROUND_AT = "last_background_at";

    private AuthSessionStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void saveSession(Context context, String username, String role) {
        prefs(context)
                .edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_ROLE, role)
                .apply();
    }

    public static String getUsername(Context context) {
        return prefs(context).getString(KEY_USERNAME, "");
    }

    public static String getRole(Context context) {
        return prefs(context).getString(KEY_ROLE, "");
    }

    public static boolean hasSession(Context context) {
        String username = getUsername(context);
        return username != null && !username.trim().isEmpty();
    }

    public static void saveLastBackgroundAt(Context context, long timestampMs) {
        prefs(context)
                .edit()
                .putLong(KEY_LAST_BACKGROUND_AT, timestampMs)
                .apply();
    }

    public static long getLastBackgroundAt(Context context) {
        return prefs(context).getLong(KEY_LAST_BACKGROUND_AT, 0L);
    }

    public static void clearSession(Context context) {
        prefs(context).edit().clear().apply();
    }
}
