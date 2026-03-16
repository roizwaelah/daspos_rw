package com.daspos.feature.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;

import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.shared.util.ViewUtils;

public class SelectDatabaseActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_database);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.select_database));

        final RadioGroup rg = findViewById(R.id.rgDatabase);
        final LinearLayout layoutRemote = findViewById(R.id.layoutRemote);
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                View radio = findViewById(checkedId);
                int index = group.indexOfChild(radio);
                layoutRemote.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
            }
        });
        findViewById(R.id.btnTestConnection).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ViewUtils.toast(SelectDatabaseActivity.this, "Tes koneksi berhasil (simulasi)"); }
        });
        findViewById(R.id.btnSaveDatabase).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ViewUtils.toast(SelectDatabaseActivity.this, getString(R.string.saved_success)); }
        });
    }
}
