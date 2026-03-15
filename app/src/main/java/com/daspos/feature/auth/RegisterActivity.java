package com.daspos.feature.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.shared.util.ViewUtils;
import com.google.android.material.button.MaterialButton;

public class RegisterActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        MaterialButton btnRegister = findViewById(R.id.btnRegister);
        TextView tvLogin = findViewById(R.id.tvLogin);

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ViewUtils.toast(RegisterActivity.this, "Akun berhasil dibuat");
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                finish();
            }
        });
        tvLogin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }
}
