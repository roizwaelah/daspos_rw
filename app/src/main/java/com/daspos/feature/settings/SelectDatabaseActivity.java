package com.daspos.feature.settings;

import android.content.Intent;
import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.feature.auth.LoginActivity;
import com.daspos.remote.RemoteProviderConfigStore;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.NetworkUtils;
import com.daspos.shared.util.ViewUtils;
import com.daspos.supabase.SupabaseConfig;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SelectDatabaseActivity extends BaseActivity {
    public static final String EXTRA_SETUP_FLOW = "extra_setup_flow";

    private final List<ProviderItem> providers = new ArrayList<>();

    private RadioGroup rgDatabase;
    private LinearLayout layoutRemote;
    private LinearLayout layoutSupabaseDetails;
    private LinearLayout layoutGenericRemoteConfig;
    private Spinner spRemoteProvider;
    private TextView tvSetupStep;
    private TextView tvSetupHint;
    private TextView tvSupabaseStatus;
    private TextView tvRemoteNotes;
    private EditText etSupabaseUrl;
    private EditText etSupabaseAnonKey;
    private EditText etRemoteBaseUrl;
    private EditText etRemoteApiKey;
    private EditText etRemoteEdgeFunctionUrl;
    private EditText etRemoteEdgeSecret;
    private boolean setupFlow;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupFlow = getIntent().getBooleanExtra(EXTRA_SETUP_FLOW, false) || !AuthSessionStore.hasSession(this);
        if (!setupFlow && !AuthSessionStore.isAdmin(this)) {
            ViewUtils.toast(this, getString(R.string.admin_only_feature));
            finish();
            return;
        }
        setContentView(R.layout.activity_select_database);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.select_database));

        rgDatabase = findViewById(R.id.rgDatabase);
        layoutRemote = findViewById(R.id.layoutRemote);
        layoutSupabaseDetails = findViewById(R.id.layoutSupabaseDetails);
        layoutGenericRemoteConfig = findViewById(R.id.layoutGenericRemoteConfig);
        spRemoteProvider = findViewById(R.id.spRemoteProvider);
        tvSetupStep = findViewById(R.id.tvSetupStep);
        tvSetupHint = findViewById(R.id.tvSetupHint);
        tvSupabaseStatus = findViewById(R.id.tvSupabaseStatus);
        tvRemoteNotes = findViewById(R.id.tvRemoteNotes);
        etSupabaseUrl = findViewById(R.id.etSupabaseUrl);
        etSupabaseAnonKey = findViewById(R.id.etSupabaseAnonKey);
        etRemoteBaseUrl = findViewById(R.id.etRemoteBaseUrl);
        etRemoteApiKey = findViewById(R.id.etRemoteApiKey);
        etRemoteEdgeFunctionUrl = findViewById(R.id.etRemoteEdgeFunctionUrl);
        etRemoteEdgeSecret = findViewById(R.id.etRemoteEdgeSecret);
        tvSetupStep.setVisibility(setupFlow ? View.VISIBLE : View.GONE);
        tvSetupHint.setVisibility(setupFlow ? View.VISIBLE : View.GONE);

        setupProviderSpinner();
        bindRemoteConfigInputs();

        bindSupabaseConfigInputs();

        String activeMode = AuthSessionStore.getDatabaseMode(this);
        if (AuthSessionStore.DATABASE_MODE_REMOTE.equalsIgnoreCase(activeMode)) {
            rgDatabase.check(R.id.rbRemote);
            layoutRemote.setVisibility(View.VISIBLE);
        } else {
            rgDatabase.check(R.id.rbLocal);
            layoutRemote.setVisibility(View.GONE);
        }
        String currentProvider = AuthSessionStore.getRemoteProviderType(this);
        spRemoteProvider.setSelection(findProviderIndex(currentProvider), false);
        refreshProviderSection(getSelectedProviderType());

        rgDatabase.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                layoutRemote.setVisibility(checkedId == R.id.rbRemote ? View.VISIBLE : View.GONE);
            }
        });

        findViewById(R.id.btnTestConnection).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (rgDatabase.getCheckedRadioButtonId() != R.id.rbRemote) {
                    ViewUtils.toast(SelectDatabaseActivity.this, "Mode Local aktif. Tidak perlu tes koneksi remote.");
                    return;
                }
                String providerType = getSelectedProviderType();
                if (AuthSessionStore.PROVIDER_SUPABASE.equals(providerType)) {
                    persistSupabaseConfigInputs();
                    if (!SupabaseConfig.isConfigured()) {
                        ViewUtils.toast(SelectDatabaseActivity.this, "Supabase belum dikonfigurasi.");
                        return;
                    }
                    if (!NetworkUtils.isOnline(SelectDatabaseActivity.this)) {
                        ViewUtils.toast(SelectDatabaseActivity.this, "Tidak ada koneksi internet.");
                        return;
                    }
                    if (!setupFlow && !AuthSessionStore.hasValidSupabaseSession(SelectDatabaseActivity.this)) {
                        ViewUtils.toast(SelectDatabaseActivity.this, "Session Supabase tidak valid. Silakan login Supabase.");
                        return;
                    }
                    ViewUtils.toast(SelectDatabaseActivity.this, "Tes koneksi remote berhasil.");
                    return;
                }

                persistRemoteConfigInputs();
                RemoteProviderConfigStore.RemoteConfig cfg = RemoteProviderConfigStore.get(SelectDatabaseActivity.this);
                if (cfg.edgeFunctionUrl.isEmpty()) {
                    ViewUtils.toast(SelectDatabaseActivity.this, "URL Edge Function/Bridge wajib diisi.");
                    return;
                }
                testGenericProvider(providerType, cfg);
            }
        });
        findViewById(R.id.btnSaveDatabase).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean remoteSelected = rgDatabase.getCheckedRadioButtonId() == R.id.rbRemote;
                if (remoteSelected) {
                    String providerType = getSelectedProviderType();
                    if (AuthSessionStore.PROVIDER_SUPABASE.equals(providerType)) {
                        persistSupabaseConfigInputs();
                        if (!SupabaseConfig.isConfigured()) {
                            ViewUtils.toast(SelectDatabaseActivity.this, "Supabase belum dikonfigurasi.");
                            return;
                        }
                        if (!setupFlow && !AuthSessionStore.hasValidSupabaseSession(SelectDatabaseActivity.this)) {
                            ViewUtils.toast(SelectDatabaseActivity.this, "Mode Remote Supabase butuh session login aktif.");
                            return;
                        }
                    } else {
                        persistRemoteConfigInputs();
                        RemoteProviderConfigStore.RemoteConfig cfg = RemoteProviderConfigStore.get(SelectDatabaseActivity.this);
                        if (cfg.edgeFunctionUrl.isEmpty()) {
                            ViewUtils.toast(SelectDatabaseActivity.this, "URL Edge Function/Bridge wajib diisi.");
                            return;
                        }
                    }

                    AuthSessionStore.setDatabaseMode(SelectDatabaseActivity.this, AuthSessionStore.DATABASE_MODE_REMOTE);
                    AuthSessionStore.setRemoteProviderType(SelectDatabaseActivity.this, providerType);
                    AuthSessionStore.setDatabaseSetupCompleted(SelectDatabaseActivity.this, true);
                    ViewUtils.toast(
                            SelectDatabaseActivity.this,
                            "Mode database disimpan: Remote (" + getProviderLabel(providerType) + ")"
                    );
                    routeAfterSavedForSetupFlow();
                    return;
                }

                AuthSessionStore.setDatabaseMode(SelectDatabaseActivity.this, AuthSessionStore.DATABASE_MODE_LOCAL);
                AuthSessionStore.setDatabaseSetupCompleted(SelectDatabaseActivity.this, true);
                ViewUtils.toast(SelectDatabaseActivity.this, "Mode database disimpan: Local");
                routeAfterSavedForSetupFlow();
            }
        });
    }

    private void routeAfterSavedForSetupFlow() {
        if (!setupFlow) return;
        if (AuthSessionStore.hasSession(this)) return;
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_SETUP_FLOW, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupProviderSpinner() {
        providers.clear();
        providers.add(new ProviderItem(AuthSessionStore.PROVIDER_SUPABASE, getString(R.string.provider_supabase)));
        providers.add(new ProviderItem(AuthSessionStore.PROVIDER_FIREBASE, getString(R.string.provider_firebase)));
        providers.add(new ProviderItem(AuthSessionStore.PROVIDER_APPWRITE, getString(R.string.provider_appwrite)));
        providers.add(new ProviderItem(AuthSessionStore.PROVIDER_POCKETBASE, getString(R.string.provider_pocketbase)));
        providers.add(new ProviderItem(AuthSessionStore.PROVIDER_CUSTOM_API, getString(R.string.provider_custom_api)));

        List<String> labels = new ArrayList<>();
        for (ProviderItem item : providers) {
            labels.add(item.label);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                labels
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRemoteProvider.setAdapter(adapter);
        spRemoteProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshProviderSection(getSelectedProviderType());
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {
                refreshProviderSection(AuthSessionStore.PROVIDER_SUPABASE);
            }
        });
    }

    private void bindRemoteConfigInputs() {
        RemoteProviderConfigStore.RemoteConfig config = RemoteProviderConfigStore.get(this);
        etRemoteBaseUrl.setText(config.baseUrl);
        etRemoteApiKey.setText(config.apiKey);
        etRemoteEdgeFunctionUrl.setText(config.edgeFunctionUrl);
        etRemoteEdgeSecret.setText(config.edgeFunctionSecret);
    }

    private void bindSupabaseConfigInputs() {
        etSupabaseUrl.setText(SupabaseConfig.getUrl());
        etSupabaseAnonKey.setText(SupabaseConfig.getAnonKey());
        refreshSupabaseStatus();
    }

    private void persistSupabaseConfigInputs() {
        boolean saved = SupabaseConfig.save(
                this,
                safe(etSupabaseUrl.getText()),
                safe(etSupabaseAnonKey.getText())
        );
        if (!saved) {
            ViewUtils.toast(this, "URL Supabase harus menggunakan https:// (kecuali localhost)");
        }
        refreshSupabaseStatus();
    }

    private void persistRemoteConfigInputs() {
        boolean saved = RemoteProviderConfigStore.save(
                this,
                safe(etRemoteBaseUrl.getText()),
                safe(etRemoteApiKey.getText()),
                safe(etRemoteEdgeFunctionUrl.getText()),
                safe(etRemoteEdgeSecret.getText())
        );
        if (!saved) {
            ViewUtils.toast(this, "URL remote harus menggunakan https:// (kecuali localhost)");
        }
    }

    private void refreshProviderSection(String providerType) {
        boolean supabase = AuthSessionStore.PROVIDER_SUPABASE.equalsIgnoreCase(providerType);
        layoutSupabaseDetails.setVisibility(supabase ? View.VISIBLE : View.GONE);
        layoutGenericRemoteConfig.setVisibility(supabase ? View.GONE : View.VISIBLE);
        if (supabase) refreshSupabaseStatus();
        tvRemoteNotes.setText(supabase
                ? getString(R.string.remote_notes_supabase)
                : getString(R.string.remote_notes_generic));
    }

    private void refreshSupabaseStatus() {
        tvSupabaseStatus.setText(SupabaseConfig.isConfigured() ? "Terkonfigurasi" : "Belum dikonfigurasi");
    }

    private int findProviderIndex(String providerType) {
        for (int i = 0; i < providers.size(); i++) {
            if (providers.get(i).type.equalsIgnoreCase(providerType)) return i;
        }
        return 0;
    }

    private String getSelectedProviderType() {
        int index = spRemoteProvider.getSelectedItemPosition();
        if (index < 0 || index >= providers.size()) {
            return AuthSessionStore.PROVIDER_SUPABASE;
        }
        return providers.get(index).type;
    }

    private String getProviderLabel(String providerType) {
        for (ProviderItem item : providers) {
            if (item.type.equalsIgnoreCase(providerType)) return item.label;
        }
        return providerType;
    }

    private String safe(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private void testGenericProvider(String providerType, RemoteProviderConfigStore.RemoteConfig cfg) {
        DbExecutor.runAsync(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(cfg.edgeFunctionUrl).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                if (!cfg.apiKey.isEmpty()) {
                    connection.setRequestProperty("x-api-key", cfg.apiKey);
                }
                if (!cfg.edgeFunctionSecret.isEmpty()) {
                    connection.setRequestProperty("x-edge-secret", cfg.edgeFunctionSecret);
                }

                JSONObject body = new JSONObject();
                body.put("provider", providerType);
                body.put("action", "health.check");
                body.put("base_url", cfg.baseUrl);
                body.put("payload", new JSONObject());

                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(payload);
                }

                int status = connection.getResponseCode();
                String response = readResponse(connection);
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("HTTP " + status + " " + response);
                }

                if (!response.isEmpty()) {
                    JSONObject json = new JSONObject(response);
                    if (json.has("success") && !json.optBoolean("success", true)) {
                        String msg = json.optString("message", json.optString("error", "Tes koneksi gagal"));
                        throw new IllegalStateException(msg);
                    }
                }
                return null;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, ignored -> ViewUtils.toast(
                        SelectDatabaseActivity.this,
                        "Tes koneksi remote berhasil (" + getProviderLabel(providerType) + ")"
                ),
                throwable -> ViewUtils.toast(
                        SelectDatabaseActivity.this,
                        "Tes koneksi gagal: " + safe(throwable.getMessage())
                ));
    }

    private String readResponse(HttpURLConnection connection) {
        try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(
                        connection.getErrorStream() != null ? connection.getErrorStream() : connection.getInputStream(),
                        StandardCharsets.UTF_8
                )
        )) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static final class ProviderItem {
        private final String type;
        private final String label;

        private ProviderItem(String type, String label) {
            this.type = type == null ? "" : type;
            this.label = label == null ? "" : label;
        }
    }
}
