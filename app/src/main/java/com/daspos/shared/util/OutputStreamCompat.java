package com.daspos.shared.util;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public final class OutputStreamCompat {
    private OutputStreamCompat() {}

    public static OutputStream openForWrite(Context context, Uri uri) {
        if (context == null || uri == null) return null;

        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                String path = uri.getPath();
                if (path == null || path.trim().isEmpty()) return null;
                return new FileOutputStream(new File(path));
            }

            OutputStream stream = context.getContentResolver().openOutputStream(uri, "w");
            if (stream != null) return stream;
            return context.getContentResolver().openOutputStream(uri);
        } catch (Exception e) {
            return null;
        }
    }
}
