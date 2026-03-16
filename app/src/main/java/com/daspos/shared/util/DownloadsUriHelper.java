package com.daspos.shared.util;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;

public final class DownloadsUriHelper {
    private DownloadsUriHelper() {}

    public static Uri createDownloadUri(Context context, String displayName, String mimeType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DasPos");
            return context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        }
        return createLegacyDownloadFileUri(displayName);
    }

    private static Uri createLegacyDownloadFileUri(String displayName) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File appDir = new File(downloadsDir, "DasPos");
        if (!appDir.exists() && !appDir.mkdirs()) return null;
        return Uri.fromFile(new File(appDir, displayName));
    }
}
