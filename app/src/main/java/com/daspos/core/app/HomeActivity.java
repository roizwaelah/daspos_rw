package com.daspos.core.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.daspos.R;
import com.daspos.feature.product.ProductActivity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.feature.auth.MenuAccessStore;
import com.daspos.feature.report.ReportActivity;
import com.daspos.feature.settings.BackupRestoreActivity;
import com.daspos.feature.settings.SettingActivity;
import com.daspos.feature.settings.StoreConfigStore;
import com.daspos.feature.transaction.TransactionActivity;
import com.daspos.repository.ProductRepository;
import com.daspos.repository.TransactionRepository;
import com.daspos.shared.util.CurrencyUtils;
import com.daspos.shared.util.NotificationDialogHelper;
import com.daspos.shared.util.ViewUtils;

import java.util.List;
import java.util.Locale;

public class HomeActivity extends BaseActivity {
    private static final long STATS_REFRESH_INTERVAL_MS = 30_000L;
    private int pendingStatRequests;
    private long lastStatsLoadedAtMs = 0L;
    private boolean statsLoadedOnce = false;
    private boolean statsLoading = false;

    @Override
    protected void onResume() {
        super.onResume();
        bindStoreIdentity();
        bindStats(false);
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

        findViewById(R.id.menuBestSeller).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, BestSellerActivity.class));
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
        bindHomeMenu(R.id.menuBestSeller, R.string.best_seller_title, R.drawable.ic_chart);
        bindHomeMenu(R.id.menuTransaction, R.string.transactions, R.drawable.ic_menu_transaction);
        bindHomeMenu(R.id.menuReport, R.string.reports, R.drawable.ic_menu_report);
        bindHomeMenu(R.id.menuBackup, R.string.backup_restore, R.drawable.ic_upload);
        applyMenuAccess();

        bindStats(true);
    }

    private void bindStoreIdentity() {
        String storeName = StoreConfigStore.getStoreName(this);
        ((TextView) findViewById(R.id.tvStoreName)).setText(storeName);

        ImageView imgStoreLogo = findViewById(R.id.imgStoreLogo);
        TextView tvStoreLogoInitial = findViewById(R.id.tvStoreLogoInitial);
        String logoUri = StoreConfigStore.getLogoUri(this);
        if (logoUri != null && !logoUri.trim().isEmpty()) {
            try {
                imgStoreLogo.setImageURI(Uri.parse(logoUri));
                tvStoreLogoInitial.setVisibility(View.GONE);
                return;
            } catch (Exception ignored) {
                // fallback to default icon
            }
        }
        imgStoreLogo.setImageDrawable(null);
        tvStoreLogoInitial.setText(extractStoreInitial(storeName));
        tvStoreLogoInitial.setVisibility(View.VISIBLE);
    }

    private String extractStoreInitial(String storeName) {
        if (storeName == null) return "D";
        String trimmed = storeName.trim();
        if (trimmed.isEmpty()) return "D";
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private void bindHomeMenu(int menuId, int titleRes, int iconRes) {
        View menu = findViewById(menuId);
        ((TextView) menu.findViewById(R.id.tvTitle)).setText(getString(titleRes));
        ((ImageView) menu.findViewById(R.id.imgMenu)).setImageResource(iconRes);
    }

    private void bindStats(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && statsLoading) return;
        if (!force && statsLoadedOnce && (now - lastStatsLoadedAtMs) < STATS_REFRESH_INTERVAL_MS) return;

        statsLoading = true;
        pendingStatRequests = 4;
        setStatsLoading(true);

        TransactionRepository.getTodayCountAsync(this, todayCount -> {
                    ((TextView) findViewById(R.id.tvStatTransactions)).setText(String.valueOf(todayCount));
                    onStatLoaded();
                }, throwable -> {
                    ((TextView) findViewById(R.id.tvStatTransactions)).setText("0");
                    onStatLoaded();
                });

        TransactionRepository.getTodayIncomeAsync(this, todayIncome -> {
                    ((TextView) findViewById(R.id.tvStatIncome)).setText(CurrencyUtils.formatRupiah(todayIncome));
                    onStatLoaded();
                }, throwable -> {
                    ((TextView) findViewById(R.id.tvStatIncome)).setText(CurrencyUtils.formatRupiah(0));
                    onStatLoaded();
                });

        ProductRepository.getAllAsync(this, products -> {
                    ((TextView) findViewById(R.id.tvStatProducts)).setText(String.valueOf(products.size()));
                    onStatLoaded();
                }, throwable -> {
                    ((TextView) findViewById(R.id.tvStatProducts)).setText("0");
                    onStatLoaded();
                });

        TransactionRepository.getIncomeByPeriodAsync(this, java.util.Calendar.getInstance(), true, monthlyIncome -> {
                    ((TextView) findViewById(R.id.tvStatIncomeMonth)).setText(CurrencyUtils.formatRupiah(monthlyIncome));
                    onStatLoaded();
                }, throwable -> {
                    ((TextView) findViewById(R.id.tvStatIncomeMonth)).setText(CurrencyUtils.formatRupiah(0));
                    onStatLoaded();
                });
    }

    private void onStatLoaded() {
        pendingStatRequests = Math.max(0, pendingStatRequests - 1);
        if (pendingStatRequests == 0) {
            statsLoadedOnce = true;
            statsLoading = false;
            lastStatsLoadedAtMs = System.currentTimeMillis();
            setStatsLoading(false);
        }
    }

    private void setStatsLoading(boolean isLoading) {
        findViewById(R.id.layoutHomeStatsLoading).setVisibility(isLoading ? View.VISIBLE : View.GONE);
        findViewById(R.id.layoutHomeStatsContent).setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    private void applyMenuAccess() {
        String username = AuthSessionStore.getUsername(this);
        String role = AuthSessionStore.getRole(this);
        List<String> allowed = MenuAccessStore.getForUser(this, username, role);

        applyMenuVisibility(R.id.menuTransaction, allowed.contains(MenuAccessStore.MENU_TRANSACTION));
        applyMenuVisibility(R.id.menuProduct, allowed.contains(MenuAccessStore.MENU_PRODUCT));
        applyMenuVisibility(R.id.menuBestSeller, allowed.contains(MenuAccessStore.MENU_BEST_SELLER));
        applyMenuVisibility(R.id.menuReport, allowed.contains(MenuAccessStore.MENU_REPORT));
        applyMenuVisibility(R.id.menuBackup, allowed.contains(MenuAccessStore.MENU_BACKUP));

        if (!allowed.contains(MenuAccessStore.MENU_TRANSACTION)
                && !allowed.contains(MenuAccessStore.MENU_PRODUCT)
                && !allowed.contains(MenuAccessStore.MENU_BEST_SELLER)
                && !allowed.contains(MenuAccessStore.MENU_REPORT)
                && !allowed.contains(MenuAccessStore.MENU_BACKUP)) {
            ViewUtils.toast(this, getString(R.string.no_menu_assigned));
        }
    }

    private void applyMenuVisibility(int menuId, boolean visible) {
        View menu = findViewById(menuId);
        if (menu == null) return;
        menu.setVisibility(visible ? View.VISIBLE : View.GONE);
        menu.setEnabled(visible);
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
