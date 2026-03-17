package com.daspos.core.app;

import android.content.Intent;
import android.net.Uri;
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
import com.daspos.shared.util.NotificationDialogHelper;

import java.util.Calendar;

public class HomeActivity extends BaseActivity {
    @Override
    protected void onResume() {
        super.onResume();
        bindStoreIdentity();
        bindStats();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        ImageView btnSettings = findViewById(R.id.btnSettings);

        bindStoreIdentity();

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
        bindHomeMenu(R.id.menuBackup, R.string.backup_restore, R.drawable.ic_upload);

        bindStats();
    }


    private void bindStoreIdentity() {
        ((TextView) findViewById(R.id.tvStoreName)).setText(StoreConfigStore.getStoreName(this));

        ImageView imgStoreLogo = findViewById(R.id.imgStoreLogo);
        String logoUri = StoreConfigStore.getLogoUri(this);
        if (logoUri != null && !logoUri.trim().isEmpty()) {
            try {
                imgStoreLogo.setImageURI(Uri.parse(logoUri));
                return;
            } catch (Exception ignored) {
                // fallback to default icon
            }
        }
        imgStoreLogo.setImageResource(R.drawable.ic_store);
    }

    private void bindHomeMenu(int menuId, int titleRes, int iconRes) {
        View menu = findViewById(menuId);
        ((TextView) menu.findViewById(R.id.tvTitle)).setText(getString(titleRes));
        ((ImageView) menu.findViewById(R.id.imgMenu)).setImageResource(iconRes);
    }

    private void bindStats() {
        TransactionRepository.getTodayCountAsync(this, todayCount -> ((TextView) findViewById(R.id.tvStatTransactions))
                .setText(String.valueOf(todayCount)), throwable -> ((TextView) findViewById(R.id.tvStatTransactions))
                .setText("0"));

        TransactionRepository.getTodayIncomeAsync(this, todayIncome -> ((TextView) findViewById(R.id.tvStatIncome))
                .setText(CurrencyUtils.formatRupiah(todayIncome)), throwable -> ((TextView) findViewById(R.id.tvStatIncome))
                .setText(CurrencyUtils.formatRupiah(0)));

        ProductRepository.getAllAsync(this, products -> ((TextView) findViewById(R.id.tvStatProducts))
                .setText(String.valueOf(products.size())), throwable -> ((TextView) findViewById(R.id.tvStatProducts))
                .setText("0"));

        TransactionRepository.getIncomeByPeriodAsync(this, Calendar.getInstance(), true, monthlyIncome -> ((TextView) findViewById(R.id.tvStatIncomeMonth))
                .setText(CurrencyUtils.formatRupiah(monthlyIncome)), throwable -> ((TextView) findViewById(R.id.tvStatIncomeMonth))
                .setText(CurrencyUtils.formatRupiah(0)));
    }

    @Override
    public void onBackPressed() {
        NotificationDialogHelper.showWarningConfirmation(
                this,
                R.string.exit_confirmation_title,
                R.string.exit_confirmation_message,
                R.string.exit,
                this::finishAffinity
        );
    }
}
