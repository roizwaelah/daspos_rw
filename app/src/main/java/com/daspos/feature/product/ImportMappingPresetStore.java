package com.daspos.feature.product;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImportMappingPresetStore {
    private static final String PREF = "import_mapping_preset";
    private static final String KEY_PREFIX = "sig_";
    private static final String KEY_NAMED = "named_";
    private static final String KEY_NAMED_LIST = "named_list";
    // Legacy per-field keys (pre tier/barcode import) kept for backward compat.
    private static final String LEGACY_NAME = "_name";
    private static final String LEGACY_PRICE = "_price";
    private static final String LEGACY_STOCK = "_stock";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(Context context, String signature, int[] mapping) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (int i = 0; i < ProductImportHelper.FIELD_COUNT; i++) {
            editor.putInt(KEY_PREFIX + signature + "_f" + i, safeIndex(mapping, i));
        }
        editor.apply();
    }

    public static void clear(Context context, String signature) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (int i = 0; i < ProductImportHelper.FIELD_COUNT; i++) {
            editor.remove(KEY_PREFIX + signature + "_f" + i);
        }
        editor.remove(KEY_PREFIX + signature + LEGACY_NAME)
                .remove(KEY_PREFIX + signature + LEGACY_PRICE)
                .remove(KEY_PREFIX + signature + LEGACY_STOCK)
                .apply();
    }

    public static int[] load(Context context, String signature, int columnCount) {
        SharedPreferences p = prefs(context);
        int[] mapping = new int[ProductImportHelper.FIELD_COUNT];
        Arrays.fill(mapping, -1);
        boolean found = false;
        for (int i = 0; i < ProductImportHelper.FIELD_COUNT; i++) {
            String key = KEY_PREFIX + signature + "_f" + i;
            if (p.contains(key)) {
                mapping[i] = p.getInt(key, -1);
                found = true;
            }
        }
        if (!found) {
            // Legacy 3-field preset format.
            int name = p.getInt(KEY_PREFIX + signature + LEGACY_NAME, -1);
            int price = p.getInt(KEY_PREFIX + signature + LEGACY_PRICE, -1);
            int stock = p.getInt(KEY_PREFIX + signature + LEGACY_STOCK, -1);
            if (name < 0 || price < 0 || stock < 0) return null;
            mapping[ProductImportHelper.IDX_NAME] = name;
            mapping[ProductImportHelper.IDX_PRICE] = price;
            mapping[ProductImportHelper.IDX_STOCK] = stock;
        }
        return validate(mapping, columnCount);
    }

    public static void saveNamed(Context context, String presetName, int[] mapping) {
        SharedPreferences.Editor editor = prefs(context).edit();
        String key = KEY_NAMED + sanitize(presetName);
        for (int i = 0; i < ProductImportHelper.FIELD_COUNT; i++) {
            editor.putInt(key + "_f" + i, safeIndex(mapping, i));
        }
        editor.putString(KEY_NAMED_LIST, appendName(prefs(context).getString(KEY_NAMED_LIST, ""), presetName))
                .apply();
    }

    public static int[] loadNamed(Context context, String presetName, int columnCount) {
        SharedPreferences p = prefs(context);
        String key = KEY_NAMED + sanitize(presetName);
        int[] mapping = new int[ProductImportHelper.FIELD_COUNT];
        Arrays.fill(mapping, -1);
        boolean found = false;
        for (int i = 0; i < ProductImportHelper.FIELD_COUNT; i++) {
            String fieldKey = key + "_f" + i;
            if (p.contains(fieldKey)) {
                mapping[i] = p.getInt(fieldKey, -1);
                found = true;
            }
        }
        if (!found) {
            int name = p.getInt(key + LEGACY_NAME, -1);
            int price = p.getInt(key + LEGACY_PRICE, -1);
            int stock = p.getInt(key + LEGACY_STOCK, -1);
            if (name < 0 || price < 0 || stock < 0) return null;
            mapping[ProductImportHelper.IDX_NAME] = name;
            mapping[ProductImportHelper.IDX_PRICE] = price;
            mapping[ProductImportHelper.IDX_STOCK] = stock;
        }
        return validate(mapping, columnCount);
    }

    public static List<String> getNamedPresets(Context context) {
        String raw = prefs(context).getString(KEY_NAMED_LIST, "");
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String part : raw.split("\n")) {
            if (!part.trim().isEmpty()) out.add(part.trim());
        }
        return out;
    }

    private static String appendName(String raw, String name) {
        List<String> names = new ArrayList<>();
        if (raw != null && !raw.trim().isEmpty()) names.addAll(Arrays.asList(raw.split("\n")));
        if (!names.contains(name)) names.add(name);
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (n == null || n.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n");
            sb.append(n.trim());
        }
        return sb.toString();
    }

    private static String sanitize(String value) {
        return value.trim().toLowerCase().replaceAll("[^a-z0-9_]+", "_");
    }

    private static int safeIndex(int[] mapping, int field) {
        return mapping != null && field < mapping.length ? mapping[field] : -1;
    }

    private static int[] validate(int[] mapping, int columnCount) {
        if (mapping == null) return null;
        int[] out = new int[ProductImportHelper.FIELD_COUNT];
        Arrays.fill(out, -1);
        for (int i = 0; i < ProductImportHelper.FIELD_COUNT && i < mapping.length; i++) {
            int col = mapping[i];
            if (col == -1) continue;
            if (col < 0 || col >= columnCount) return null;
            out[i] = col;
        }
        // Required fields must be mapped; every mapped column must be unique.
        for (int required : new int[]{
                ProductImportHelper.IDX_NAME,
                ProductImportHelper.IDX_PRICE,
                ProductImportHelper.IDX_STOCK}) {
            if (out[required] < 0) return null;
        }
        for (int i = 0; i < out.length; i++) {
            if (out[i] < 0) continue;
            for (int j = i + 1; j < out.length; j++) {
                if (out[j] == out[i]) return null;
            }
        }
        return out;
    }
}
