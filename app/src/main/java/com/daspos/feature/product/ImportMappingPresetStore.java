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

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(Context context, String signature, int[] mapping) {
        prefs(context).edit()
                .putInt(KEY_PREFIX + signature + "_name", mapping[0])
                .putInt(KEY_PREFIX + signature + "_price", mapping[1])
                .putInt(KEY_PREFIX + signature + "_stock", mapping[2])
                .apply();
    }

    public static void clear(Context context, String signature) {
        prefs(context).edit()
                .remove(KEY_PREFIX + signature + "_name")
                .remove(KEY_PREFIX + signature + "_price")
                .remove(KEY_PREFIX + signature + "_stock")
                .apply();
    }

    public static int[] load(Context context, String signature, int columnCount) {
        SharedPreferences p = prefs(context);
        int name = p.getInt(KEY_PREFIX + signature + "_name", -1);
        int price = p.getInt(KEY_PREFIX + signature + "_price", -1);
        int stock = p.getInt(KEY_PREFIX + signature + "_stock", -1);
        return validate(name, price, stock, columnCount);
    }

    public static void saveNamed(Context context, String presetName, int[] mapping) {
        SharedPreferences p = prefs(context);
        String key = KEY_NAMED + sanitize(presetName);
        p.edit()
                .putInt(key + "_name", mapping[0])
                .putInt(key + "_price", mapping[1])
                .putInt(key + "_stock", mapping[2])
                .putString(KEY_NAMED_LIST, appendName(p.getString(KEY_NAMED_LIST, ""), presetName))
                .apply();
    }

    public static int[] loadNamed(Context context, String presetName, int columnCount) {
        SharedPreferences p = prefs(context);
        String key = KEY_NAMED + sanitize(presetName);
        int name = p.getInt(key + "_name", -1);
        int price = p.getInt(key + "_price", -1);
        int stock = p.getInt(key + "_stock", -1);
        return validate(name, price, stock, columnCount);
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

    private static int[] validate(int name, int price, int stock, int columnCount) {
        if (name < 0 || price < 0 || stock < 0) return null;
        if (name >= columnCount || price >= columnCount || stock >= columnCount) return null;
        if (name == price || name == stock || price == stock) return null;
        return new int[]{name, price, stock};
    }
}
