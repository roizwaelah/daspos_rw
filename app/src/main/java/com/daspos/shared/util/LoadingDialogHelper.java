package com.daspos.shared.util;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class LoadingDialogHelper {
    private LoadingDialogHelper() {
    }

    public static AlertDialog show(Activity activity, String message) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.HORIZONTAL);
        int padding = Math.round(activity.getResources().getDisplayMetrics().density * 20);
        container.setPadding(padding, padding, padding, padding);

        ProgressBar progressBar = new ProgressBar(activity);
        progressBar.setIndeterminate(true);

        TextView textView = new TextView(activity);
        textView.setText(message);
        textView.setPadding(Math.round(activity.getResources().getDisplayMetrics().density * 16), 0, 0, 0);

        container.addView(progressBar);
        container.addView(textView);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
                .setView(container)
                .setCancelable(false)
                .create();
        dialog.show();
        return dialog;
    }

    public static void dismiss(AlertDialog dialog) {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
