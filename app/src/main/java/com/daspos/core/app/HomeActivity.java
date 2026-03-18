package com.daspos.core.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.feature.product.ProductActivity;
import com.daspos.model.BestSellerItem;
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
    private static final int BEST_SELLER_PAGE_SIZE = 10;

    private final Calendar bestSellerCalendar = Calendar.getInstance();
    private boolean bestSellerMonthly = false;
    private int bestSellerPage = 0;
    private java.util.List<BestSellerItem> bestSellerItems = new java.util.ArrayList<>();

    private BestSellerAdapter bestSellerAdapter;
    private TextView tvBestSellerPeriod;
    private TextView tvBestSellerEmpty;
    private TextView tvBestSellerPage;
    private View btnBestSellerDaily;
    private View btnBestSellerMonthly;
    private View btnBestSellerPrev;
    private View btnBestSellerNext;

    @Override
    protected void onResume() {
        super.onResume();
        bindStoreIdentity();
        bindStats();
        loadBestSellerItems();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        ImageView btnSettings = findViewById(R.id.btnSettings);

        setupBestSellerSection();
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
        loadBestSellerItems();
    }

    private void setupBestSellerSection() {
        RecyclerView rvBestSeller = findViewById(R.id.rvBestSeller);
        rvBestSeller.setLayoutManager(new LinearLayoutManager(this));
        rvBestSeller.setNestedScrollingEnabled(false);
        bestSellerAdapter = new BestSellerAdapter();
        rvBestSeller.setAdapter(bestSellerAdapter);

        tvBestSellerPeriod = findViewById(R.id.tvBestSellerPeriod);
        tvBestSellerEmpty = findViewById(R.id.tvBestSellerEmpty);
        tvBestSellerPage = findViewById(R.id.tvBestSellerPage);
        btnBestSellerDaily = findViewById(R.id.btnBestSellerDaily);
        btnBestSellerMonthly = findViewById(R.id.btnBestSellerMonthly);
        btnBestSellerPrev = findViewById(R.id.btnBestSellerPrev);
        btnBestSellerNext = findViewById(R.id.btnBestSellerNext);

        btnBestSellerDaily.setOnClickListener(v -> switchBestSellerPeriod(false));
        btnBestSellerMonthly.setOnClickListener(v -> switchBestSellerPeriod(true));
        btnBestSellerPrev.setOnClickListener(v -> {
            if (bestSellerPage > 0) {
                bestSellerPage--;
                renderBestSellerItems();
            }
        });
        btnBestSellerNext.setOnClickListener(v -> {
            int totalPages = getBestSellerTotalPages();
            if (bestSellerPage < totalPages - 1) {
                bestSellerPage++;
                renderBestSellerItems();
            }
        });

        updateBestSellerToggleState();
    }

    private void switchBestSellerPeriod(boolean monthly) {
        if (bestSellerMonthly == monthly) return;
        bestSellerMonthly = monthly;
        bestSellerPage = 0;
        updateBestSellerToggleState();
        loadBestSellerItems();
    }

    private void loadBestSellerItems() {
        TransactionRepository.getBestSellerItemsAsync(this, bestSellerCalendar, bestSellerMonthly, items -> {
            bestSellerItems = items;
            bestSellerPage = 0;
            renderBestSellerItems();
        }, throwable -> {
            bestSellerItems = new java.util.ArrayList<>();
            bestSellerPage = 0;
            renderBestSellerItems();
        });
    }

    private void renderBestSellerItems() {
        if (tvBestSellerPeriod == null) return;
        tvBestSellerPeriod.setText(getString(bestSellerMonthly ? R.string.best_seller_period_monthly : R.string.best_seller_period_daily));

        int totalPages = getBestSellerTotalPages();
        if (bestSellerPage >= totalPages) bestSellerPage = Math.max(0, totalPages - 1);

        int fromIndex = bestSellerPage * BEST_SELLER_PAGE_SIZE;
        int toIndex = Math.min(fromIndex + BEST_SELLER_PAGE_SIZE, bestSellerItems.size());
        java.util.List<BestSellerItem> pageItems = fromIndex < toIndex
                ? new java.util.ArrayList<>(bestSellerItems.subList(fromIndex, toIndex))
                : new java.util.ArrayList<>();

        bestSellerAdapter.submit(pageItems);

        boolean isEmpty = bestSellerItems.isEmpty();
        tvBestSellerEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        findViewById(R.id.rvBestSeller).setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        tvBestSellerPage.setText(getString(R.string.best_seller_page_format, isEmpty ? 0 : (bestSellerPage + 1), totalPages));
        btnBestSellerPrev.setEnabled(bestSellerPage > 0);
        btnBestSellerNext.setEnabled(bestSellerPage < totalPages - 1);
        btnBestSellerPrev.setAlpha(btnBestSellerPrev.isEnabled() ? 1f : 0.4f);
        btnBestSellerNext.setAlpha(btnBestSellerNext.isEnabled() ? 1f : 0.4f);
    }

    private int getBestSellerTotalPages() {
        return Math.max(1, (int) Math.ceil(bestSellerItems.size() / (double) BEST_SELLER_PAGE_SIZE));
    }

    private void updateBestSellerToggleState() {
        btnBestSellerDaily.setSelected(!bestSellerMonthly);
        btnBestSellerMonthly.setSelected(bestSellerMonthly);
        btnBestSellerDaily.setBackgroundResource(bestSellerMonthly ? R.drawable.bg_best_seller_tab_inactive : R.drawable.bg_best_seller_tab_active);
        btnBestSellerMonthly.setBackgroundResource(bestSellerMonthly ? R.drawable.bg_best_seller_tab_active : R.drawable.bg_best_seller_tab_inactive);
        ((TextView) btnBestSellerDaily).setTextColor(ContextCompat.getColor(this, bestSellerMonthly ? R.color.text_primary : android.R.color.white));
        ((TextView) btnBestSellerMonthly).setTextColor(ContextCompat.getColor(this, bestSellerMonthly ? android.R.color.white : R.color.text_primary));
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
