package com.daspos.core.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.feature.auth.LoginActivity;
import com.daspos.repository.ProductRepository;
import com.daspos.repository.TransactionRepository;
import com.daspos.shared.util.ThemeManager;

public class BaseActivity extends AppCompatActivity {
    private static final long AUTO_LOGOUT_TIMEOUT_MS = 60 * 60 * 1000L;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (shouldTrackBackgroundTime()) {
            AuthSessionStore.saveLastBackgroundAt(this, System.currentTimeMillis());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        ProductRepository.triggerSyncIfPossible(this);
        TransactionRepository.triggerSyncIfPossible(this);
        if (!shouldTrackBackgroundTime()) {
            return;
        }

        long lastBackgroundAt = AuthSessionStore.getLastBackgroundAt(this);
        if (lastBackgroundAt <= 0L) {
            return;
        }

        long elapsedMs = System.currentTimeMillis() - lastBackgroundAt;
        if (elapsedMs < AUTO_LOGOUT_TIMEOUT_MS) {
            return;
        }

        AuthSessionStore.clearSession(this);
        AuthSessionStore.saveLastBackgroundAt(this, 0L);

        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private boolean shouldTrackBackgroundTime() {
        return !(this instanceof LoginActivity) && AuthSessionStore.hasSession(this);
    }
}
