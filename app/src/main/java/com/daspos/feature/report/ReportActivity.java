package com.daspos.feature.report;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.transaction.StrukActivity;
import com.daspos.model.ReportItem;
import com.daspos.repository.TransactionRepository;
import com.daspos.shared.util.CurrencyUtils;
import com.daspos.shared.util.ViewUtils;
import com.daspos.ui.UiStateRenderer;
import com.daspos.ui.state.ReportUiState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportActivity extends BaseActivity {
    private static final int REQ_EXPORT_PDF = 601;
    private static final int REQ_EXPORT_XLSX = 602;
    private final Calendar selectedCalendar = Calendar.getInstance();
    private ReportAdapter adapter;
    private TextView tvTodayHistoryInfo;
    private TextView tvSelectedDate;
    private TextView tvSummaryTransactionCount;
    private TextView tvSummaryIncome;
    private RadioGroup rgRecap;
    private ReportViewModel viewModel;
    private androidx.appcompat.app.AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.reports));

        tvTodayHistoryInfo = findViewById(R.id.tvTodayHistoryInfo);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvSummaryTransactionCount = findViewById(R.id.tvSummaryTransactionCount);
        tvSummaryIncome = findViewById(R.id.tvSummaryIncome);
        rgRecap = findViewById(R.id.rgRecap);

        final RecyclerView rv = findViewById(R.id.rvReport);
        final View layoutState = findViewById(R.id.layoutReportState);
        final ProgressBar progress = findViewById(R.id.progressReport);
        final TextView tvState = findViewById(R.id.tvReportState);

        String today = new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID")).format(new Date());
        tvTodayHistoryInfo.setText(today + " • " + TransactionRepository.getTodayCount(this) + " transaksi • " +
                CurrencyUtils.formatRupiah(TransactionRepository.getTodayIncome(this)));

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReportAdapter(new ReportAdapter.Listener() {
            @Override public void onReportItemClicked(ReportItem item) {
                if (item == null || isMonthlyMode()) return;
                startActivity(StrukActivity.createIntent(ReportActivity.this, item.getId()));
            }
        });
        rv.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(ReportViewModel.class);
        viewModel.getReportItems().observe(this, new Observer<List<ReportItem>>() {
            @Override public void onChanged(List<ReportItem> reportItems) { adapter.submit(reportItems); }
        });
        viewModel.getReportCount().observe(this, new Observer<Integer>() {
            @Override public void onChanged(Integer count) { tvSummaryTransactionCount.setText(String.valueOf(count)); }
        });
        viewModel.getReportIncome().observe(this, new Observer<Double>() {
            @Override public void onChanged(Double incomeValue) {
                tvSummaryIncome.setText(CurrencyUtils.formatRupiah(incomeValue == null ? 0 : incomeValue));
            }
        });
        viewModel.getReportUiState().observe(this, new Observer<ReportUiState>() {
            @Override public void onChanged(ReportUiState state) {
                UiStateRenderer.renderReportState(state, rv, layoutState, progress, tvState, getString(R.string.loading));
            }
        });

        ImageButton btnPrev = findViewById(R.id.btnPrevPeriod);
        ImageButton btnNext = findViewById(R.id.btnNextPeriod);
        btnPrev.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { shiftPeriod(-1); } });
        btnNext.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { shiftPeriod(1); } });

        rgRecap.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) { refreshReport(); }
        });

        MaterialButton btnPdf = findViewById(R.id.btnPdf);
        MaterialButton btnExcel = findViewById(R.id.btnExcel);
        btnPdf.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startExportPdf(); } });
        btnExcel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startExportXlsx(); } });

        refreshReport();
    }

    private void startExportPdf() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, ReportExportHelper.buildSuggestedFileName("laporan_daspos", "pdf"));
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQ_EXPORT_PDF);
    }

    private void startExportXlsx() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        intent.putExtra(Intent.EXTRA_TITLE, ReportExportHelper.buildSuggestedFileName("laporan_daspos", "xlsx"));
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQ_EXPORT_XLSX);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        boolean ok = false;
        if (requestCode == REQ_EXPORT_PDF) {
            showProgressDialog("Mengekspor laporan PDF...");
            ok = ReportExportHelper.exportPdf(this, uri, tvSelectedDate.getText().toString(), adapter.getItems(),
                    Integer.parseInt(tvSummaryTransactionCount.getText().toString()), parseMoney(tvSummaryIncome.getText().toString()));
            hideProgressDialog();
            showExportResultNotification(ok, getString(ok ? R.string.export_success : R.string.export_failed));
        } else if (requestCode == REQ_EXPORT_XLSX) {
            showProgressDialog("Mengekspor laporan XLSX...");
            ok = ReportExportHelper.exportXlsx(this, uri, adapter.getItems(),
                    Integer.parseInt(tvSummaryTransactionCount.getText().toString()), parseMoney(tvSummaryIncome.getText().toString()));
            hideProgressDialog();
            showExportResultNotification(ok, getString(ok ? R.string.xlsx_export_success : R.string.export_failed));
        }
    }


    private void showProgressDialog(String message) {
        hideProgressDialog();

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        int padding = Math.round(getResources().getDisplayMetrics().density * 20);
        container.setPadding(padding, padding, padding, padding);

        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);

        TextView textView = new TextView(this);
        textView.setText(message);
        textView.setPadding(Math.round(getResources().getDisplayMetrics().density * 16), 0, 0, 0);

        container.addView(progressBar);
        container.addView(textView);

        progressDialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
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
        new MaterialAlertDialogBuilder(this)
                .setTitle(success ? "Berhasil" : "Gagal")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private double parseMoney(String displayed) {
        try { return Double.parseDouble(displayed.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
    }

    private void shiftPeriod(int diff) {
        boolean monthly = isMonthlyMode();
        selectedCalendar.add(monthly ? Calendar.MONTH : Calendar.DAY_OF_MONTH, diff);
        refreshReport();
    }

    private boolean isMonthlyMode() { return rgRecap.getCheckedRadioButtonId() == R.id.rbMonthly; }

    private void refreshReport() {
        boolean monthly = isMonthlyMode();
        if (monthly) tvSelectedDate.setText(new SimpleDateFormat("MMMM yyyy", new Locale("id", "ID")).format(selectedCalendar.getTime()));
        else tvSelectedDate.setText(new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID")).format(selectedCalendar.getTime()));
        viewModel.load(selectedCalendar, monthly);
    }
}
