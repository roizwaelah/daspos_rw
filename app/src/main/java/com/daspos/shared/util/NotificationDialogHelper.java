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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class NotificationDialogHelper {

    private NotificationDialogHelper() {
    }

    public static void showWarningConfirmation(
            Activity activity,
            int titleRes,
            int messageRes,
            int confirmTextRes,
            Runnable onConfirmed
    ) {
        showWarningConfirmation(
                activity,
                activity.getString(titleRes),
                activity.getString(messageRes),
                activity.getString(confirmTextRes),
                onConfirmed
        );
    }

    public static void showWarningConfirmation(
            Activity activity,
            String title,
            String message,
            String confirmText,
            Runnable onConfirmed
    ) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_notification, null);

        ((TextView) dialogView.findViewById(R.id.tvDialogTitle)).setText(title);
        ((TextView) dialogView.findViewById(R.id.tvDialogMessage)).setText(message);

        ImageView icon = dialogView.findViewById(R.id.imgDialogIcon);
        icon.setImageResource(R.drawable.bg_circle_warning_soft);
        icon.clearColorFilter();

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.DasPosAlertDialog)
                .setView(dialogView)
                .create();

        dialog.setOnShowListener(d -> {
            Drawable background = ContextCompat.getDrawable(activity, R.drawable.bg_dialog_notification);
            if (dialog.getWindow() != null && background != null) {
                dialog.getWindow().setBackgroundDrawable(background);
            }
        });

        MaterialButton btnCancel = dialogView.findViewById(R.id.btnDialogCancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnDialogConfirm);
        btnConfirm.setText(confirmText);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            onConfirmed.run();
        });

        dialog.show();
    }
}
