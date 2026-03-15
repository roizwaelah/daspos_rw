package com.daspos.feature.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.shared.util.ViewUtils;

public class ReceiptSettingActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_setting);
        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.receipt));
        findViewById(R.id.btnPreviewReceipt).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(ReceiptSettingActivity.this, ReceiptPreviewActivity.class)); }
        });
        findViewById(R.id.btnReceiptCancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnReceiptSave).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ViewUtils.toast(ReceiptSettingActivity.this, getString(R.string.saved_success)); }
        });
    }
}
