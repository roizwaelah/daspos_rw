package com.daspos.shared.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class RestoreStateStore {
    private static final String PREF = "restore_state";
    private static final String KEY_LAST_RESTORE_AT = "last_restore_at";
    private static final long SUPABASE_SYNC_COOLDOWN_MS = 120_000L;

    private RestoreStateStore() {
    }

    public static void markRestored(Context context) {
        prefs(context).edit().putLong(KEY_LAST_RESTORE_AT, System.currentTimeMillis()).apply();
    }

    public static boolean shouldDelaySupabaseSync(Context context) {
        long last = prefs(context).getLong(KEY_LAST_RESTORE_AT, 0L);
        if (last <= 0L) return false;
        return System.currentTimeMillis() - last < SUPABASE_SYNC_COOLDOWN_MS;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
