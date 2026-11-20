package com.daspos.feature.report;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.daspos.model.ReportItem;
import com.daspos.repository.TransactionRepository;
import com.daspos.ui.state.ReportUiState;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ReportViewModel extends AndroidViewModel {
    public static final int PAGE_SIZE = 50;

    private final MutableLiveData<List<ReportItem>> reportItems = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> reportCount = new MutableLiveData<>(0);
    private final MutableLiveData<Double> reportIncome = new MutableLiveData<>(0.0);
    private final MutableLiveData<ReportUiState> reportUiState = new MutableLiveData<>();
    private final List<ReportItem> accumulatedItems = new ArrayList<>();

    private Calendar activeCalendar;
    private boolean activeMonthly = false;
    private int currentPage = 0;
    private boolean isLoadingPage = false;
    private boolean hasMorePages = false;
    private int requestGeneration = 0;

    public ReportViewModel(@NonNull Application application) {
        super(application);
    }

    public MutableLiveData<List<ReportItem>> getReportItems() { return reportItems; }
    public MutableLiveData<Integer> getReportCount() { return reportCount; }
    public MutableLiveData<Double> getReportIncome() { return reportIncome; }
    public MutableLiveData<ReportUiState> getReportUiState() { return reportUiState; }

    public void load(Calendar selectedCalendar, boolean monthly) {
        activeCalendar = selectedCalendar == null ? Calendar.getInstance() : (Calendar) selectedCalendar.clone();
        activeMonthly = monthly;
        currentPage = 0;
        hasMorePages = !monthly;
        isLoadingPage = true;
        accumulatedItems.clear();
        final int generation = ++requestGeneration;

        reportUiState.setValue(ReportUiState.loading());
        fetchPage(0, generation, true);
    }

    public void loadNextPage() {
        if (activeMonthly || isLoadingPage || !hasMorePages || activeCalendar == null) return;
        isLoadingPage = true;
        final int nextPage = currentPage + 1;
        final int generation = requestGeneration;
        fetchPage(nextPage, generation, false);
    }

    private void fetchPage(final int page, final int generation, final boolean isFirstPage) {
        final int offset = page * PAGE_SIZE;
        final Calendar calSnapshot = (Calendar) activeCalendar.clone();
        final boolean monthlySnapshot = activeMonthly;

        // Paged DB path (getReportPageOptimized) serves the list, per-page rows,
        // and first-page aggregates (totalCount/totalIncome). Monthly mode returns
        // daily summaries in a single unpaged result.
        TransactionRepository.getReportPageOptimizedAsync(getApplication(), calSnapshot, monthlySnapshot, PAGE_SIZE, offset, pageResult -> {
            if (generation != requestGeneration) return;
            isLoadingPage = false;
            currentPage = page;
            hasMorePages = pageResult != null && pageResult.hasMore;

            if (isFirstPage) {
                accumulatedItems.clear();
                int count = pageResult == null ? 0 : pageResult.totalCount;
                double income = pageResult == null ? 0.0 : pageResult.totalIncome;
                reportCount.setValue(count);
                reportIncome.setValue(income);
            }

            if (pageResult != null && pageResult.items != null) {
                accumulatedItems.addAll(pageResult.items);
            }

            List<ReportItem> snapshot = new ArrayList<>(accumulatedItems);
            reportItems.setValue(snapshot);

            int effectiveCount = reportCount.getValue() == null ? snapshot.size() : reportCount.getValue();
            double effectiveIncome = reportIncome.getValue() == null ? 0.0 : reportIncome.getValue();

            if (snapshot.isEmpty()) {
                reportUiState.setValue(ReportUiState.empty("Belum ada laporan pada periode ini"));
            } else {
                reportUiState.setValue(ReportUiState.success(snapshot, effectiveCount, effectiveIncome));
            }
        }, throwable -> {
            if (generation != requestGeneration) return;
            isLoadingPage = false;
            if (isFirstPage) {
                reportUiState.setValue(ReportUiState.error("Gagal memuat laporan"));
            }
        });
    }
}
