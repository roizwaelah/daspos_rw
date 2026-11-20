package com.daspos.feature.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.daspos.shared.util.PasswordHasher;
import com.daspos.shared.util.SecurePrefs;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AuthSessionStore {
    private static final String PREF = "auth_session";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLE = "role";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_OUTLET_ID = "outlet_id";
    private static final String KEY_DATABASE_MODE = "database_mode";
    private static final String KEY_DATABASE_SETUP_COMPLETED = "database_setup_completed";
    private static final String KEY_REMOTE_PROVIDER_TYPE = "remote_provider_type";
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
    private static final String KEY_OFFLINE_REMOTE_PROVIDER_TYPE = "offline_remote_provider_type";
    private static final String KEY_OFFLINE_REMOTE_USERNAME = "offline_remote_username";
    private static final String KEY_OFFLINE_REMOTE_PASSWORD_HASH = "offline_remote_password_hash";
    private static final String KEY_OFFLINE_REMOTE_ROLE = "offline_remote_role";
    private static final String KEY_OFFLINE_REMOTE_USER_ID = "offline_remote_user_id";
    private static final String KEY_OFFLINE_REMOTE_OUTLET_ID = "offline_remote_outlet_id";
    private static final String KEY_OFFLINE_REMOTE_OUTLET_NAME = "offline_remote_outlet_name";
    private static final String KEY_OFFLINE_REMOTE_OUTLET_ADDRESS = "offline_remote_outlet_address";
    private static final String KEY_OFFLINE_REMOTE_OUTLET_PHONE = "offline_remote_outlet_phone";
    private static final String KEY_OFFLINE_REMOTE_OUTLET_EMAIL = "offline_remote_outlet_email";
    private static final String KEY_OFFLINE_REMOTE_ACCESS_TOKEN = "offline_remote_access_token";
    private static final String KEY_OFFLINE_REMOTE_REFRESH_TOKEN = "offline_remote_refresh_token";
    private static final String KEY_OFFLINE_REMOTE_ALLOWED_MENUS = "offline_remote_allowed_menus";

    private AuthSessionStore() {
    }

    public static final String DATABASE_MODE_LOCAL = "local";
    public static final String DATABASE_MODE_REMOTE = "remote";
    public static final String PROVIDER_SUPABASE = "supabase";
    public static final String PROVIDER_FIREBASE = "firebase";
    public static final String PROVIDER_APPWRITE = "appwrite";
    public static final String PROVIDER_POCKETBASE = "pocketbase";
    public static final String PROVIDER_CUSTOM_API = "custom_api";

    private static SharedPreferences prefs(Context context) {
        return SecurePrefs.get(context, PREF);
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

    public static void updateTokens(Context context, String accessToken, String refreshToken) {
        SharedPreferences.Editor editor = prefs(context).edit()
                .putString(KEY_ACCESS_TOKEN, valueOrEmpty(accessToken))
                .putString(KEY_REFRESH_TOKEN, valueOrEmpty(refreshToken));
        // Keep the offline Supabase credential fresh so offline fallback stays valid.
        if (PROVIDER_SUPABASE.equalsIgnoreCase(getSource(context))) {
            editor.putString(KEY_OFFLINE_ACCESS_TOKEN, valueOrEmpty(accessToken))
                    .putString(KEY_OFFLINE_REFRESH_TOKEN, valueOrEmpty(refreshToken));
        }
        editor.apply();
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

        String offlineRemoteProvider = prefs(context).getString(KEY_OFFLINE_REMOTE_PROVIDER_TYPE, "");
        String offlineRemoteUsername = prefs(context).getString(KEY_OFFLINE_REMOTE_USERNAME, "");
        if (!isBlank(offlineRemoteProvider) && !isBlank(offlineRemoteUsername)) {
            return normalizeProviderType(offlineRemoteProvider);
        }

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
        String remoteOutletId = prefs(context).getString(KEY_OFFLINE_REMOTE_OUTLET_ID, "");
        if (remoteOutletId != null && !remoteOutletId.trim().isEmpty()) return remoteOutletId;
        return prefs(context).getString(KEY_OFFLINE_OUTLET_ID, "");
    }

    public static void setDatabaseMode(Context context, String mode) {
        String normalized = DATABASE_MODE_REMOTE;
        if (DATABASE_MODE_LOCAL.equalsIgnoreCase(valueOrEmpty(mode))) {
            normalized = DATABASE_MODE_LOCAL;
        }
        prefs(context)
                .edit()
                .putString(KEY_DATABASE_MODE, normalized)
                .putBoolean(KEY_DATABASE_SETUP_COMPLETED, true)
                .apply();
    }

    public static boolean isDatabaseSetupCompleted(Context context) {
        return prefs(context).getBoolean(KEY_DATABASE_SETUP_COMPLETED, false);
    }

    public static void setDatabaseSetupCompleted(Context context, boolean completed) {
        prefs(context).edit().putBoolean(KEY_DATABASE_SETUP_COMPLETED, completed).apply();
    }

    public static String getDatabaseMode(Context context) {
        String mode = prefs(context).getString(KEY_DATABASE_MODE, "");
        if (DATABASE_MODE_LOCAL.equalsIgnoreCase(valueOrEmpty(mode))) return DATABASE_MODE_LOCAL;
        if (DATABASE_MODE_REMOTE.equalsIgnoreCase(valueOrEmpty(mode))) return DATABASE_MODE_REMOTE;

        String source = getSource(context);
        if ("supabase".equalsIgnoreCase(source)) return DATABASE_MODE_REMOTE;
        return DATABASE_MODE_LOCAL;
    }

    public static boolean isLocalDatabaseMode(Context context) {
        return DATABASE_MODE_LOCAL.equalsIgnoreCase(getDatabaseMode(context));
    }

    public static boolean isRemoteDatabaseMode(Context context) {
        return DATABASE_MODE_REMOTE.equalsIgnoreCase(getDatabaseMode(context));
    }

    public static void setRemoteProviderType(Context context, String providerType) {
        String normalized = normalizeProviderType(providerType);
        prefs(context)
                .edit()
                .putString(KEY_REMOTE_PROVIDER_TYPE, normalized)
                .apply();
    }

    public static String getRemoteProviderType(Context context) {
        String saved = prefs(context).getString(KEY_REMOTE_PROVIDER_TYPE, "");
        if (!isBlank(saved)) {
            return normalizeProviderType(saved);
        }
        String source = getSource(context);
        if (PROVIDER_FIREBASE.equalsIgnoreCase(source)) return PROVIDER_FIREBASE;
        if (PROVIDER_APPWRITE.equalsIgnoreCase(source)) return PROVIDER_APPWRITE;
        if (PROVIDER_POCKETBASE.equalsIgnoreCase(source)) return PROVIDER_POCKETBASE;
        if (PROVIDER_CUSTOM_API.equalsIgnoreCase(source)) return PROVIDER_CUSTOM_API;
        if (PROVIDER_SUPABASE.equalsIgnoreCase(source)) return PROVIDER_SUPABASE;
        return PROVIDER_SUPABASE;
    }

    public static boolean hasSession(Context context) {
        String source = getSource(context);
        if ("supabase".equals(source)) {
            return hasValidSupabaseSession(context);
        }
        if (isRemoteProvider(source)) {
            String username = getUsername(context);
            return username != null && !username.trim().isEmpty();
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
                .putString(KEY_OFFLINE_PASSWORD_HASH, PasswordHasher.hash(password))
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
        if (!PasswordHasher.verify(password, storedHash)) return null;

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

    public static void saveOfflineRemoteCredential(
            Context context,
            String providerType,
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
            String refreshToken,
            List<String> allowedMenus
    ) {
        if (isBlank(providerType) || PROVIDER_SUPABASE.equalsIgnoreCase(providerType)) return;
        if (isBlank(username) || isBlank(password)) return;
        prefs(context).edit()
                .putString(KEY_OFFLINE_REMOTE_PROVIDER_TYPE, normalizeProviderType(providerType))
                .putString(KEY_OFFLINE_REMOTE_USERNAME, username.trim())
                .putString(KEY_OFFLINE_REMOTE_PASSWORD_HASH, PasswordHasher.hash(password))
                .putString(KEY_OFFLINE_REMOTE_ROLE, valueOrEmpty(role))
                .putString(KEY_OFFLINE_REMOTE_USER_ID, valueOrEmpty(userId))
                .putString(KEY_OFFLINE_REMOTE_OUTLET_ID, valueOrEmpty(outletId))
                .putString(KEY_OFFLINE_REMOTE_OUTLET_NAME, valueOrEmpty(outletName))
                .putString(KEY_OFFLINE_REMOTE_OUTLET_ADDRESS, valueOrEmpty(outletAddress))
                .putString(KEY_OFFLINE_REMOTE_OUTLET_PHONE, valueOrEmpty(outletPhone))
                .putString(KEY_OFFLINE_REMOTE_OUTLET_EMAIL, valueOrEmpty(outletEmail))
                .putString(KEY_OFFLINE_REMOTE_ACCESS_TOKEN, valueOrEmpty(accessToken))
                .putString(KEY_OFFLINE_REMOTE_REFRESH_TOKEN, valueOrEmpty(refreshToken))
                .putString(KEY_OFFLINE_REMOTE_ALLOWED_MENUS, toJsonArray(normalizeMenus(allowedMenus)).toString())
                .apply();
    }

    public static OfflineRemoteSession tryOfflineRemoteLogin(
            Context context,
            String providerType,
            String username,
            String password
    ) {
        String normalizedProvider = normalizeProviderType(providerType);
        if (PROVIDER_SUPABASE.equals(normalizedProvider)) return null;
        if (isBlank(username) || isBlank(password)) return null;

        String storedProvider = normalizeProviderType(prefs(context).getString(KEY_OFFLINE_REMOTE_PROVIDER_TYPE, ""));
        if (!normalizedProvider.equals(storedProvider)) return null;
        String storedUsername = prefs(context).getString(KEY_OFFLINE_REMOTE_USERNAME, "");
        String storedHash = prefs(context).getString(KEY_OFFLINE_REMOTE_PASSWORD_HASH, "");
        if (isBlank(storedUsername) || isBlank(storedHash)) return null;
        if (!storedUsername.trim().equalsIgnoreCase(username.trim())) return null;
        if (!PasswordHasher.verify(password, storedHash)) return null;

        return new OfflineRemoteSession(
                storedProvider,
                storedUsername.trim(),
                valueOrEmpty(prefs(context).getString(KEY_OFFLINE_REMOTE_ROLE, "")),
                valueOrEmpty(prefs(context).getString(KEY_OFFLINE_REMOTE_USER_ID, "")),
                valueOrEmpty(prefs(context).getString(KEY_OFFLINE_REMOTE_OUTLET_ID, "")),
                valueOrEmpty(prefs(context).getString(KEY_OFFLINE_REMOTE_OUTLET_NAME, "")),
                valueOrEmpty(prefs(context).getString(KEY_OFFLINE_REMOTE_OUTLET_ADDRESS, "")),
                valueOrEmpty(prefs(context).getString(KEY_OFFLINE_REMOTE_OUTLET_PHONE, "")),
                valueOrEmpty(prefs(context).getString(KEY_OFFLINE_REMOTE_OUTLET_EMAIL, "")),
                valueOrEmpty(prefs(context).getString(KEY_OFFLINE_REMOTE_ACCESS_TOKEN, "")),
                valueOrEmpty(prefs(context).getString(KEY_OFFLINE_REMOTE_REFRESH_TOKEN, "")),
                parseMenus(prefs(context).getString(KEY_OFFLINE_REMOTE_ALLOWED_MENUS, ""))
        );
    }

    private static boolean isRemoteProvider(String source) {
        String value = valueOrEmpty(source).trim().toLowerCase(Locale.ROOT);
        return PROVIDER_SUPABASE.equals(value)
                || PROVIDER_FIREBASE.equals(value)
                || PROVIDER_APPWRITE.equals(value)
                || PROVIDER_POCKETBASE.equals(value)
                || PROVIDER_CUSTOM_API.equals(value);
    }

    private static List<String> normalizeMenus(List<String> source) {
        Set<String> dedup = new LinkedHashSet<>();
        if (source != null) {
            for (String item : source) {
                String menu = valueOrEmpty(item).trim().toLowerCase(Locale.ROOT);
                if (menu.isEmpty()) continue;
                dedup.add(menu);
            }
        }
        return new ArrayList<>(dedup);
    }

    private static JSONArray toJsonArray(List<String> menus) {
        JSONArray array = new JSONArray();
        if (menus == null) return array;
        for (String menu : menus) {
            if (isBlank(menu)) continue;
            array.put(menu.trim().toLowerCase(Locale.ROOT));
        }
        return array;
    }

    private static List<String> parseMenus(String raw) {
        List<String> out = new ArrayList<>();
        if (isBlank(raw)) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String menu = valueOrEmpty(arr.optString(i, "")).trim().toLowerCase(Locale.ROOT);
                if (menu.isEmpty()) continue;
                if (!out.contains(menu)) out.add(menu);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeProviderType(String providerType) {
        String value = valueOrEmpty(providerType).trim().toLowerCase();
        if (PROVIDER_FIREBASE.equals(value)) return PROVIDER_FIREBASE;
        if (PROVIDER_APPWRITE.equals(value)) return PROVIDER_APPWRITE;
        if (PROVIDER_POCKETBASE.equals(value)) return PROVIDER_POCKETBASE;
        if (PROVIDER_CUSTOM_API.equals(value)) return PROVIDER_CUSTOM_API;
        return PROVIDER_SUPABASE;
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

    public static final class OfflineRemoteSession {
        public final String providerType;
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
        public final List<String> allowedMenus;

        private OfflineRemoteSession(
                String providerType,
                String username,
                String role,
                String userId,
                String outletId,
                String outletName,
                String outletAddress,
                String outletPhone,
                String outletEmail,
                String accessToken,
                String refreshToken,
                List<String> allowedMenus
        ) {
            this.providerType = providerType;
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
            this.allowedMenus = allowedMenus == null ? new ArrayList<>() : new ArrayList<>(allowedMenus);
        }
    }
}
