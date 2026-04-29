package com.daspos.feature.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.shared.util.NetworkUtils;
import com.daspos.shared.util.ViewUtils;
import com.daspos.supabase.SupabaseConfig;

public class SelectDatabaseActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthSessionStore.isAdmin(this)) {
            ViewUtils.toast(this, getString(R.string.admin_only_feature));
            finish();
            return;
        }
        setContentView(R.layout.activity_select_database);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.select_database));

        final RadioGroup rg = findViewById(R.id.rgDatabase);
        final LinearLayout layoutRemote = findViewById(R.id.layoutRemote);
        final TextView tvSupabaseUrl = findViewById(R.id.tvSupabaseUrl);
        final TextView tvSupabaseStatus = findViewById(R.id.tvSupabaseStatus);

        tvSupabaseUrl.setText(SupabaseConfig.getUrl().isEmpty() ? "-" : SupabaseConfig.getUrl());
        tvSupabaseStatus.setText(SupabaseConfig.isConfigured() ? "Terkonfigurasi" : "Belum dikonfigurasi");

        String activeMode = AuthSessionStore.getDatabaseMode(this);
        if (AuthSessionStore.DATABASE_MODE_REMOTE.equalsIgnoreCase(activeMode)) {
            rg.check(R.id.rbRemote);
            layoutRemote.setVisibility(View.VISIBLE);
        } else {
            rg.check(R.id.rbLocal);
            layoutRemote.setVisibility(View.GONE);
        }

        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                View radio = findViewById(checkedId);
                int index = group.indexOfChild(radio);
                layoutRemote.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
            }
        });

        findViewById(R.id.btnTestConnection).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (rg.getCheckedRadioButtonId() != R.id.rbRemote) {
                    ViewUtils.toast(SelectDatabaseActivity.this, "Mode Local aktif. Tidak perlu tes koneksi remote.");
                    return;
                }
                if (!SupabaseConfig.isConfigured()) {
                    ViewUtils.toast(SelectDatabaseActivity.this, "Supabase belum dikonfigurasi.");
                    return;
                }
                if (!NetworkUtils.isOnline(SelectDatabaseActivity.this)) {
                    ViewUtils.toast(SelectDatabaseActivity.this, "Tidak ada koneksi internet.");
                    return;
                }
                if (!AuthSessionStore.hasValidSupabaseSession(SelectDatabaseActivity.this)) {
                    ViewUtils.toast(SelectDatabaseActivity.this, "Session Supabase tidak valid. Silakan login Supabase.");
                    return;
                }
                ViewUtils.toast(SelectDatabaseActivity.this, "Tes koneksi remote berhasil.");
            }
        });
        findViewById(R.id.btnSaveDatabase).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean remoteSelected = rg.getCheckedRadioButtonId() == R.id.rbRemote;
                if (remoteSelected) {
                    if (!SupabaseConfig.isConfigured()) {
                        ViewUtils.toast(SelectDatabaseActivity.this, "Supabase belum dikonfigurasi.");
                        return;
                    }
                    if (!AuthSessionStore.hasValidSupabaseSession(SelectDatabaseActivity.this)) {
                        ViewUtils.toast(SelectDatabaseActivity.this, "Mode Remote butuh session Supabase aktif.");
                        return;
                    }
                    AuthSessionStore.setDatabaseMode(SelectDatabaseActivity.this, AuthSessionStore.DATABASE_MODE_REMOTE);
                    AuthSessionStore.setRemoteProviderType(SelectDatabaseActivity.this, AuthSessionStore.PROVIDER_SUPABASE);
                    ViewUtils.toast(SelectDatabaseActivity.this, "Mode database disimpan: Remote");
                    return;
                }

                AuthSessionStore.setDatabaseMode(SelectDatabaseActivity.this, AuthSessionStore.DATABASE_MODE_LOCAL);
                ViewUtils.toast(SelectDatabaseActivity.this, "Mode database disimpan: Local");
            }
        });
    }
}
