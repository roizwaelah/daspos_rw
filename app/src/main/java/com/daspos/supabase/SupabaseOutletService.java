package com.daspos.supabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SupabaseOutletService {
    private SupabaseOutletService() {
    }

    public static void updateOutletName(String outletId, String accessToken, String outletName) throws Exception {
        updateOutletInfo(outletId, accessToken, outletName, null, null, null);
    }

    public static void updateOutletInfo(
            String outletId,
            String accessToken,
            String outletName,
            String address,
            String phone,
            String email
    ) throws Exception {
        if (!SupabaseConfig.isConfigured()) throw new IllegalStateException("Supabase belum dikonfigurasi");
        if (outletId == null || outletId.trim().isEmpty()) throw new IllegalArgumentException("Outlet tidak valid");
        if (accessToken == null || accessToken.trim().isEmpty()) throw new IllegalArgumentException("Sesi login tidak valid");

        String normalizedOutletId = outletId.trim();
        List<String> endpointAttempts = new ArrayList<>();
        endpointAttempts.add(SupabaseConfig.getUrl()
                + "/rest/v1/outlets?id=eq." + encode(normalizedOutletId) + "&select=id");
        endpointAttempts.add(SupabaseConfig.getUrl()
                + "/rest/v1/outlets?code=eq." + encode(normalizedOutletId) + "&select=id");

        List<JSONObject> payloadAttempts = new ArrayList<>();

        JSONObject standardFull = new JSONObject();
        standardFull.put("name", valueOrEmpty(outletName));
        standardFull.put("address", nullableValue(address));
        standardFull.put("phone", nullableValue(phone));
        standardFull.put("email", nullableValue(email));
        payloadAttempts.add(standardFull);

        JSONObject standardNameAddress = new JSONObject();
        standardNameAddress.put("name", valueOrEmpty(outletName));
        standardNameAddress.put("address", nullableValue(address));
        payloadAttempts.add(standardNameAddress);

        JSONObject standardNameOnly = new JSONObject();
        standardNameOnly.put("name", valueOrEmpty(outletName));
        payloadAttempts.add(standardNameOnly);

        JSONObject alternateFull = new JSONObject();
        alternateFull.put("store_name", valueOrEmpty(outletName));
        alternateFull.put("store_address", nullableValue(address));
        alternateFull.put("store_phone", nullableValue(phone));
        alternateFull.put("store_email", nullableValue(email));
        payloadAttempts.add(alternateFull);

        JSONObject alternateNameAddress = new JSONObject();
        alternateNameAddress.put("store_name", valueOrEmpty(outletName));
        alternateNameAddress.put("store_address", nullableValue(address));
        payloadAttempts.add(alternateNameAddress);

        JSONObject alternateNameOnly = new JSONObject();
        alternateNameOnly.put("store_name", valueOrEmpty(outletName));
        payloadAttempts.add(alternateNameOnly);

        IllegalStateException lastError = null;
        for (String endpoint : endpointAttempts) {
            for (JSONObject payload : payloadAttempts) {
                try {
                    patchOutlet(endpoint, accessToken, payload);
                    return;
                } catch (IllegalStateException error) {
                    lastError = error;
                    String msg = error.getMessage() == null ? "" : error.getMessage();
                    if (!looksLikeSchemaMismatch(msg) && !looksLikeNoRowsUpdated(msg)) throw error;
                }
            }
        }

        if (lastError != null) {
            throw lastError;
        }
    }

    private static void patchOutlet(String endpoint, String accessToken, JSONObject payload) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("PATCH");
            conn.setRequestProperty("apikey", SupabaseConfig.getAnonKey());
            conn.setRequestProperty("Authorization", "Bearer " + accessToken.trim());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Prefer", "return=representation");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String response = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) {
                throw new IllegalStateException(response == null || response.trim().isEmpty()
                        ? "Gagal update outlet ke Supabase"
                        : response.trim());
            }
            if (response == null || response.trim().isEmpty()) {
                throw new IllegalStateException(buildNoRowsMessage(endpoint, accessToken));
            }
            JSONArray rows = new JSONArray(response);
            if (rows.length() == 0) {
                throw new IllegalStateException(buildNoRowsMessage(endpoint, accessToken));
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean looksLikeSchemaMismatch(String errorMessage) {
        String message = errorMessage == null ? "" : errorMessage.toLowerCase();
        return message.contains("column")
                || message.contains("schema cache")
                || message.contains("could not find")
                || message.contains("does not exist");
    }

    private static boolean looksLikeNoRowsUpdated(String errorMessage) {
        String message = errorMessage == null ? "" : errorMessage.toLowerCase();
        return message.contains("no rows updated");
    }

    private static String buildNoRowsMessage(String endpoint, String accessToken) {
        boolean exists = rowExists(endpoint, accessToken);
        if (exists) {
            return "Sinkron gagal: akses update outlet ditolak (RLS policy)";
        }
        return "Sinkron gagal: outlet tidak ditemukan dari sesi login (id/code tidak cocok)";
    }

    private static boolean rowExists(String endpoint, String accessToken) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", SupabaseConfig.getAnonKey());
            conn.setRequestProperty("Authorization", "Bearer " + accessToken.trim());
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int code = conn.getResponseCode();
            String response = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) return false;
            JSONArray arr = new JSONArray(response);
            return arr.length() > 0;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static Object nullableValue(String value) {
        return value == null ? JSONObject.NULL : value.trim();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private static String readBody(InputStream stream) {
        if (stream == null) return "";
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return "";
        }
    }
}
