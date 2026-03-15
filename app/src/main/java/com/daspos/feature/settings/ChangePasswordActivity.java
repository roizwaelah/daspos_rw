package com.daspos.feature.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.shared.util.ViewUtils;

public class ChangePasswordActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);
        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.change_password));
        final EditText oldPass = findViewById(R.id.etOldPassword);
        final EditText newPass = findViewById(R.id.etNewPassword);
        final EditText confirmPass = findViewById(R.id.etConfirmPassword);
        findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String oldVal = oldPass.getText().toString().trim();
                String newVal = newPass.getText().toString().trim();
                String confirmVal = confirmPass.getText().toString().trim();
                if (oldVal.isEmpty() || newVal.isEmpty() || confirmVal.isEmpty()) { ViewUtils.toast(ChangePasswordActivity.this, getString(R.string.field_required)); return; }
                if (!newVal.equals(confirmVal)) { ViewUtils.toast(ChangePasswordActivity.this, getString(R.string.password_not_match)); return; }
                ViewUtils.toast(ChangePasswordActivity.this, "Password berhasil diperbarui");
                finish();
            }
        });
        findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }
}
