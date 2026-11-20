package com.daspos.feature.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.settings.StoreConfigStore;
import com.daspos.remote.RemoteAuthBridgeService;
import com.daspos.repository.UserRepository;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.NetworkUtils;
import com.daspos.shared.util.LoadingDialogHelper;
import com.daspos.shared.util.PasswordFieldToggleHelper;
import com.daspos.shared.util.ViewUtils;
import com.daspos.supabase.SupabaseAuthService;
import com.daspos.supabase.SupabaseConfig;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends BaseActivity {
    private AlertDialog loadingDialog;
    private boolean setupFlow;

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
        TextView tvSetupStep = findViewById(R.id.tvSetupStep);
        TextView tvSetupHint = findViewById(R.id.tvSetupHint);
        setupFlow = getIntent().getBooleanExtra(LoginActivity.EXTRA_SETUP_FLOW, false);
        tvSetupStep.setVisibility(setupFlow ? View.VISIBLE : View.GONE);
        tvSetupHint.setVisibility(setupFlow ? View.VISIBLE : View.GONE);
        if (AuthSessionStore.isRemoteDatabaseMode(this)) {
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
                if (AuthSessionStore.isRemoteDatabaseMode(RegisterActivity.this)) {
                    handleRemoteRegister(email, password, storeName, btnRegister, tvLogin);
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
                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                        intent.putExtra(LoginActivity.EXTRA_SETUP_FLOW, setupFlow);
                        startActivity(intent);
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

    private void handleRemoteRegister(
            String email,
            String password,
            String storeName,
            MaterialButton btnRegister,
            TextView tvLogin
    ) {
        String providerType = AuthSessionStore.getRemoteProviderType(this);
        if (!NetworkUtils.isOnline(this)) {
            setLoading(false, btnRegister, tvLogin);
            ViewUtils.toast(this, getString(R.string.register_need_internet));
            return;
        }
        if (AuthSessionStore.PROVIDER_SUPABASE.equalsIgnoreCase(providerType)) {
            if (!SupabaseConfig.isConfigured()) {
                setLoading(false, btnRegister, tvLogin);
                ViewUtils.toast(this, getString(R.string.register_failed));
                return;
            }
            DbExecutor.runAsync(() -> SupabaseAuthService.signupWithEmailActivation(email, password, storeName),
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
                        intent.putExtra(LoginActivity.EXTRA_SETUP_FLOW, setupFlow);
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

        DbExecutor.runAsync(() -> RemoteAuthBridgeService.register(RegisterActivity.this, providerType, email, password, storeName),
                result -> {
                    setLoading(false, btnRegister, tvLogin);
                    if (!result.success) {
                        String msg = result.message == null || result.message.trim().isEmpty()
                                ? getString(R.string.register_failed)
                                : result.message;
                        ViewUtils.toast(RegisterActivity.this, msg);
                        return;
                    }
                    ViewUtils.toast(
                            RegisterActivity.this,
                            result.requiresEmailVerification
                                    ? getString(R.string.register_activation_sent)
                                    : getString(R.string.register_success)
                    );
                    Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                    intent.putExtra(LoginActivity.EXTRA_PREFILL_EMAIL, email);
                    intent.putExtra(LoginActivity.EXTRA_SETUP_FLOW, setupFlow);
                    startActivity(intent);
                    finish();
                }, throwable -> {
                    setLoading(false, btnRegister, tvLogin);
                    String msg = throwable == null || throwable.getMessage() == null
                            ? getString(R.string.register_failed)
                            : throwable.getMessage();
                    ViewUtils.toast(RegisterActivity.this, msg);
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
