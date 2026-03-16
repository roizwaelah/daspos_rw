package com.daspos.shared.util;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.daspos.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class NotificationDialogHelper {

    private NotificationDialogHelper() {
    }

    public static void showWarningConfirmation(
            Activity activity,
            int titleRes,
            int messageRes,
            int positiveRes,
            Runnable onConfirmed
    ) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_notification, null);

        ((TextView) dialogView.findViewById(R.id.tvDialogTitle)).setText(titleRes);
        ((TextView) dialogView.findViewById(R.id.tvDialogMessage)).setText(messageRes);

        ImageView icon = dialogView.findViewById(R.id.imgDialogIcon);
        icon.setImageResource(R.drawable.ic_warning);
        icon.setColorFilter(ContextCompat.getColor(activity, R.color.surface_white));

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.DasPosAlertDialog)
                .setView(dialogView)
                .setPositiveButton(positiveRes, (d, which) -> onConfirmed.run())
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            Drawable background = ContextCompat.getDrawable(activity, R.drawable.bg_dialog_notification);
            if (dialog.getWindow() != null && background != null) {
                dialog.getWindow().setBackgroundDrawable(background);
            }
        });

        dialog.show();
    }
}
