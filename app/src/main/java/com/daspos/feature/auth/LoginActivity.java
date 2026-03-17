package com.daspos.feature.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.core.app.HomeActivity;
import com.daspos.repository.UserRepository;
import com.daspos.shared.util.ViewUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        final TextInputEditText etUsername = findViewById(R.id.etUsername);
        final TextInputEditText etPassword = findViewById(R.id.etPassword);
        MaterialButton btnLogin = findViewById(R.id.btnLogin);
        TextView tvRegister = findViewById(R.id.tvRegister);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String user = String.valueOf(etUsername.getText()).trim();
                String pass = String.valueOf(etPassword.getText()).trim();
                if (user.isEmpty() || pass.isEmpty()) {
                    ViewUtils.toast(LoginActivity.this, getString(R.string.field_required));
                    return;
                }

                UserRepository.authenticateAsync(LoginActivity.this, user, pass, authenticated -> {
                    if (!authenticated) {
                        ViewUtils.toast(LoginActivity.this, getString(R.string.login_failed));
                        return;
                    }
                    UserRepository.getByUsernameAsync(LoginActivity.this, user, loggedInUser -> {
                        String role = loggedInUser == null ? "" : loggedInUser.getRole();
                        AuthSessionStore.saveSession(LoginActivity.this, user, role);
                        AuthSessionStore.saveLastBackgroundAt(LoginActivity.this, 0L);
                        startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                        finish();
                    }, throwable -> ViewUtils.toast(LoginActivity.this, getString(R.string.login_failed)));
                }, throwable -> ViewUtils.toast(LoginActivity.this, getString(R.string.login_failed)));
            }
        });
        tvRegister.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(LoginActivity.this, RegisterActivity.class)); }
        });
    }
}
