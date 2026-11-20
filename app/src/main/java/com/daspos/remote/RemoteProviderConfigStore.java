package com.daspos.remote;

import android.content.Context;
import android.content.SharedPreferences;

import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.shared.util.SecurePrefs;
import com.daspos.supabase.SupabaseConfig;

public final class RemoteProviderConfigStore {
    private static final String PREF = "remote_provider_config";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_EDGE_FUNCTION_URL = "edge_function_url";
    private static final String KEY_EDGE_FUNCTION_SECRET = "edge_function_secret";

    private RemoteProviderConfigStore() {
    }

    public static RemoteConfig get(Context context) {
        SharedPreferences preferences = prefs(context);
        return new RemoteConfig(
                normalizeUrl(preferences.getString(KEY_BASE_URL, "")),
                safe(preferences.getString(KEY_API_KEY, "")),
                normalizeUrl(preferences.getString(KEY_EDGE_FUNCTION_URL, "")),
                safe(preferences.getString(KEY_EDGE_FUNCTION_SECRET, ""))
        );
    }

    public static boolean save(
            Context context,
            String baseUrl,
            String apiKey,
            String edgeFunctionUrl,
            String edgeFunctionSecret
    ) {
        String normalizedBase = normalizeUrl(baseUrl);
        String normalizedEdge = normalizeUrl(edgeFunctionUrl);
        if (!isAllowedUrl(normalizedBase) || !isAllowedUrl(normalizedEdge)) return false;
        prefs(context).edit()
                .putString(KEY_BASE_URL, normalizedBase)
                .putString(KEY_API_KEY, safe(apiKey))
                .putString(KEY_EDGE_FUNCTION_URL, normalizedEdge)
                .putString(KEY_EDGE_FUNCTION_SECRET, safe(edgeFunctionSecret))
                .apply();
        return true;
    }

    public static boolean isConfigured(Context context, String providerType) {
        String provider = safe(providerType).toLowerCase();
        if (AuthSessionStore.PROVIDER_SUPABASE.equals(provider)) {
            return SupabaseConfig.isConfigured();
        }
        RemoteConfig config = get(context);
        return !config.edgeFunctionUrl.isEmpty();
    }

    private static SharedPreferences prefs(Context context) {
        return SecurePrefs.get(context, PREF);
    }

    private static boolean isAllowedUrl(String url) {
        return url.isEmpty() || SupabaseConfig.isAllowedRemoteUrl(url);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeUrl(String value) {
        String clean = safe(value);
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    public static final class RemoteConfig {
        public final String baseUrl;
        public final String apiKey;
        public final String edgeFunctionUrl;
        public final String edgeFunctionSecret;

        private RemoteConfig(
                String baseUrl,
                String apiKey,
                String edgeFunctionUrl,
                String edgeFunctionSecret
        ) {
            this.baseUrl = baseUrl == null ? "" : baseUrl;
            this.apiKey = apiKey == null ? "" : apiKey;
            this.edgeFunctionUrl = edgeFunctionUrl == null ? "" : edgeFunctionUrl;
            this.edgeFunctionSecret = edgeFunctionSecret == null ? "" : edgeFunctionSecret;
        }
    }
}
