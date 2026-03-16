package com.daspos.feature.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.feature.auth.LoginActivity;
import com.daspos.shared.util.NotificationDialogHelper;
import com.daspos.shared.util.ViewUtils;

public class AccountSettingActivity extends BaseActivity {
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
    }

    private void bindAccountInfo() {
        String username = AuthSessionStore.getUsername(this);
        String role = AuthSessionStore.getRole(this);
        String storeName = StoreConfigStore.getStoreName(this);

        if (username == null || username.trim().isEmpty()) username = "-";
        if (role == null || role.trim().isEmpty()) role = "-";
        if (storeName == null || storeName.trim().isEmpty()) storeName = "-";

        ((TextView) findViewById(R.id.tvUsernameInfo)).setText(getString(R.string.account_username_format, username));
        ((TextView) findViewById(R.id.tvRoleInfo)).setText(getString(R.string.account_role_format, role));
        ((TextView) findViewById(R.id.tvStoreInfo)).setText(getString(R.string.account_store_format, storeName));
    }

    private void bindSettingItem(int includeId, int textRes, int iconRes) {
        View include = findViewById(includeId);
        ((TextView) include.findViewById(R.id.tvSettingTitle)).setText(getString(textRes));
        ((ImageView) include.findViewById(R.id.imgSettingIcon)).setImageResource(iconRes);
    }
}
