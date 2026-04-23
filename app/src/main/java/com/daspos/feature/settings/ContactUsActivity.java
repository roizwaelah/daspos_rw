package com.daspos.feature.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.shared.util.ViewUtils;

public class ContactUsActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthSessionStore.isAdmin(this)) {
            ViewUtils.toast(this, getString(R.string.admin_only_feature));
            finish();
            return;
        }
        setContentView(R.layout.activity_contact_us);
        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.contact_us));
        findViewById(R.id.btnWhatsapp).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String number = "628112630731";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + number));
                try { startActivity(intent); } catch (Exception e) { ViewUtils.toast(ContactUsActivity.this, "WhatsApp/browser tidak tersedia"); }
            }
        });
    }
}
