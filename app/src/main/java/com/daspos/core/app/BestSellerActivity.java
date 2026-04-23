package com.daspos.core.app;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.feature.auth.MenuAccessGuard;
import com.daspos.feature.auth.MenuAccessStore;
import com.daspos.model.BestSellerItem;
import com.daspos.repository.TransactionRepository;
import com.daspos.shared.util.ViewUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class BestSellerActivity extends BaseActivity {
    private static final int BEST_SELLER_PAGE_SIZE = 10;

    private final Calendar bestSellerCalendar = Calendar.getInstance();
    private boolean bestSellerMonthly = false;
    private int bestSellerPage = 0;
    private List<BestSellerItem> bestSellerItems = new ArrayList<>();

    private BestSellerAdapter bestSellerAdapter;
    private TextView tvBestSellerPeriod;
    private View layoutBestSellerLoading;
    private ProgressBar progressBestSeller;
    private TextView tvBestSellerEmpty;
    private TextView tvBestSellerPage;
    private View btnBestSellerDaily;
    private View btnBestSellerMonthly;
    private View btnBestSellerPrev;
    private View btnBestSellerNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!MenuAccessGuard.ensureAccess(this, MenuAccessStore.MENU_BEST_SELLER)) return;
        setContentView(R.layout.activity_best_seller);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.best_seller_title));

        setupBestSellerSection();
        loadBestSellerItems();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBestSellerItems();
    }

    private void setupBestSellerSection() {
        RecyclerView rvBestSeller = findViewById(R.id.rvBestSeller);
        rvBestSeller.setLayoutManager(new LinearLayoutManager(this));
        rvBestSeller.setNestedScrollingEnabled(false);
        bestSellerAdapter = new BestSellerAdapter();
        rvBestSeller.setAdapter(bestSellerAdapter);

        tvBestSellerPeriod = findViewById(R.id.tvBestSellerPeriod);
        layoutBestSellerLoading = findViewById(R.id.layoutBestSellerLoading);
        progressBestSeller = findViewById(R.id.progressBestSeller);
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
        setBestSellerLoading(true);
        TransactionRepository.getBestSellerItemsAsync(this, bestSellerCalendar, bestSellerMonthly, items -> {
            bestSellerItems = items;
            bestSellerPage = 0;
            setBestSellerLoading(false);
            renderBestSellerItems();
        }, throwable -> {
            bestSellerItems = new ArrayList<>();
            bestSellerPage = 0;
            setBestSellerLoading(false);
            renderBestSellerItems();
        });
    }

    private void setBestSellerLoading(boolean isLoading) {
        if (layoutBestSellerLoading == null || progressBestSeller == null) return;
        layoutBestSellerLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        progressBestSeller.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        findViewById(R.id.rvBestSeller).setVisibility(isLoading ? View.GONE : View.VISIBLE);
        tvBestSellerEmpty.setVisibility(View.GONE);
    }

    private void renderBestSellerItems() {
        if (tvBestSellerPeriod == null) return;
        tvBestSellerPeriod.setText(getString(bestSellerMonthly ? R.string.best_seller_period_monthly : R.string.best_seller_period_daily));

        int totalPages = getBestSellerTotalPages();
        if (bestSellerPage >= totalPages) bestSellerPage = Math.max(0, totalPages - 1);

        int fromIndex = bestSellerPage * BEST_SELLER_PAGE_SIZE;
        int toIndex = Math.min(fromIndex + BEST_SELLER_PAGE_SIZE, bestSellerItems.size());
        List<BestSellerItem> pageItems = fromIndex < toIndex
                ? new ArrayList<>(bestSellerItems.subList(fromIndex, toIndex))
                : new ArrayList<>();

        bestSellerAdapter.submit(pageItems, fromIndex);

        boolean isEmpty = bestSellerItems.isEmpty();
        layoutBestSellerLoading.setVisibility(View.GONE);
        progressBestSeller.setVisibility(View.GONE);
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
}
