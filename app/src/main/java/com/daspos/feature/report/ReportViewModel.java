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
    private final MutableLiveData<List<ReportItem>> reportItems = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> reportCount = new MutableLiveData<>(0);
    private final MutableLiveData<Double> reportIncome = new MutableLiveData<>(0.0);
    private final MutableLiveData<ReportUiState> reportUiState = new MutableLiveData<>();

    public ReportViewModel(@NonNull Application application) {
        super(application);
    }

    public MutableLiveData<List<ReportItem>> getReportItems() { return reportItems; }
    public MutableLiveData<Integer> getReportCount() { return reportCount; }
    public MutableLiveData<Double> getReportIncome() { return reportIncome; }
    public MutableLiveData<ReportUiState> getReportUiState() { return reportUiState; }

    public void load(Calendar selectedCalendar, boolean monthly) {
        reportUiState.setValue(ReportUiState.loading());
        try {
            List<ReportItem> items = TransactionRepository.getReportItemsByPeriod(getApplication(), selectedCalendar, monthly);
            int count = TransactionRepository.getCountByPeriod(getApplication(), selectedCalendar, monthly);
            double income = TransactionRepository.getIncomeByPeriod(getApplication(), selectedCalendar, monthly);

            reportItems.setValue(items);
            reportCount.setValue(count);
            reportIncome.setValue(income);

            if (items == null || items.isEmpty()) {
                reportUiState.setValue(ReportUiState.empty("Belum ada laporan pada periode ini"));
            } else {
                reportUiState.setValue(ReportUiState.success(items, count, income));
            }
        } catch (Exception e) {
            reportUiState.setValue(ReportUiState.error("Gagal memuat laporan"));
        }
    }
}
