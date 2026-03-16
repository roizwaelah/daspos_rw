package com.daspos.feature.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.auth.LoginActivity;
import com.daspos.shared.util.ViewUtils;

public class SettingActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.settings));

        bindSettingItem(R.id.itemStoreInfo, R.string.business_info, R.drawable.ic_store);
        bindSettingItem(R.id.itemPrinter, R.string.printer, R.drawable.ic_menu_printer);
        bindSettingItem(R.id.itemReceipt, R.string.receipt, R.drawable.ic_menu_transaction);
        bindSettingItem(R.id.itemAccount, R.string.account, R.drawable.ic_account);
        bindSettingItem(R.id.itemContact, R.string.contact_us, R.drawable.ic_whatsapp);
        bindSettingItem(R.id.itemSelectDatabase, R.string.select_database, R.drawable.ic_menu_database);
        bindSettingItem(R.id.itemAppInfo, R.string.app_info, R.drawable.ic_info);

        findViewById(R.id.itemStoreInfo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingActivity.this, StoreInfoActivity.class));
            }
        });

        findViewById(R.id.itemPrinter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingActivity.this, com.daspos.feature.printer.PrinterSettingActivity.class));
            }
        });

        findViewById(R.id.itemReceipt).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingActivity.this, ReceiptSettingActivity.class));
            }
        });

        findViewById(R.id.itemAccount).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingActivity.this, AccountSettingActivity.class));
            }
        });

        findViewById(R.id.itemContact).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingActivity.this, ContactUsActivity.class));
            }
        });

        findViewById(R.id.itemSelectDatabase).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingActivity.this, SelectDatabaseActivity.class));
            }
        });

        findViewById(R.id.itemAppInfo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingActivity.this, AppInfoActivity.class));
            }
        });

        findViewById(R.id.btnLogout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(SettingActivity.this)
                        .setTitle(R.string.logout)
                        .setMessage(R.string.logout_confirm_message)
                        .setPositiveButton(R.string.logout, (dialog, which) -> {
                            startActivity(new Intent(SettingActivity.this, LoginActivity.class));
                            finishAffinity();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
    }

    private void bindSettingItem(int includeId, int textRes, int iconRes) {
        View include = findViewById(includeId);
        ((TextView) include.findViewById(R.id.tvSettingTitle)).setText(getString(textRes));
        ((ImageView) include.findViewById(R.id.imgSettingIcon)).setImageResource(iconRes);
    }
}
