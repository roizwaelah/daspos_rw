package com.daspos.feature.settings;

import android.content.Context;
import android.content.SharedPreferences;

public class ReceiptConfigStore {
    private static final String PREF = "receipt_config";
    private static final String KEY_HEADER = "header";
    private static final String KEY_FOOTER = "footer";
    private static final String KEY_SHOW_LOGO = "show_logo";
    private static final String DEFAULT_FOOTER = "Terima kasih";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String getHeader(Context context) {
        return prefs(context).getString(KEY_HEADER, "");
    }

    public static String getFooter(Context context) {
        return prefs(context).getString(KEY_FOOTER, DEFAULT_FOOTER);
    }

    public static boolean shouldShowLogo(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_LOGO, true);
    }

    public static void save(Context context, String header, String footer, boolean showLogo) {
        prefs(context).edit()
                .putString(KEY_HEADER, header == null ? "" : header.trim())
                .putString(KEY_FOOTER, footer == null || footer.trim().isEmpty() ? DEFAULT_FOOTER : footer.trim())
                .putBoolean(KEY_SHOW_LOGO, showLogo)
                .apply();
    }
}
