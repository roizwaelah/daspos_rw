package com.daspos.supabase;

import android.content.Context;
import android.content.SharedPreferences;

import com.daspos.BuildConfig;
import com.daspos.shared.util.SecurePrefs;

public final class SupabaseConfig {
    private static final String PREF = "supabase_config";
    private static final String KEY_URL = "url";
    private static final String KEY_ANON_KEY = "anon_key";
    private static Context appContext;

    private SupabaseConfig() {
    }

    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static Context getAppContext() {
        return appContext;
    }

    public static boolean save(Context context, String url, String anonKey) {
        Context safeContext = context == null ? appContext : context.getApplicationContext();
        if (safeContext == null) return false;
        String normalized = normalizeUrl(url);
        if (!normalized.isEmpty() && !isAllowedRemoteUrl(normalized)) return false;
        SecurePrefs.get(safeContext, PREF).edit()
                .putString(KEY_URL, normalized)
                .putString(KEY_ANON_KEY, safe(anonKey))
                .apply();
        return true;
    }

    public static String getUrl() {
        String runtimeUrl = getRuntimeValue(KEY_URL);
        if (!runtimeUrl.isEmpty()) return runtimeUrl;
        return normalizeUrl(BuildConfig.SUPABASE_URL);
    }

    public static String getAnonKey() {
        String runtimeKey = getRuntimeValue(KEY_ANON_KEY);
        if (!runtimeKey.isEmpty()) return runtimeKey;
        return safe(BuildConfig.SUPABASE_ANON_KEY);
    }

    public static String getDeleteAccountFunctionName() {
        return BuildConfig.SUPABASE_DELETE_ACCOUNT_FUNCTION == null
                ? ""
                : BuildConfig.SUPABASE_DELETE_ACCOUNT_FUNCTION.trim();
    }

    public static String getCreateCashierFunctionName() {
        return BuildConfig.SUPABASE_CREATE_CASHIER_FUNCTION == null
                ? ""
                : BuildConfig.SUPABASE_CREATE_CASHIER_FUNCTION.trim();
    }

    public static String getDeleteAccountFunctionUrl() {
        if (getUrl().isEmpty() || getDeleteAccountFunctionName().isEmpty()) return "";
        return getUrl() + "/functions/v1/" + getDeleteAccountFunctionName();
    }

    public static String getCreateCashierFunctionUrl() {
        if (getUrl().isEmpty() || getCreateCashierFunctionName().isEmpty()) return "";
        return getUrl() + "/functions/v1/" + getCreateCashierFunctionName();
    }

    public static boolean isConfigured() {
        return !getUrl().isEmpty() && !getAnonKey().isEmpty();
    }

    private static String getRuntimeValue(String key) {
        if (appContext == null) return "";
        return safe(SecurePrefs.get(appContext, PREF).getString(key, ""));
    }

    // Remote endpoints must be HTTPS; plain HTTP is only tolerated for
    // loopback development targets.
    public static boolean isAllowedRemoteUrl(String url) {
        String clean = safe(url);
        if (clean.isEmpty()) return false;
        if (clean.startsWith("https://")) return true;
        if (!clean.startsWith("http://")) return false;
        String host = clean.substring("http://".length());
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private static String normalizeUrl(String value) {
        String clean = safe(value);
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
