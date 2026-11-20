package com.daspos.supabase;

import android.content.Context;

import com.daspos.feature.auth.AuthSessionStore;

/**
 * Exchanges the stored Supabase refresh token for a new access token and
 * persists the rotated pair. Serialized so concurrent 401s trigger a single
 * refresh (Supabase rotates refresh tokens on use).
 */
public final class SupabaseSessionRefresher {
    private SupabaseSessionRefresher() {
    }

    public static String refreshAccessToken() {
        Context context = SupabaseConfig.getAppContext();
        if (context == null) return null;
        String refreshToken = AuthSessionStore.getRefreshToken(context);
        if (refreshToken == null || refreshToken.trim().isEmpty()) return null;

        synchronized (SupabaseSessionRefresher.class) {
            SupabaseAuthService.TokenRefreshResult result = SupabaseAuthService.refreshTokens(refreshToken);
            if (result == null || result.accessToken == null || result.accessToken.trim().isEmpty()) {
                return null;
            }
            AuthSessionStore.updateTokens(context, result.accessToken, result.refreshToken);
            return result.accessToken;
        }
    }
}
