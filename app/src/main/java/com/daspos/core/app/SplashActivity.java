package com.daspos.core.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.daspos.R;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.feature.auth.LoginActivity;

public class SplashActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

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
}
