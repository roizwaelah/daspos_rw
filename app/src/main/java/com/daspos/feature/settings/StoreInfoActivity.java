package com.daspos.feature.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.LoadingDialogHelper;
import com.daspos.shared.util.NetworkUtils;
import com.daspos.shared.util.ViewUtils;
import com.daspos.supabase.SupabaseConfig;
import com.daspos.supabase.SupabaseOutletService;

import java.util.Locale;

public class StoreInfoActivity extends BaseActivity {
    private static final int REQ_PICK_LOGO = 1201;
    private AlertDialog loadingDialog;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthSessionStore.isAdmin(this)) {
            ViewUtils.toast(this, getString(R.string.admin_only_feature));
            finish();
            return;
        }
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
        renderLogo(etStoreName.getText().toString());
        etStoreName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderLogo(String.valueOf(s));
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        findViewById(R.id.btnSelectLogo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, REQ_PICK_LOGO);
            }
        });
        findViewById(R.id.btnRemoveLogo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                StoreConfigStore.clearLogo(StoreInfoActivity.this);
                renderLogo(etStoreName.getText().toString());
                ViewUtils.toast(StoreInfoActivity.this, "Logo dihapus");
            }
        });
        findViewById(R.id.btnStoreCancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnStoreSave).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String storeName = etStoreName.getText().toString();
                final String storeAddress = etStoreAddress.getText().toString();
                final String storePhone = etStorePhone.getText().toString();
                final String storeEmail = etStoreEmail.getText().toString();

                StoreConfigStore.save(
                        StoreInfoActivity.this,
                        storeName,
                        storeAddress,
                        storePhone,
                        storeEmail
                );
                String syncBlockedReason = getSyncBlockedReason();
                if (syncBlockedReason != null) {
                    ViewUtils.toast(
                            StoreInfoActivity.this,
                            getString(R.string.saved_local_sync_pending) + " (" + syncBlockedReason + ")"
                    );
                    finish();
                    return;
                }

                setLoading(true);
                final String outletId = AuthSessionStore.getOutletId(StoreInfoActivity.this);
                final String accessToken = AuthSessionStore.getAccessToken(StoreInfoActivity.this);
                DbExecutor.runAsync(() -> {
                    SupabaseOutletService.updateOutletInfo(
                            outletId,
                            accessToken,
                            storeName,
                            storeAddress,
                            storePhone,
                            storeEmail
                    );
                    return null;
                }, ignored -> {
                    setLoading(false);
                    ViewUtils.toast(StoreInfoActivity.this, getString(R.string.saved_success));
                    finish();
                }, throwable -> {
                    setLoading(false);
                    String err = throwable == null || throwable.getMessage() == null
                            ? ""
                            : throwable.getMessage().trim();
                    if (err.isEmpty()) {
                        ViewUtils.toast(StoreInfoActivity.this, getString(R.string.saved_local_sync_failed));
                    } else {
                        ViewUtils.toast(StoreInfoActivity.this, getString(R.string.saved_local_sync_failed) + ": " + err);
                    }
                    finish();
                });
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
            EditText etStoreName = findViewById(R.id.etStoreName);
            renderLogo(etStoreName.getText().toString());
            ViewUtils.toast(this, "Logo toko dipilih");
        }
    }

    private void renderLogo(String storeName) {
        ImageView img = findViewById(R.id.imgStoreLogo);
        TextView tvInitial = findViewById(R.id.tvStoreLogoInitial);
        String logoUri = StoreConfigStore.getLogoUri(this);
        if (logoUri != null && !logoUri.trim().isEmpty()) {
            try {
                img.setImageURI(Uri.parse(logoUri));
                tvInitial.setVisibility(View.GONE);
            } catch (Exception ignored) {
                img.setImageDrawable(null);
                tvInitial.setText(extractStoreInitial(storeName));
                tvInitial.setVisibility(View.VISIBLE);
            }
        } else {
            img.setImageDrawable(null);
            tvInitial.setText(extractStoreInitial(storeName));
            tvInitial.setVisibility(View.VISIBLE);
        }
    }

    private String extractStoreInitial(String storeName) {
        if (storeName == null) return "D";
        String trimmed = storeName.trim();
        if (trimmed.isEmpty()) return "D";
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String getSyncBlockedReason() {
        if (!SupabaseConfig.isConfigured()) return "Supabase belum dikonfigurasi";
        String outletId = AuthSessionStore.getOutletId(this);
        String accessToken = AuthSessionStore.getAccessToken(this);
        if (outletId == null || outletId.trim().isEmpty()
                || accessToken == null || accessToken.trim().isEmpty()) {
            return "Sesi Supabase tidak valid, login ulang diperlukan";
        }
        if (!NetworkUtils.isOnline(this)) return "Perangkat offline";
        return null;
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
