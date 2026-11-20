package com.daspos.shared.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.InputStream;

public final class ImageDecodeHelper {
    private ImageDecodeHelper() {
    }

    /**
     * Decodes an image stream with subsampling so the longest side is at most
     * {@code maxDimension}px. Decoding a full-resolution camera photo on the
     * main thread causes noticeable UI jank and memory spikes.
     */
    public static Bitmap decodeSampled(Context context, String uriString, int maxDimension) {
        if (context == null || uriString == null || uriString.trim().isEmpty()) return null;
        Uri uri = Uri.parse(uriString);

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, bounds);
        } catch (Exception ignored) {
            return null;
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension);
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            return BitmapFactory.decodeStream(in, null, opts);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int calculateInSampleSize(int width, int height, int maxDimension) {
        int sample = 1;
        while (Math.max(width, height) / (sample * 2) >= maxDimension) {
            sample *= 2;
        }
        return sample;
    }
}
