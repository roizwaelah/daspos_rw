package com.daspos.core.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.daspos.R;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.feature.auth.LoginActivity;

public class SplashActivity extends BaseActivity {
    public static final String EXTRA_AUTH_MESSAGE = "extra_auth_message";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        String authMessage = resolveAuthMessageFromIntent(getIntent());
        if (authMessage != null) {
            Intent loginIntent = new Intent(SplashActivity.this, LoginActivity.class);
            loginIntent.putExtra(EXTRA_AUTH_MESSAGE, authMessage);
            startActivity(loginIntent);
            finish();
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                Class<?> destination = AuthSessionStore.hasSession(SplashActivity.this)
                        ? HomeActivity.class
                        : LoginActivity.class;
                startActivity(new Intent(SplashActivity.this, destination));
                finish();
            }
        }, 1200);
    }

    private String resolveAuthMessageFromIntent(Intent intent) {
        if (intent == null) return null;
        Uri data = intent.getData();
        if (data == null) return null;
        if (!"daspos".equalsIgnoreCase(data.getScheme())) return null;
        if (!"auth".equalsIgnoreCase(data.getHost())) return null;
        if (data.getPath() == null || !data.getPath().startsWith("/callback")) return null;

        String error = data.getQueryParameter("error_description");
        if (error == null || error.trim().isEmpty()) {
            error = readFromFragment(data, "error_description");
        }
        if (error != null && !error.trim().isEmpty()) {
            return getString(R.string.auth_email_verify_failed);
        }
        return getString(R.string.auth_email_verify_success);
    }

    private String readFromFragment(Uri uri, String key) {
        String fragment = uri.getFragment();
        if (fragment == null || fragment.trim().isEmpty()) return "";
        String[] pairs = fragment.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2) continue;
            if (!key.equalsIgnoreCase(kv[0])) continue;
            try {
                return Uri.decode(kv[1]);
            } catch (Exception ignored) {
                return kv[1];
            }
        }
        return "";
    }
}
