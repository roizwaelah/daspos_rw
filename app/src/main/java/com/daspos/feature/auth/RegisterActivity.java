package com.daspos.feature.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.repository.UserRepository;
import com.daspos.shared.util.ViewUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        final TextInputEditText etUsername = findViewById(R.id.etUsername);
        final TextInputEditText etPassword = findViewById(R.id.etPassword);
        final TextInputEditText etConfirmPassword = findViewById(R.id.etConfirmPassword);

        MaterialButton btnRegister = findViewById(R.id.btnRegister);
        TextView tvLogin = findViewById(R.id.tvLogin);

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String username = String.valueOf(etUsername.getText()).trim();
                String password = String.valueOf(etPassword.getText()).trim();
                String confirmPassword = String.valueOf(etConfirmPassword.getText()).trim();

                if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                    ViewUtils.toast(RegisterActivity.this, getString(R.string.field_required));
                    return;
                }

                if (!password.equals(confirmPassword)) {
                    ViewUtils.toast(RegisterActivity.this, getString(R.string.password_not_match));
                    return;
                }

                if (UserRepository.usernameExists(RegisterActivity.this, username)) {
                    ViewUtils.toast(RegisterActivity.this, getString(R.string.username_already_exists));
                    return;
                }

                UserRepository.add(RegisterActivity.this, username, password, "Kasir");
                ViewUtils.toast(RegisterActivity.this, getString(R.string.register_success));
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                finish();
            }
        });
        tvLogin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }
}
