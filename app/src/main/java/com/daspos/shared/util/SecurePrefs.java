package com.daspos.shared.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SharedPreferences backed by EncryptedSharedPreferences when the Android
 * Keystore is available, with a plain-prefs fallback so a keystore failure
 * degrades security instead of crashing the app.
 *
 * Instances are cached per name: building a MasterKey hits the Tink
 * AndroidKeysetManager synchronized lock + keystore disk I/O (~100-400ms),
 * and callers invoke this on every prefs read across all threads.
 */
public final class SecurePrefs {
    private static final ConcurrentHashMap<String, SharedPreferences> CACHE = new ConcurrentHashMap<>();

    private SecurePrefs() {
    }

    public static SharedPreferences get(Context context, String name) {
        SharedPreferences cached = CACHE.get(name);
        if (cached != null) return cached;
        SharedPreferences created = create(context.getApplicationContext(), name);
        SharedPreferences existing = CACHE.putIfAbsent(name, created);
        return existing != null ? existing : created;
    }

    private static SharedPreferences create(Context appContext, String name) {
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    appContext,
                    name,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException | RuntimeException e) {
            return appContext.getSharedPreferences(name, Context.MODE_PRIVATE);
        }
    }
}
