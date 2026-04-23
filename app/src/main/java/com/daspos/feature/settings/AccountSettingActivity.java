package com.daspos.feature.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.BuildConfig;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.feature.auth.LoginActivity;
import com.daspos.repository.UserRepository;
import com.daspos.repository.TransactionRepository;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.LoadingDialogHelper;
import com.daspos.shared.util.NetworkUtils;
import com.daspos.shared.util.NotificationDialogHelper;
import com.daspos.shared.util.ViewUtils;
import com.daspos.supabase.SupabaseAuthService;
import com.daspos.supabase.SupabaseConfig;

public class AccountSettingActivity extends BaseActivity {
    private AlertDialog loadingDialog;

    @Override protected void onResume() {
        super.onResume();
        bindAccountInfo();
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_setting);
        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.account));
        bindAccountInfo();
        bindSettingItem(R.id.itemChangePassword, R.string.change_password, R.drawable.ic_lock);
        bindSettingItem(R.id.itemAccountLogout, R.string.logout, R.drawable.ic_logout);
        bindSettingItem(R.id.itemDeleteAccount, R.string.delete_account, R.drawable.ic_delete_account);
        bindSettingItem(R.id.itemDeleteOutletPurge, R.string.purge_outlet, R.drawable.ic_delete_outlet);
        findViewById(R.id.itemDeleteOutletPurge).setVisibility(AuthSessionStore.isAdmin(this) ? View.VISIBLE : View.GONE);

        findViewById(R.id.itemChangePassword).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(AccountSettingActivity.this, ChangePasswordActivity.class)); }
        });
        findViewById(R.id.itemAccountLogout).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                NotificationDialogHelper.showWarningConfirmation(
                        AccountSettingActivity.this,
                        R.string.logout,
                        R.string.logout_confirm_message,
                        R.string.logout,
                        () -> {
                            AuthSessionStore.clearSession(AccountSettingActivity.this);
                            startActivity(new Intent(AccountSettingActivity.this, LoginActivity.class));
                            finishAffinity();
                        }
                );
            }
        });
        findViewById(R.id.itemDeleteAccount).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                NotificationDialogHelper.showWarningConfirmation(
                        AccountSettingActivity.this,
                        R.string.delete_account,
                        R.string.delete_account_confirm_message,
                        R.string.delete_account,
                        () -> deleteCurrentAccount(false)
                );
            }
        });
        findViewById(R.id.itemDeleteOutletPurge).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                NotificationDialogHelper.showWarningConfirmation(
                        AccountSettingActivity.this,
                        R.string.purge_outlet,
                        R.string.purge_outlet_confirm_message,
                        R.string.delete_account,
                        () -> deleteCurrentAccount(true)
                );
            }
        });
    }

    private void bindAccountInfo() {
        String email = AuthSessionStore.getUsername(this);
        String role = AuthSessionStore.getRole(this);
        String storeName = StoreConfigStore.getStoreName(this);

        if (email == null || email.trim().isEmpty()) email = "-";
        if (role == null || role.trim().isEmpty()) role = "-";
        if (storeName == null || storeName.trim().isEmpty()) storeName = "-";
        String source = AuthSessionStore.getSource(this);
        boolean supabaseSessionValid = AuthSessionStore.hasValidSupabaseSession(this);
        boolean repoSupabaseActive = TransactionRepository.isSupabaseSessionActive(this);
        String debugInfo = "Build " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                + "\nSource: " + source
                + "\nSupabaseSessionValid: " + (supabaseSessionValid ? "YES" : "NO")
                + "\nRepoSupabaseActive: " + (repoSupabaseActive ? "YES" : "NO")
                + "\nReportQueryMode: period-filter";

        ((TextView) findViewById(R.id.tvUsernameInfo)).setText(getString(R.string.account_username_format, email));
        ((TextView) findViewById(R.id.tvRoleInfo)).setText(getString(R.string.account_role_format, role));
        ((TextView) findViewById(R.id.tvStoreInfo)).setText(getString(R.string.account_store_format, storeName));
        ((TextView) findViewById(R.id.tvDebugInfo)).setText(debugInfo);
    }

    private void bindSettingItem(int includeId, int textRes, int iconRes) {
        View include = findViewById(includeId);
        ((TextView) include.findViewById(R.id.tvSettingTitle)).setText(getString(textRes));
        ((ImageView) include.findViewById(R.id.imgSettingIcon)).setImageResource(iconRes);
    }

    private void deleteCurrentAccount(boolean purgeOutlet) {
        String email = AuthSessionStore.getUsername(this);
        if (email == null || email.trim().isEmpty()) {
            ViewUtils.toast(this, getString(R.string.delete_account_failed));
            return;
        }
        String source = AuthSessionStore.getSource(this);
        if ("supabase".equalsIgnoreCase(source)) {
            if (!SupabaseConfig.isConfigured() || !NetworkUtils.isOnline(this)) {
                ViewUtils.toast(this, getString(R.string.delete_account_need_internet));
                return;
            }

            setLoading(true);
            String accessToken = AuthSessionStore.getAccessToken(this);
            String userId = AuthSessionStore.getUserId(this);
            DbExecutor.runAsync(
                    () -> SupabaseAuthService.deleteCurrentAccount(accessToken, userId, purgeOutlet),
                    result -> {
                        setLoading(false);
                        if (!result.success) {
                            String msg = result.message == null || result.message.trim().isEmpty()
                                    ? getString(purgeOutlet ? R.string.purge_outlet_failed : R.string.delete_account_failed)
                                    : result.message;
                            ViewUtils.toast(AccountSettingActivity.this, msg);
                            return;
                        }
                        UserRepository.deleteAsync(
                                AccountSettingActivity.this,
                                email,
                                () -> completeDeleteAccountSuccess(purgeOutlet),
                                throwable -> completeDeleteAccountSuccess(purgeOutlet)
                        );
                    },
                    throwable -> {
                        setLoading(false);
                        String msg = throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()
                                ? getString(purgeOutlet ? R.string.purge_outlet_failed : R.string.delete_account_failed)
                                : throwable.getMessage().trim();
                        ViewUtils.toast(AccountSettingActivity.this, msg);
                    }
            );
            return;
        }

        setLoading(true);
        UserRepository.deleteAsync(
                this,
                email,
                () -> {
                    setLoading(false);
                    completeDeleteAccountSuccess(purgeOutlet);
                },
                throwable -> {
                    setLoading(false);
                    ViewUtils.toast(AccountSettingActivity.this, getString(purgeOutlet ? R.string.purge_outlet_failed : R.string.delete_account_failed));
                }
        );
    }

    private void completeDeleteAccountSuccess(boolean purgeOutlet) {
        ViewUtils.toast(this, getString(purgeOutlet ? R.string.purge_outlet_success : R.string.delete_account_success));
        AuthSessionStore.clearSession(this);
        startActivity(new Intent(this, LoginActivity.class));
        finishAffinity();
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            loadingDialog = LoadingDialogHelper.show(this, getString(R.string.loading));
            return;
        }
        LoadingDialogHelper.dismiss(loadingDialog);
        loadingDialog = null;
    }
}
