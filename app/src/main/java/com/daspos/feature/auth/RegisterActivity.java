package com.daspos.feature.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.settings.StoreConfigStore;
import com.daspos.repository.UserRepository;
import com.daspos.shared.util.LoadingDialogHelper;
import com.daspos.shared.util.PasswordFieldToggleHelper;
import com.daspos.shared.util.ViewUtils;
import com.daspos.supabase.SupabaseAuthService;
import com.daspos.supabase.SupabaseConfig;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends BaseActivity {
    private AlertDialog loadingDialog;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        final TextInputEditText etStoreName = findViewById(R.id.etStoreName);
        final TextInputEditText etUsername = findViewById(R.id.etUsername);
        final TextInputEditText etPassword = findViewById(R.id.etPassword);
        final TextInputEditText etConfirmPassword = findViewById(R.id.etConfirmPassword);
        PasswordFieldToggleHelper.attach(etPassword);
        PasswordFieldToggleHelper.attach(etConfirmPassword);

        MaterialButton btnRegister = findViewById(R.id.btnRegister);
        if (SupabaseConfig.isConfigured()) {
            etStoreName.setText("");
        } else {
            etStoreName.setText(StoreConfigStore.getStoreName(this));
        }
        TextView tvLogin = findViewById(R.id.tvLogin);

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String storeName = String.valueOf(etStoreName.getText()).trim();
                String email = String.valueOf(etUsername.getText()).trim();
                String password = String.valueOf(etPassword.getText()).trim();
                String confirmPassword = String.valueOf(etConfirmPassword.getText()).trim();

                if (storeName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                    ViewUtils.toast(RegisterActivity.this, getString(R.string.field_required));
                    return;
                }
                if (!email.contains("@")) {
                    ViewUtils.toast(RegisterActivity.this, getString(R.string.invalid_email));
                    return;
                }

                if (!password.equals(confirmPassword)) {
                    ViewUtils.toast(RegisterActivity.this, getString(R.string.password_not_match));
                    return;
                }

                setLoading(true, btnRegister, tvLogin);
                if (SupabaseConfig.isConfigured()) {
                    com.daspos.shared.util.DbExecutor.runAsync(() ->
                                    SupabaseAuthService.signupWithEmailActivation(email, password, storeName),
                            signupResult -> {
                                setLoading(false, btnRegister, tvLogin);
                                if (!signupResult.success) {
                                    String msg = signupResult.message == null || signupResult.message.trim().isEmpty()
                                            ? getString(R.string.register_failed)
                                            : signupResult.message;
                                    ViewUtils.toast(RegisterActivity.this, msg);
                                    return;
                                }
                                ViewUtils.toast(RegisterActivity.this, getString(R.string.register_activation_sent));
                                Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                                intent.putExtra(LoginActivity.EXTRA_PREFILL_EMAIL, email);
                                startActivity(intent);
                                finish();
                            }, throwable -> {
                                setLoading(false, btnRegister, tvLogin);
                                String msg = throwable == null || throwable.getMessage() == null
                                        ? getString(R.string.register_failed)
                                        : throwable.getMessage();
                                ViewUtils.toast(RegisterActivity.this, msg);
                            });
                    return;
                }
                UserRepository.usernameExistsAsync(RegisterActivity.this, email, exists -> {
                    if (exists) {
                        setLoading(false, btnRegister, tvLogin);
                        ViewUtils.toast(RegisterActivity.this, getString(R.string.email_already_exists));
                        return;
                    }

                    StoreConfigStore.save(
                            RegisterActivity.this,
                            storeName,
                            StoreConfigStore.getAddress(RegisterActivity.this),
                            StoreConfigStore.getPhone(RegisterActivity.this),
                            email
                    );

                    UserRepository.addAsync(RegisterActivity.this, email, password, "Admin", () -> {
                        setLoading(false, btnRegister, tvLogin);
                        ViewUtils.toast(RegisterActivity.this, getString(R.string.register_success));
                        startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                        finish();
                    }, throwable -> {
                        setLoading(false, btnRegister, tvLogin);
                        ViewUtils.toast(RegisterActivity.this, getString(R.string.register_failed));
                    });
                }, throwable -> {
                    setLoading(false, btnRegister, tvLogin);
                    ViewUtils.toast(RegisterActivity.this, getString(R.string.register_failed));
                });
            }
        });
        tvLogin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    private void setLoading(boolean isLoading, MaterialButton btnRegister, TextView tvLogin) {
        btnRegister.setEnabled(!isLoading);
        tvLogin.setEnabled(!isLoading);
        if (isLoading) {
            loadingDialog = LoadingDialogHelper.show(this, getString(R.string.loading));
        } else {
            LoadingDialogHelper.dismiss(loadingDialog);
            loadingDialog = null;
        }
    }
}
