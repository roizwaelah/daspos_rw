package com.daspos.feature.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.auth.LoginActivity;
import com.daspos.shared.util.ViewUtils;

public class AccountSettingActivity extends BaseActivity {
    @Override protected void onResume() {
        super.onResume();
        ((TextView) findViewById(R.id.tvStoreInfo)).setText("Toko: " + StoreConfigStore.getStoreName(this));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_setting);
        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.account));

        ((TextView) findViewById(R.id.tvStoreInfo)).setText("Toko: " + StoreConfigStore.getStoreName(this));

        findViewById(R.id.itemChangePassword).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(AccountSettingActivity.this, ChangePasswordActivity.class)); }
        });
        findViewById(R.id.itemAccountLogout).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(AccountSettingActivity.this, LoginActivity.class));
                finishAffinity();
            }
        });
    }
}
