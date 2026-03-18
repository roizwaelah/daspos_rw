package com.daspos.feature.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;

import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.shared.util.ViewUtils;

public class ReceiptSettingActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_setting);
        Toolbar toolbar = findViewById(R.id.toolbar);
        final EditText etReceiptHeader = findViewById(R.id.etReceiptHeader);
        final EditText etReceiptFooter = findViewById(R.id.etReceiptFooter);
        final Switch switchReceiptLogo = findViewById(R.id.switchReceiptLogo);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.receipt));
        etReceiptHeader.setText(ReceiptConfigStore.getHeader(this));
        etReceiptFooter.setText(ReceiptConfigStore.getFooter(this));
        switchReceiptLogo.setChecked(ReceiptConfigStore.shouldShowLogo(this));
        findViewById(R.id.btnPreviewReceipt).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ReceiptConfigStore.save(
                        ReceiptSettingActivity.this,
                        etReceiptHeader.getText().toString(),
                        etReceiptFooter.getText().toString(),
                        switchReceiptLogo.isChecked()
                );
                startActivity(new Intent(ReceiptSettingActivity.this, ReceiptPreviewActivity.class));
            }
        });
        findViewById(R.id.btnReceiptCancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnReceiptSave).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ReceiptConfigStore.save(
                        ReceiptSettingActivity.this,
                        etReceiptHeader.getText().toString(),
                        etReceiptFooter.getText().toString(),
                        switchReceiptLogo.isChecked()
                );
                ViewUtils.toast(ReceiptSettingActivity.this, getString(R.string.saved_success));
            }
        });
    }
}
