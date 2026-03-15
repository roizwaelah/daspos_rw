package com.daspos.shared.util;

import android.app.Activity;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

public class ViewUtils {
    public static void setupBackToolbar(final Activity activity, Toolbar toolbar, String title) {
        toolbar.setTitle(title);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                activity.onBackPressed();
            }
        });
    }

    public static void toast(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
