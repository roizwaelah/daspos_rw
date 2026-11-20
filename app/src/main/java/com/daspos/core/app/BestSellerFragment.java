package com.daspos.core.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
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

public class BestSellerFragment extends Fragment {
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
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!MenuAccessGuard.ensureFragmentAccess(this, MenuAccessStore.MENU_BEST_SELLER)) return;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_best_seller, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        Toolbar toolbar = root.findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.best_seller_title));

        setupBestSellerSection();
        // onResume (which always follows onCreate) performs the initial load;
        // calling it here too fired the same query twice on first display.
    }

    @Override
    public void onResume() {
        super.onResume();
        loadBestSellerItems();
    }

    @Override
    public void onDestroyView() {
        bestSellerAdapter = null;
        tvBestSellerPeriod = null;
        layoutBestSellerLoading = null;
        progressBestSeller = null;
        tvBestSellerEmpty = null;
        tvBestSellerPage = null;
        btnBestSellerDaily = null;
        btnBestSellerMonthly = null;
        btnBestSellerPrev = null;
        btnBestSellerNext = null;
        super.onDestroyView();
    }

    private void setupBestSellerSection() {
        RecyclerView rvBestSeller = findViewById(R.id.rvBestSeller);
        rvBestSeller.setLayoutManager(new LinearLayoutManager(requireContext()));
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
        if (tvBestSellerPeriod == null) return;
        setBestSellerLoading(true);
        TransactionRepository.getBestSellerItemsOptimizedAsync(requireContext(), bestSellerCalendar, bestSellerMonthly, items -> {
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

    @SuppressWarnings("unchecked")
    private <T extends View> T findViewById(int id) {
        View v = getView();
        return v == null ? null : (T) v.findViewById(id);
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
        ((TextView) btnBestSellerDaily).setTextColor(ContextCompat.getColor(requireContext(), bestSellerMonthly ? R.color.text_primary : android.R.color.white));
        ((TextView) btnBestSellerMonthly).setTextColor(ContextCompat.getColor(requireContext(), bestSellerMonthly ? android.R.color.white : R.color.text_primary));
    }
}
