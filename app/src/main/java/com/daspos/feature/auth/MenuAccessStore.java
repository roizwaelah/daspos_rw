package com.daspos.feature.auth;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MenuAccessStore {
    public static final String MENU_TRANSACTION = "transaction";
    public static final String MENU_PRODUCT = "product";
    public static final String MENU_BEST_SELLER = "best_seller";
    public static final String MENU_REPORT = "report";
    public static final String MENU_BACKUP = "backup";

    private static final String PREF = "menu_access";

    private MenuAccessStore() {
    }

    public static List<String> allMenus() {
        List<String> out = new ArrayList<>();
        out.add(MENU_TRANSACTION);
        out.add(MENU_PRODUCT);
        out.add(MENU_BEST_SELLER);
        out.add(MENU_REPORT);
        out.add(MENU_BACKUP);
        return out;
    }

    public static void saveForUser(Context context, String username, List<String> menus) {
        String key = keyOf(username);
        if (key.isEmpty()) return;
        JSONArray arr = new JSONArray();
        Set<String> dedup = new LinkedHashSet<>();
        if (menus != null) dedup.addAll(menus);
        for (String menu : dedup) {
            if (allMenus().contains(menu)) arr.put(menu);
        }
        prefs(context).edit().putString(key, arr.toString()).apply();
    }

    public static List<String> getForUser(Context context, String username, String role) {
        if ("admin".equalsIgnoreCase(safe(role)) || "owner".equalsIgnoreCase(safe(role))) {
            return allMenus();
        }
        String key = keyOf(username);
        if (key.isEmpty()) return new ArrayList<>();
        String raw = prefs(context).getString(key, "");
        if (raw == null || raw.trim().isEmpty()) return new ArrayList<>();

        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String menu = arr.optString(i, "");
                if (allMenus().contains(menu) && !out.contains(menu)) out.add(menu);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static List<String> getStoredForUser(Context context, String username) {
        String key = keyOf(username);
        if (key.isEmpty()) return new ArrayList<>();
        String raw = prefs(context).getString(key, "");
        if (raw == null || raw.trim().isEmpty()) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String menu = arr.optString(i, "");
                if (allMenus().contains(menu) && !out.contains(menu)) out.add(menu);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static boolean hasAccess(Context context, String username, String role, String menuKey) {
        return getForUser(context, username, role).contains(menuKey);
    }

    public static void clearForUser(Context context, String username) {
        String key = keyOf(username);
        if (key.isEmpty()) return;
        prefs(context).edit().remove(key).apply();
    }

    public static void clearAll(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static String keyOf(String username) {
        String normalized = safe(username).trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return "";
        return "menus_" + normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
