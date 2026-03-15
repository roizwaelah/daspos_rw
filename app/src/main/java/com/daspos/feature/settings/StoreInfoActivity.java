package com.daspos.feature.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.shared.util.ViewUtils;

public class StoreInfoActivity extends BaseActivity {
    private static final int REQ_PICK_LOGO = 1201;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store_info);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.business_info));

        final EditText etStoreName = findViewById(R.id.etStoreName);
        final EditText etStoreAddress = findViewById(R.id.etStoreAddress);
        final EditText etStorePhone = findViewById(R.id.etStorePhone);
        final EditText etStoreEmail = findViewById(R.id.etStoreEmail);

        etStoreName.setText(StoreConfigStore.getStoreName(this));
        etStoreAddress.setText(StoreConfigStore.getAddress(this));
        etStorePhone.setText(StoreConfigStore.getPhone(this));
        etStoreEmail.setText(StoreConfigStore.getEmail(this));
        renderLogo();

        findViewById(R.id.btnSelectLogo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(intent, REQ_PICK_LOGO);
            }
        });
        findViewById(R.id.btnRemoveLogo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                StoreConfigStore.clearLogo(StoreInfoActivity.this);
                renderLogo();
                ViewUtils.toast(StoreInfoActivity.this, "Logo dihapus");
            }
        });
        findViewById(R.id.btnStoreCancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnStoreSave).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                StoreConfigStore.save(
                        StoreInfoActivity.this,
                        etStoreName.getText().toString(),
                        etStoreAddress.getText().toString(),
                        etStorePhone.getText().toString(),
                        etStoreEmail.getText().toString()
                );
                ViewUtils.toast(StoreInfoActivity.this, getString(R.string.saved_success));
                finish();
            }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_LOGO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                final int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) { }
            StoreConfigStore.saveLogoUri(this, uri.toString());
            renderLogo();
            ViewUtils.toast(this, "Logo toko dipilih");
        }
    }

    private void renderLogo() {
        ImageView img = findViewById(R.id.imgStoreLogo);
        String logoUri = StoreConfigStore.getLogoUri(this);
        if (logoUri != null && !logoUri.trim().isEmpty()) {
            try {
                img.setImageURI(Uri.parse(logoUri));
            } catch (Exception ignored) {
                img.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        } else {
            img.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }
}
