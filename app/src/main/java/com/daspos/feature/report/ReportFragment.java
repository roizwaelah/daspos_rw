package com.daspos.feature.report;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.core.app.MainActivity;
import com.daspos.feature.auth.MenuAccessGuard;
import com.daspos.feature.auth.MenuAccessStore;
import com.daspos.feature.transaction.StrukFragment;
import com.daspos.model.ReportItem;
import com.daspos.repository.TransactionRepository;
import com.daspos.shared.util.CurrencyUtils;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.DownloadsUriHelper;
import com.daspos.shared.util.ViewUtils;
import com.daspos.ui.UiStateRenderer;
import com.daspos.ui.state.ReportUiState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ReportFragment extends Fragment {
    private final Calendar selectedCalendar = Calendar.getInstance();
    private ReportAdapter adapter;
    private TextView tvSelectedDate;
    private TextView tvSummaryTransactionCount;
    private TextView tvSummaryIncome;
    private RadioGroup rgRecap;
    private ReportViewModel viewModel;
    private androidx.appcompat.app.AlertDialog progressDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!MenuAccessGuard.ensureFragmentAccess(this, MenuAccessStore.MENU_REPORT)) return;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_report, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshReport();
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        Toolbar toolbar = root.findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.reports));

        tvSelectedDate = root.findViewById(R.id.tvSelectedDate);
        tvSummaryTransactionCount = root.findViewById(R.id.tvSummaryTransactionCount);
        tvSummaryIncome = root.findViewById(R.id.tvSummaryIncome);
        rgRecap = root.findViewById(R.id.rgRecap);

        final RecyclerView rv = root.findViewById(R.id.rvReport);
        final View layoutState = root.findViewById(R.id.layoutReportState);
        final ProgressBar progress = root.findViewById(R.id.progressReport);
        final TextView tvState = root.findViewById(R.id.tvReportState);

        final LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        rv.setLayoutManager(layoutManager);
        adapter = new ReportAdapter(item -> {
            if (item == null || isMonthlyMode()) return;
            ((MainActivity) requireActivity()).navigateTo(StrukFragment.newInstance(item.getId(), false));
        });
        rv.setAdapter(adapter);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0) return;
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                if (firstVisibleItemPosition >= 0 && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 10) {
                    viewModel.loadNextPage();
                }
            }
        });

        viewModel = new ViewModelProvider(requireActivity()).get(ReportViewModel.class);
        viewModel.getReportItems().observe(getViewLifecycleOwner(), reportItems -> adapter.submit(reportItems));
        viewModel.getReportCount().observe(getViewLifecycleOwner(),
                count -> tvSummaryTransactionCount.setText(String.valueOf(count)));
        viewModel.getReportIncome().observe(getViewLifecycleOwner(), incomeValue ->
                tvSummaryIncome.setText(CurrencyUtils.formatRupiah(incomeValue == null ? 0 : incomeValue)));
        viewModel.getReportUiState().observe(getViewLifecycleOwner(),
                state -> UiStateRenderer.renderReportState(state, rv, layoutState, progress, tvState, getString(R.string.loading)));

        ImageButton btnPrev = root.findViewById(R.id.btnPrevPeriod);
        ImageButton btnNext = root.findViewById(R.id.btnNextPeriod);
        btnPrev.setOnClickListener(v -> shiftPeriod(-1));
        btnNext.setOnClickListener(v -> shiftPeriod(1));

        rgRecap.setOnCheckedChangeListener((group, checkedId) -> refreshReport());

        MaterialButton btnPdf = root.findViewById(R.id.btnPdf);
        MaterialButton btnExcel = root.findViewById(R.id.btnExcel);
        btnPdf.setOnClickListener(v -> startExportPdf());
        btnExcel.setOnClickListener(v -> startExportXlsx());
    }

    @Override
    public void onDestroyView() {
        hideProgressDialog();
        super.onDestroyView();
    }

    private void startExportPdf() {
        showProgressDialog("Mengekspor laporan PDF...");
        exportWithCurrentFilter((items, count, income, title) -> {
            String fileName = ReportExportHelper.buildSuggestedFileName("laporan_daspos", "pdf");
            Uri uri = DownloadsUriHelper.createDownloadUri(requireContext(), fileName, "application/pdf");
            if (uri == null) return ExportResult.failed();
            boolean ok = ReportExportHelper.exportPdf(requireContext(), uri, title, items, count, income);
            return ok
                    ? ExportResult.success(getString(R.string.export_success) + "\nTersimpan di Download/DasPos")
                    : ExportResult.failed();
        });
    }

    private void startExportXlsx() {
        showProgressDialog("Mengekspor laporan XLSX...");
        exportWithCurrentFilter((items, count, income, title) -> {
            String xlsxName = ReportExportHelper.buildSuggestedFileName("laporan_daspos", "xlsx");
            Uri xlsxUri = DownloadsUriHelper.createDownloadUri(requireContext(), xlsxName,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            boolean ok = xlsxUri != null && ReportExportHelper.exportXlsx(requireContext(), xlsxUri, items, count, income);
            if (ok) {
                return ExportResult.success(getString(R.string.xlsx_export_success) + "\nTersimpan di Download/DasPos");
            }

            String csvName = ReportExportHelper.buildSuggestedFileName("laporan_daspos", "csv");
            Uri csvUri = DownloadsUriHelper.createDownloadUri(requireContext(), csvName, "text/csv");
            boolean csvOk = csvUri != null && ReportExportHelper.exportCsv(requireContext(), csvUri, items);
            if (!csvOk) return ExportResult.failed();
            return ExportResult.success(
                    getString(R.string.export_success)
                            + "\nFile disimpan sebagai CSV (kompatibel Excel)"
                            + "\nTersimpan di Download/DasPos"
            );
        });
    }

    private void showProgressDialog(String message) {
        hideProgressDialog();

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        int padding = Math.round(getResources().getDisplayMetrics().density * 20);
        container.setPadding(padding, padding, padding, padding);

        ProgressBar progressBar = new ProgressBar(requireContext());
        progressBar.setIndeterminate(true);

        TextView textView = new TextView(requireContext());
        textView.setText(message);
        textView.setPadding(Math.round(getResources().getDisplayMetrics().density * 16), 0, 0, 0);

        container.addView(progressBar);
        container.addView(textView);

        progressDialog = new MaterialAlertDialogBuilder(requireActivity(), com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
                .setView(container)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
    }

    private void showExportResultNotification(boolean success, String message) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(success ? "Berhasil" : "Gagal")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void exportWithCurrentFilter(ExportAction action) {
        boolean monthly = isMonthlyMode();
        Calendar exportCalendar = (Calendar) selectedCalendar.clone();
        String exportTitle = tvSelectedDate.getText().toString();
        final Context appContext = requireContext().getApplicationContext();

        DbExecutor.runExportAsync(() -> {
            try {
                // Remote-aware period fetch kept from rw; runs off the export pool.
                List<ReportItem> items = TransactionRepository.getReportItemsByPeriod(appContext, exportCalendar, monthly);

                List<ReportItem> safeItems = items == null ? new ArrayList<>() : items;
                int count = 0;
                double income = 0;
                for (ReportItem item : safeItems) {
                    income += item.getTotal();
                    if (monthly) {
                        count += parseMonthlyRowTransactionCount(item.getDate());
                    } else {
                        count++;
                    }
                }

                ExportResult result = action.run(safeItems, count, income, exportTitle);

                Activity activity = getActivity();
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
                activity.runOnUiThread(() -> {
                    if (getActivity() == null || getActivity().isFinishing() || getActivity().isDestroyed()) return;
                    hideProgressDialog();
                    boolean ok = result != null && result.success;
                    String message = ok ? result.message : getString(R.string.export_failed);
                    showExportResultNotification(ok, message);
                });
            } catch (Exception e) {
                Activity activity = getActivity();
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
                activity.runOnUiThread(() -> {
                    if (getActivity() == null || getActivity().isFinishing() || getActivity().isDestroyed()) return;
                    hideProgressDialog();
                    showExportResultNotification(false, getString(R.string.export_failed));
                });
            }
        });
    }

    private static int parseMonthlyRowTransactionCount(String dateSummary) {
        if (dateSummary == null) return 0;
        String digits = dateSummary.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private interface ExportAction {
        ExportResult run(List<ReportItem> items, int count, double income, String title);
    }

    private static final class ExportResult {
        private final boolean success;
        private final String message;

        private ExportResult(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }

        private static ExportResult success(String message) {
            return new ExportResult(true, message);
        }

        private static ExportResult failed() {
            return new ExportResult(false, "");
        }
    }

    private void shiftPeriod(int diff) {
        boolean monthly = isMonthlyMode();
        selectedCalendar.add(monthly ? Calendar.MONTH : Calendar.DAY_OF_MONTH, diff);
        refreshReport();
    }

    private boolean isMonthlyMode() { return rgRecap.getCheckedRadioButtonId() == R.id.rbMonthly; }

    private void refreshReport() {
        if (tvSelectedDate == null || rgRecap == null || viewModel == null) return;
        boolean monthly = isMonthlyMode();
        if (monthly) tvSelectedDate.setText(new SimpleDateFormat("MMMM yyyy", new Locale("id", "ID")).format(selectedCalendar.getTime()));
        else tvSelectedDate.setText(new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID")).format(selectedCalendar.getTime()));
        viewModel.load(selectedCalendar, monthly);
    }
}
