package com.daspos.core.app;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.daspos.R;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.feature.auth.MenuAccessStore;
import com.daspos.feature.product.ProductFragment;
import com.daspos.feature.report.ReportFragment;
import com.daspos.feature.settings.BackupRestoreFragment;
import com.daspos.feature.settings.SettingFragment;
import com.daspos.feature.settings.StoreConfigStore;
import com.daspos.feature.transaction.TransactionFragment;
import com.daspos.repository.ProductRepository;
import com.daspos.repository.TransactionRepository;
import com.daspos.shared.util.CurrencyUtils;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.ImageDecodeHelper;
import com.daspos.shared.util.ViewUtils;

import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {
    private static final long STATS_REFRESH_INTERVAL_MS = 30_000L;
    private static final int LOGO_MAX_DIMENSION = 512;
    private View rootView;
    private String boundLogoUri;
    private Bitmap boundLogoBitmap;
    private int logoLoadGeneration = 0;
    private int pendingStatRequests;
    private long lastStatsLoadedAtMs = 0L;
    private boolean statsLoadedOnce = false;
    private boolean statsLoading = false;

    @Override
    public void onResume() {
        super.onResume();
        bindStoreIdentity();
        applyMenuAccess();
        bindStats(false);
    }

    @Override
    public void onDestroyView() {
        logoLoadGeneration++;
        ImageView imgStoreLogo = findViewById(R.id.imgStoreLogo);
        if (imgStoreLogo != null) {
            imgStoreLogo.setImageDrawable(null);
        }
        if (boundLogoBitmap != null && !boundLogoBitmap.isRecycled()) {
            boundLogoBitmap.recycle();
        }
        boundLogoBitmap = null;
        boundLogoUri = null;
        rootView = null;
        super.onDestroyView();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        rootView = root;

        ImageView btnSettings = root.findViewById(R.id.btnSettings);

        bindStoreIdentity();

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) requireActivity()).navigateTo(new SettingFragment());
            }
        });

        root.findViewById(R.id.menuProduct).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) requireActivity()).navigateTo(new ProductFragment());
            }
        });

        root.findViewById(R.id.menuBestSeller).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) requireActivity()).navigateTo(new BestSellerFragment());
            }
        });

        root.findViewById(R.id.menuTransaction).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) requireActivity()).navigateTo(new TransactionFragment());
            }
        });

        root.findViewById(R.id.menuReport).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) requireActivity()).navigateTo(new ReportFragment());
            }
        });

        root.findViewById(R.id.menuBackup).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) requireActivity()).navigateTo(new BackupRestoreFragment());
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

    @SuppressWarnings("unchecked")
    private <T extends View> T findViewById(int id) {
        return rootView == null ? null : (T) rootView.findViewById(id);
    }

    private boolean isViewAlive() {
        return rootView != null;
    }

    private void bindStoreIdentity() {
        if (!isViewAlive()) return;
        String storeName = StoreConfigStore.getStoreName(requireContext());
        ((TextView) findViewById(R.id.tvStoreName)).setText(storeName);

        final ImageView imgStoreLogo = findViewById(R.id.imgStoreLogo);
        final TextView tvStoreLogoInitial = findViewById(R.id.tvStoreLogoInitial);
        final String logoUri = StoreConfigStore.getLogoUri(requireContext());

        if (logoUri == null || logoUri.trim().isEmpty()) {
            logoLoadGeneration++;
            imgStoreLogo.setImageDrawable(null);
            tvStoreLogoInitial.setText(extractStoreInitial(storeName));
            tvStoreLogoInitial.setVisibility(View.VISIBLE);
            return;
        }

        if (logoUri.equals(boundLogoUri) && boundLogoBitmap != null && !boundLogoBitmap.isRecycled()) {
            imgStoreLogo.setImageBitmap(boundLogoBitmap);
            tvStoreLogoInitial.setVisibility(View.GONE);
            return;
        }

        // Tampilkan inisial sementara hingga decode background selesai
        imgStoreLogo.setImageDrawable(null);
        tvStoreLogoInitial.setText(extractStoreInitial(storeName));
        tvStoreLogoInitial.setVisibility(View.VISIBLE);

        final int requestGen = ++logoLoadGeneration;
        final android.content.Context appContext = requireContext().getApplicationContext();
        DbExecutor.runAsync(
                () -> ImageDecodeHelper.decodeSampled(appContext, logoUri, LOGO_MAX_DIMENSION),
                bitmap -> {
                    if (!isViewAlive() || requestGen != logoLoadGeneration) {
                        if (bitmap != null && !bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                        return;
                    }
                    if (bitmap == null) {
                        StoreConfigStore.clearLogo(requireContext());
                        boundLogoUri = null;
                        if (boundLogoBitmap != null && !boundLogoBitmap.isRecycled()) {
                            boundLogoBitmap.recycle();
                        }
                        boundLogoBitmap = null;
                        imgStoreLogo.setImageDrawable(null);
                        tvStoreLogoInitial.setText(extractStoreInitial(StoreConfigStore.getStoreName(requireContext())));
                        tvStoreLogoInitial.setVisibility(View.VISIBLE);
                        return;
                    }
                    if (boundLogoBitmap != null && !boundLogoBitmap.isRecycled()) {
                        boundLogoBitmap.recycle();
                    }
                    boundLogoUri = logoUri;
                    boundLogoBitmap = bitmap;
                    imgStoreLogo.setImageBitmap(bitmap);
                    tvStoreLogoInitial.setVisibility(View.GONE);
                },
                throwable -> {
                    if (!isViewAlive() || requestGen != logoLoadGeneration) return;
                    StoreConfigStore.clearLogo(requireContext());
                    imgStoreLogo.setImageDrawable(null);
                    tvStoreLogoInitial.setText(extractStoreInitial(StoreConfigStore.getStoreName(requireContext())));
                    tvStoreLogoInitial.setVisibility(View.VISIBLE);
                }
        );
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
        if (!isViewAlive()) return;
        long now = System.currentTimeMillis();
        if (!force && statsLoading) return;
        if (!force && statsLoadedOnce && (now - lastStatsLoadedAtMs) < STATS_REFRESH_INTERVAL_MS) return;

        statsLoading = true;
        pendingStatRequests = 4;
        setStatsLoading(true);

        TransactionRepository.getTodayCountOptimizedAsync(requireContext(), todayCount -> {
                    if (!isViewAlive()) return;
                    ((TextView) findViewById(R.id.tvStatTransactions)).setText(String.valueOf(todayCount));
                    onStatLoaded();
                }, throwable -> {
                    if (!isViewAlive()) return;
                    ((TextView) findViewById(R.id.tvStatTransactions)).setText("0");
                    onStatLoaded();
                });

        TransactionRepository.getTodayIncomeOptimizedAsync(requireContext(), todayIncome -> {
                    if (!isViewAlive()) return;
                    ((TextView) findViewById(R.id.tvStatIncome)).setText(CurrencyUtils.formatRupiah(todayIncome));
                    onStatLoaded();
                }, throwable -> {
                    if (!isViewAlive()) return;
                    ((TextView) findViewById(R.id.tvStatIncome)).setText(CurrencyUtils.formatRupiah(0));
                    onStatLoaded();
                });

        ProductRepository.getCountAsync(requireContext(), count -> {
                    if (!isViewAlive()) return;
                    ((TextView) findViewById(R.id.tvStatProducts)).setText(String.valueOf(count));
                    onStatLoaded();
                }, throwable -> {
                    if (!isViewAlive()) return;
                    ((TextView) findViewById(R.id.tvStatProducts)).setText("0");
                    onStatLoaded();
                });

        TransactionRepository.getIncomeByPeriodOptimizedAsync(requireContext(), java.util.Calendar.getInstance(), true, monthlyIncome -> {
                    if (!isViewAlive()) return;
                    ((TextView) findViewById(R.id.tvStatIncomeMonth)).setText(CurrencyUtils.formatRupiah(monthlyIncome));
                    onStatLoaded();
                }, throwable -> {
                    if (!isViewAlive()) return;
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
        View layoutHomeStatsLoading = findViewById(R.id.layoutHomeStatsLoading);
        View layoutHomeStatsContent = findViewById(R.id.layoutHomeStatsContent);
        if (layoutHomeStatsLoading != null) {
            layoutHomeStatsLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        if (layoutHomeStatsContent != null) {
            layoutHomeStatsContent.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        }
    }

    private void applyMenuAccess() {
        if (!isViewAlive()) return;
        String username = AuthSessionStore.getUsername(requireContext());
        String role = AuthSessionStore.getRole(requireContext());
        List<String> allowed = MenuAccessStore.getForUser(requireContext(), username, role);

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
            ViewUtils.toast(requireActivity(), getString(R.string.no_menu_assigned));
        }
    }

    private void applyMenuVisibility(int menuId, boolean visible) {
        View menu = findViewById(menuId);
        if (menu == null) return;
        menu.setVisibility(visible ? View.VISIBLE : View.GONE);
        menu.setEnabled(visible);
    }
}
