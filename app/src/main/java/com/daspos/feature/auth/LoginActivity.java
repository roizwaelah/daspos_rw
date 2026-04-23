package com.daspos.feature.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.core.app.HomeActivity;
import com.daspos.core.app.SplashActivity;
import com.daspos.feature.settings.StoreConfigStore;
import com.daspos.repository.UserRepository;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.LoadingDialogHelper;
import com.daspos.shared.util.NetworkUtils;
import com.daspos.shared.util.PasswordFieldToggleHelper;
import com.daspos.shared.util.ViewUtils;
import com.daspos.supabase.SupabaseAuthService;
import com.daspos.supabase.SupabaseConfig;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends BaseActivity {
    public static final String EXTRA_PREFILL_EMAIL = "extra_prefill_email";
    private AlertDialog loadingDialog;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        final TextInputEditText etUsername = findViewById(R.id.etUsername);
        final TextInputEditText etPassword = findViewById(R.id.etPassword);
        PasswordFieldToggleHelper.attach(etPassword);
        MaterialButton btnLogin = findViewById(R.id.btnLogin);
        TextView tvRegister = findViewById(R.id.tvRegister);
        TextView tvResendVerification = findViewById(R.id.tvResendVerification);

        String prefillEmail = getIntent().getStringExtra(EXTRA_PREFILL_EMAIL);
        if (prefillEmail != null && !prefillEmail.trim().isEmpty()) {
            etUsername.setText(prefillEmail.trim());
        }

        String authMessage = getIntent().getStringExtra(SplashActivity.EXTRA_AUTH_MESSAGE);
        if (authMessage != null && !authMessage.trim().isEmpty()) {
            ViewUtils.toast(this, authMessage);
        }

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String email = String.valueOf(etUsername.getText()).trim();
                String pass = String.valueOf(etPassword.getText()).trim();
                if (email.isEmpty() || pass.isEmpty()) {
                    ViewUtils.toast(LoginActivity.this, getString(R.string.field_required));
                    return;
                }

                setLoading(true, btnLogin, tvRegister);
                DbExecutor.runAsync(() -> authenticate(email, pass), result -> {
                    setLoading(false, btnLogin, tvRegister);
                    if (!result.authenticated) {
                        ViewUtils.toast(LoginActivity.this, getString(R.string.login_failed));
                        return;
                    }
                    AuthSessionStore.saveSession(
                            LoginActivity.this,
                            result.username,
                            result.role,
                            result.userId,
                            result.accessToken,
                            result.refreshToken,
                            result.source,
                            result.outletId
                    );
                    if ("supabase".equalsIgnoreCase(result.source)) {
                        if ("kasir".equalsIgnoreCase(result.role)) {
                            List<String> menusFromServer = result.allowedMenus == null ? new ArrayList<>() : result.allowedMenus;
                            if (!menusFromServer.isEmpty()) {
                                MenuAccessStore.saveForUser(LoginActivity.this, result.username, menusFromServer);
                            } else if (MenuAccessStore.getStoredForUser(LoginActivity.this, result.username).isEmpty()) {
                                MenuAccessStore.saveForUser(LoginActivity.this, result.username, defaultCashierMenus());
                            }
                        }
                        StoreConfigStore.save(
                                LoginActivity.this,
                                result.outletName,
                                result.outletAddress,
                                result.outletPhone,
                                result.outletEmail
                        );
                        AuthSessionStore.saveOfflineSupabaseCredential(
                                LoginActivity.this,
                                result.username,
                                pass,
                                result.role,
                                result.userId,
                                result.outletId,
                                result.outletName,
                                result.outletAddress,
                                result.outletPhone,
                                result.outletEmail,
                                result.accessToken,
                                result.refreshToken
                        );
                    }
                    AuthSessionStore.saveLastBackgroundAt(LoginActivity.this, 0L);
                    startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                    finish();
                }, throwable -> {
                    setLoading(false, btnLogin, tvRegister);
                    ViewUtils.toast(LoginActivity.this, getString(R.string.login_failed));
                });
            }
        });
        tvRegister.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(LoginActivity.this, RegisterActivity.class)); }
        });
        tvResendVerification.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!SupabaseConfig.isConfigured()) {
                    ViewUtils.toast(LoginActivity.this, getString(R.string.supabase_only_feature));
                    return;
                }
                String email = String.valueOf(etUsername.getText()).trim();
                if (email.isEmpty()) {
                    ViewUtils.toast(LoginActivity.this, getString(R.string.email_required));
                    return;
                }
                if (!email.contains("@")) {
                    ViewUtils.toast(LoginActivity.this, getString(R.string.invalid_email));
                    return;
                }

                setLoading(true, btnLogin, tvRegister);
                tvResendVerification.setEnabled(false);
                DbExecutor.runAsync(() -> SupabaseAuthService.resendSignupVerification(email), result -> {
                    setLoading(false, btnLogin, tvRegister);
                    tvResendVerification.setEnabled(true);
                    if (result.success) {
                        ViewUtils.toast(LoginActivity.this, getString(R.string.resend_verification_success));
                    } else {
                        String msg = result.message == null || result.message.trim().isEmpty()
                                ? getString(R.string.resend_verification_failed)
                                : result.message;
                        ViewUtils.toast(LoginActivity.this, msg);
                    }
                }, throwable -> {
                    setLoading(false, btnLogin, tvRegister);
                    tvResendVerification.setEnabled(true);
                    String msg = throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()
                            ? getString(R.string.resend_verification_failed)
                            : throwable.getMessage().trim();
                    ViewUtils.toast(LoginActivity.this, msg);
                });
            }
        });
    }

    private List<String> defaultCashierMenus() {
        List<String> out = new ArrayList<>();
        out.add(MenuAccessStore.MENU_TRANSACTION);
        out.add(MenuAccessStore.MENU_PRODUCT);
        out.add(MenuAccessStore.MENU_BEST_SELLER);
        out.add(MenuAccessStore.MENU_REPORT);
        return out;
    }

    private AuthOutcome authenticate(String email, String password) {
        boolean supabaseConfigured = SupabaseConfig.isConfigured();
        boolean online = NetworkUtils.isOnline(this);

        if (supabaseConfigured && online) {
            SupabaseAuthService.AuthResult remoteResult = SupabaseAuthService.loginWithPassword(email, password);
            if (remoteResult.authenticated) {
                return AuthOutcome.supabase(remoteResult);
            }
        }
        if (supabaseConfigured && !online) {
            AuthSessionStore.OfflineSupabaseSession offlineSession =
                    AuthSessionStore.tryOfflineSupabaseLogin(this, email, password);
            if (offlineSession != null) {
                return AuthOutcome.supabaseOffline(offlineSession);
            }
        }

        boolean authenticated = UserRepository.authenticate(LoginActivity.this, email, password);
        if (!authenticated) {
            return AuthOutcome.failed();
        }
        com.daspos.model.User user = UserRepository.getByUsername(LoginActivity.this, email);
        String role = user == null ? "" : user.getRole();
        return AuthOutcome.local(email, role);
    }

    private static final class AuthOutcome {
        private final boolean authenticated;
        private final String username;
        private final String role;
        private final String userId;
        private final String accessToken;
        private final String refreshToken;
        private final String source;
        private final java.util.List<String> allowedMenus;
        private final String outletId;
        private final String outletName;
        private final String outletAddress;
        private final String outletPhone;
        private final String outletEmail;

        private AuthOutcome(
                boolean authenticated,
                String username,
                String role,
                String userId,
                String accessToken,
                String refreshToken,
                String source,
                java.util.List<String> allowedMenus,
                String outletId,
                String outletName,
                String outletAddress,
                String outletPhone,
                String outletEmail
        ) {
            this.authenticated = authenticated;
            this.username = username;
            this.role = role;
            this.userId = userId;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.source = source;
            this.allowedMenus = allowedMenus == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(allowedMenus);
            this.outletId = outletId;
            this.outletName = outletName;
            this.outletAddress = outletAddress;
            this.outletPhone = outletPhone;
            this.outletEmail = outletEmail;
        }

        private static AuthOutcome local(String username, String role) {
            return new AuthOutcome(true, username, role, "", "", "", "local", new java.util.ArrayList<>(), "", "", "", "", "");
        }

        private static AuthOutcome supabase(SupabaseAuthService.AuthResult result) {
            return new AuthOutcome(
                    true,
                    result.username,
                    result.role,
                    result.userId,
                    result.accessToken,
                    result.refreshToken,
                    "supabase",
                    result.allowedMenus,
                    result.outletId,
                    result.outletName,
                    result.outletAddress,
                    result.outletPhone,
                    result.outletEmail
            );
        }

        private static AuthOutcome supabaseOffline(AuthSessionStore.OfflineSupabaseSession session) {
            return new AuthOutcome(
                    true,
                    session.username,
                    session.role,
                    session.userId,
                    session.accessToken,
                    session.refreshToken,
                    "supabase",
                    new java.util.ArrayList<>(),
                    session.outletId,
                    session.outletName,
                    session.outletAddress,
                    session.outletPhone,
                    session.outletEmail
            );
        }

        private static AuthOutcome failed() {
            return new AuthOutcome(false, "", "", "", "", "", "", new java.util.ArrayList<>(), "", "", "", "", "");
        }
    }

    private void setLoading(boolean isLoading, MaterialButton btnLogin, TextView tvRegister) {
        btnLogin.setEnabled(!isLoading);
        tvRegister.setEnabled(!isLoading);
        if (isLoading) {
            loadingDialog = LoadingDialogHelper.show(this, getString(R.string.loading));
        } else {
            LoadingDialogHelper.dismiss(loadingDialog);
            loadingDialog = null;
        }
    }
}
