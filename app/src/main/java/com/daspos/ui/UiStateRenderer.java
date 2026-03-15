package com.daspos.ui;

import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.daspos.ui.state.ListUiState;
import com.daspos.ui.state.ReportUiState;

public class UiStateRenderer {
    public static void renderListState(ListUiState<?> state, View contentView, View stateLayout, ProgressBar progressBar, TextView stateText, String loadingText) {
        if (state == null) return;
        if (state.getStatus() == ListUiState.Status.LOADING) {
            stateLayout.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            stateText.setText(loadingText);
            contentView.setVisibility(View.GONE);
        } else if (state.getStatus() == ListUiState.Status.EMPTY || state.getStatus() == ListUiState.Status.ERROR) {
            stateLayout.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            stateText.setText(state.getMessage());
            contentView.setVisibility(View.GONE);
        } else if (state.getStatus() == ListUiState.Status.SUCCESS) {
            stateLayout.setVisibility(View.GONE);
            contentView.setVisibility(View.VISIBLE);
        }
    }

    public static void renderReportState(ReportUiState state, View contentView, View stateLayout, ProgressBar progressBar, TextView stateText, String loadingText) {
        if (state == null) return;
        if (state.getStatus() == ReportUiState.Status.LOADING) {
            stateLayout.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            stateText.setText(loadingText);
            contentView.setVisibility(View.GONE);
        } else if (state.getStatus() == ReportUiState.Status.EMPTY || state.getStatus() == ReportUiState.Status.ERROR) {
            stateLayout.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            stateText.setText(state.getMessage());
            contentView.setVisibility(View.GONE);
        } else if (state.getStatus() == ReportUiState.Status.SUCCESS) {
            stateLayout.setVisibility(View.GONE);
            contentView.setVisibility(View.VISIBLE);
        }
    }
}
