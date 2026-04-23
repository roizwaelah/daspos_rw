package com.daspos.feature.auth;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class AuthSessionStore {
    private static final String PREF = "auth_session";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLE = "role";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_OUTLET_ID = "outlet_id";
    private static final String KEY_LAST_BACKGROUND_AT = "last_background_at";
    private static final String KEY_OFFLINE_USERNAME = "offline_username";
    private static final String KEY_OFFLINE_PASSWORD_HASH = "offline_password_hash";
    private static final String KEY_OFFLINE_ROLE = "offline_role";
    private static final String KEY_OFFLINE_USER_ID = "offline_user_id";
    private static final String KEY_OFFLINE_OUTLET_ID = "offline_outlet_id";
    private static final String KEY_OFFLINE_OUTLET_NAME = "offline_outlet_name";
    private static final String KEY_OFFLINE_OUTLET_ADDRESS = "offline_outlet_address";
    private static final String KEY_OFFLINE_OUTLET_PHONE = "offline_outlet_phone";
    private static final String KEY_OFFLINE_OUTLET_EMAIL = "offline_outlet_email";
    private static final String KEY_OFFLINE_ACCESS_TOKEN = "offline_access_token";
    private static final String KEY_OFFLINE_REFRESH_TOKEN = "offline_refresh_token";

    private AuthSessionStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void saveSession(Context context, String username, String role) {
        saveSession(context, username, role, "", "", "", "local", "");
    }

    public static void saveSession(
            Context context,
            String username,
            String role,
            String userId,
            String accessToken,
            String refreshToken,
            String source,
            String outletId
    ) {
        prefs(context)
                .edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_ROLE, role)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_SOURCE, source)
                .putString(KEY_OUTLET_ID, outletId)
                .apply();
    }

    public static String getUsername(Context context) {
        return prefs(context).getString(KEY_USERNAME, "");
    }

    public static String getRole(Context context) {
        return prefs(context).getString(KEY_ROLE, "");
    }

    public static String getUserId(Context context) {
        return prefs(context).getString(KEY_USER_ID, "");
    }

    public static String getAccessToken(Context context) {
        String token = prefs(context).getString(KEY_ACCESS_TOKEN, "");
        if (token != null && !token.trim().isEmpty()) return token;
        return prefs(context).getString(KEY_OFFLINE_ACCESS_TOKEN, "");
    }

    public static String getRefreshToken(Context context) {
        String token = prefs(context).getString(KEY_REFRESH_TOKEN, "");
        if (token != null && !token.trim().isEmpty()) return token;
        return prefs(context).getString(KEY_OFFLINE_REFRESH_TOKEN, "");
    }

    public static String getSource(Context context) {
        String source = prefs(context).getString(KEY_SOURCE, "");
        if (source != null && !source.trim().isEmpty()) return source.trim().toLowerCase();

        String offlineAccessToken = prefs(context).getString(KEY_OFFLINE_ACCESS_TOKEN, "");
        String offlineOutletId = prefs(context).getString(KEY_OFFLINE_OUTLET_ID, "");
        if (offlineAccessToken != null && !offlineAccessToken.trim().isEmpty()
                && offlineOutletId != null && !offlineOutletId.trim().isEmpty()) {
            return "supabase";
        }
        return "local";
    }

    public static String getOutletId(Context context) {
        String outletId = prefs(context).getString(KEY_OUTLET_ID, "");
        if (outletId != null && !outletId.trim().isEmpty()) return outletId;
        return prefs(context).getString(KEY_OFFLINE_OUTLET_ID, "");
    }

    public static boolean hasSession(Context context) {
        if ("supabase".equals(getSource(context))) {
            return hasValidSupabaseSession(context);
        }
        String username = getUsername(context);
        return username != null && !username.trim().isEmpty();
    }

    public static boolean hasValidSupabaseSession(Context context) {
        String username = getUsername(context);
        String userId = getUserId(context);
        String accessToken = getAccessToken(context);
        String outletId = getOutletId(context);
        return username != null && !username.trim().isEmpty()
                && userId != null && !userId.trim().isEmpty()
                && accessToken != null && !accessToken.trim().isEmpty()
                && outletId != null && !outletId.trim().isEmpty();
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
        prefs(context).edit()
                .remove(KEY_USERNAME)
                .remove(KEY_ROLE)
                .remove(KEY_USER_ID)
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_SOURCE)
                .remove(KEY_OUTLET_ID)
                .remove(KEY_LAST_BACKGROUND_AT)
                .apply();
    }

    public static boolean isAdmin(Context context) {
        String role = getRole(context);
        return role != null && "admin".equalsIgnoreCase(role.trim());
    }

    public static void saveOfflineSupabaseCredential(
            Context context,
            String username,
            String password,
            String role,
            String userId,
            String outletId,
            String outletName,
            String outletAddress,
            String outletPhone,
            String outletEmail,
            String accessToken,
            String refreshToken
    ) {
        if (isBlank(username) || isBlank(password)) return;
        prefs(context).edit()
                .putString(KEY_OFFLINE_USERNAME, username.trim())
                .putString(KEY_OFFLINE_PASSWORD_HASH, sha256(password))
                .putString(KEY_OFFLINE_ROLE, valueOrEmpty(role))
                .putString(KEY_OFFLINE_USER_ID, valueOrEmpty(userId))
                .putString(KEY_OFFLINE_OUTLET_ID, valueOrEmpty(outletId))
                .putString(KEY_OFFLINE_OUTLET_NAME, valueOrEmpty(outletName))
                .putString(KEY_OFFLINE_OUTLET_ADDRESS, valueOrEmpty(outletAddress))
                .putString(KEY_OFFLINE_OUTLET_PHONE, valueOrEmpty(outletPhone))
                .putString(KEY_OFFLINE_OUTLET_EMAIL, valueOrEmpty(outletEmail))
                .putString(KEY_OFFLINE_ACCESS_TOKEN, valueOrEmpty(accessToken))
                .putString(KEY_OFFLINE_REFRESH_TOKEN, valueOrEmpty(refreshToken))
                .apply();
    }

    public static OfflineSupabaseSession tryOfflineSupabaseLogin(Context context, String username, String password) {
        if (isBlank(username) || isBlank(password)) return null;
        String storedUsername = prefs(context).getString(KEY_OFFLINE_USERNAME, "");
        String storedHash = prefs(context).getString(KEY_OFFLINE_PASSWORD_HASH, "");
        if (isBlank(storedUsername) || isBlank(storedHash)) return null;
        if (!storedUsername.trim().equalsIgnoreCase(username.trim())) return null;
        if (!storedHash.equals(sha256(password))) return null;

        String role = prefs(context).getString(KEY_OFFLINE_ROLE, "");
        String userId = prefs(context).getString(KEY_OFFLINE_USER_ID, "");
        String outletId = prefs(context).getString(KEY_OFFLINE_OUTLET_ID, "");
        String outletName = prefs(context).getString(KEY_OFFLINE_OUTLET_NAME, "");
        String outletAddress = prefs(context).getString(KEY_OFFLINE_OUTLET_ADDRESS, "");
        String outletPhone = prefs(context).getString(KEY_OFFLINE_OUTLET_PHONE, "");
        String outletEmail = prefs(context).getString(KEY_OFFLINE_OUTLET_EMAIL, "");
        String accessToken = prefs(context).getString(KEY_OFFLINE_ACCESS_TOKEN, "");
        String refreshToken = prefs(context).getString(KEY_OFFLINE_REFRESH_TOKEN, "");
        if (isBlank(userId) || isBlank(outletId)) return null;

        return new OfflineSupabaseSession(
                storedUsername.trim(),
                valueOrEmpty(role),
                valueOrEmpty(userId),
                valueOrEmpty(outletId),
                valueOrEmpty(outletName),
                valueOrEmpty(outletAddress),
                valueOrEmpty(outletPhone),
                valueOrEmpty(outletEmail),
                valueOrEmpty(accessToken),
                valueOrEmpty(refreshToken)
        );
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(valueOrEmpty(raw).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    public static final class OfflineSupabaseSession {
        public final String username;
        public final String role;
        public final String userId;
        public final String outletId;
        public final String outletName;
        public final String outletAddress;
        public final String outletPhone;
        public final String outletEmail;
        public final String accessToken;
        public final String refreshToken;

        private OfflineSupabaseSession(
                String username,
                String role,
                String userId,
                String outletId,
                String outletName,
                String outletAddress,
                String outletPhone,
                String outletEmail,
                String accessToken,
                String refreshToken
        ) {
            this.username = username;
            this.role = role;
            this.userId = userId;
            this.outletId = outletId;
            this.outletName = outletName;
            this.outletAddress = outletAddress;
            this.outletPhone = outletPhone;
            this.outletEmail = outletEmail;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }
}
