package com.daspos.core.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.daspos.R;
import com.daspos.feature.product.ProductActivity;
import com.daspos.feature.report.ReportActivity;
import com.daspos.feature.settings.BackupRestoreActivity;
import com.daspos.feature.settings.SettingActivity;
import com.daspos.feature.settings.StoreConfigStore;
import com.daspos.feature.transaction.TransactionActivity;
import com.daspos.repository.ProductRepository;
import com.daspos.repository.TransactionRepository;
import com.daspos.shared.util.CurrencyUtils;

public class HomeActivity extends BaseActivity {
    @Override
    protected void onResume() {
        super.onResume();
        ((TextView) findViewById(R.id.tvStoreName)).setText(StoreConfigStore.getStoreName(this));
        bindStats();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        TextView tvStoreName = findViewById(R.id.tvStoreName);
        ImageView btnSettings = findViewById(R.id.btnSettings);

        tvStoreName.setText(StoreConfigStore.getStoreName(this));

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, SettingActivity.class));
            }
        });

        findViewById(R.id.menuProduct).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, ProductActivity.class));
            }
        });

        findViewById(R.id.menuTransaction).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, TransactionActivity.class));
            }
        });

        findViewById(R.id.menuReport).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, ReportActivity.class));
            }
        });

        findViewById(R.id.menuBackup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, BackupRestoreActivity.class));
            }
        });

        bindHomeMenu(R.id.menuProduct, R.string.manage_products, R.drawable.ic_menu_product);
        bindHomeMenu(R.id.menuTransaction, R.string.transactions, R.drawable.ic_menu_transaction);
        bindHomeMenu(R.id.menuReport, R.string.reports, R.drawable.ic_menu_report);
        bindHomeMenu(R.id.menuBackup, R.string.backup_restore, R.drawable.ic_backup);

        bindStats();
    }

    private void bindHomeMenu(int menuId, int titleRes, int iconRes) {
        View menu = findViewById(menuId);
        ((TextView) menu.findViewById(R.id.tvTitle)).setText(getString(titleRes));
        ((ImageView) menu.findViewById(R.id.imgMenu)).setImageResource(iconRes);
    }

    private void bindStats() {
        ((TextView) findViewById(R.id.tvStatTransactions))
                .setText(String.valueOf(TransactionRepository.getTodayCount(this)));
        ((TextView) findViewById(R.id.tvStatIncome))
                .setText(CurrencyUtils.formatRupiah(TransactionRepository.getTodayIncome(this)));
        ((TextView) findViewById(R.id.tvStatProducts))
                .setText(String.valueOf(ProductRepository.getAll(this).size()));
    }
}
