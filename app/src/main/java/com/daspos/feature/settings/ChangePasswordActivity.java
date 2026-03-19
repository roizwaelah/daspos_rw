package com.daspos.feature.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.repository.UserRepository;
import com.daspos.shared.util.LoadingDialogHelper;
import com.daspos.shared.util.ViewUtils;

public class ChangePasswordActivity extends BaseActivity {
    private AlertDialog loadingDialog;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);
        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.change_password));
        final EditText oldPass = findViewById(R.id.etOldPassword);
        final EditText newPass = findViewById(R.id.etNewPassword);
        final EditText confirmPass = findViewById(R.id.etConfirmPassword);
        View btnSave = findViewById(R.id.btnSave);
        View btnCancel = findViewById(R.id.btnCancel);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String oldVal = oldPass.getText().toString().trim();
                String newVal = newPass.getText().toString().trim();
                String confirmVal = confirmPass.getText().toString().trim();
                String username = AuthSessionStore.getUsername(ChangePasswordActivity.this);
                if (oldVal.isEmpty() || newVal.isEmpty() || confirmVal.isEmpty()) { ViewUtils.toast(ChangePasswordActivity.this, getString(R.string.field_required)); return; }
                if (!newVal.equals(confirmVal)) { ViewUtils.toast(ChangePasswordActivity.this, getString(R.string.password_not_match)); return; }
                if (username == null || username.trim().isEmpty()) { ViewUtils.toast(ChangePasswordActivity.this, getString(R.string.user_session_not_found)); return; }
                setLoading(true, btnSave, btnCancel);
                UserRepository.authenticateAsync(ChangePasswordActivity.this, username, oldVal, authenticated -> {
                    if (!authenticated) {
                        setLoading(false, btnSave, btnCancel);
                        ViewUtils.toast(ChangePasswordActivity.this, getString(R.string.old_password_invalid));
                        return;
                    }
                    UserRepository.updatePasswordAsync(ChangePasswordActivity.this, username, newVal, () -> {
                        setLoading(false, btnSave, btnCancel);
                        ViewUtils.toast(ChangePasswordActivity.this, getString(R.string.password_updated));
                        finish();
                    }, throwable -> {
                        setLoading(false, btnSave, btnCancel);
                        ViewUtils.toast(ChangePasswordActivity.this, getString(R.string.password_update_failed));
                    });
                }, throwable -> {
                    setLoading(false, btnSave, btnCancel);
                    ViewUtils.toast(ChangePasswordActivity.this, getString(R.string.password_update_failed));
                });
            }
        });
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    private void setLoading(boolean isLoading, View btnSave, View btnCancel) {
        btnSave.setEnabled(!isLoading);
        btnCancel.setEnabled(!isLoading);
        if (isLoading) {
            loadingDialog = LoadingDialogHelper.show(this, getString(R.string.loading));
        } else {
            LoadingDialogHelper.dismiss(loadingDialog);
            loadingDialog = null;
        }
    }
}
