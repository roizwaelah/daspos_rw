package com.daspos.feature.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.core.app.HomeActivity;
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
                if (!user.isEmpty() && !pass.isEmpty()) {
                    startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                    finish();
                } else {
                    etUsername.setError(getString(R.string.label_username));
                }
            }
        });
        tvRegister.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(LoginActivity.this, RegisterActivity.class)); }
        });
    }
}
