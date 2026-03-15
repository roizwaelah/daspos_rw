package com.daspos.feature.settings;

import android.os.Bundle;

import com.daspos.core.app.BaseActivity;

import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.shared.util.ViewUtils;

public class AppInfoActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_info);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.app_info));
    }
}
