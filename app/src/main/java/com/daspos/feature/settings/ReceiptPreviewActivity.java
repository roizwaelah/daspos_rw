package com.daspos.feature.settings;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.shared.util.ViewUtils;

public class ReceiptPreviewActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_preview);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.receipt_preview));

        String receiptHeader = ReceiptConfigStore.getHeader(this);
        String receiptFooter = ReceiptConfigStore.getFooter(this);
        boolean showLogo = ReceiptConfigStore.shouldShowLogo(this);

        TextView headerView = findViewById(R.id.tvReceiptHeader);
        ((TextView) findViewById(R.id.tvReceiptStoreName)).setText(StoreConfigStore.getStoreName(this));
        ((TextView) findViewById(R.id.tvReceiptAddress)).setText(StoreConfigStore.getAddress(this));
        ((TextView) findViewById(R.id.tvReceiptPhone)).setText(StoreConfigStore.getPhone(this));
        ((TextView) findViewById(R.id.tvReceiptEmail)).setText(StoreConfigStore.getEmail(this));
        ((TextView) findViewById(R.id.tvReceiptFooter)).setText(receiptFooter);

        headerView.setText(receiptHeader);
        headerView.setVisibility(receiptHeader.isEmpty() ? View.GONE : View.VISIBLE);

        boolean hasIdentityLine = !StoreConfigStore.getAddress(this).isEmpty()
                || !StoreConfigStore.getPhone(this).isEmpty()
                || !StoreConfigStore.getEmail(this).isEmpty();
        findViewById(R.id.layoutReceiptIdentity).setVisibility(hasIdentityLine ? View.VISIBLE : View.GONE);

        ImageView logo = findViewById(R.id.imgReceiptStoreLogo);
        String logoUri = StoreConfigStore.getLogoUri(this);
        if (showLogo && logoUri != null && !logoUri.trim().isEmpty()) {
            try {
                logo.setImageURI(Uri.parse(logoUri));
                logo.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                logo.setVisibility(View.GONE);
            }
        } else {
            logo.setVisibility(View.GONE);
        }
    }
}
