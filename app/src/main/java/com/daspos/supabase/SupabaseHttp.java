package com.daspos.supabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Shared HTTP plumbing for Supabase REST/RPC calls: headers, timeouts,
 * error mapping, and the single 401 -> token-refresh -> retry path.
 */
public final class SupabaseHttp {
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 12000;

    /** Test seam: intercepts outbound requests without touching the network. */
    public interface RequestHandler {
        JSONArray handle(String method, String endpoint, String accessToken,
                         String jsonBody, String errorContext) throws Exception;
    }

    private static volatile RequestHandler requestHandlerOverride;

    private SupabaseHttp() {
    }

    public static void setRequestHandlerOverrideForTesting(RequestHandler handler) {
        requestHandlerOverride = handler;
    }

    public static JSONArray request(String method, String endpoint, String accessToken, String jsonBody, String errorContext) throws Exception {
        RequestHandler override = requestHandlerOverride;
        if (override != null) {
            return override.handle(method, endpoint, accessToken, jsonBody, errorContext);
        }
        return request(method, endpoint, accessToken, jsonBody, errorContext, true);
    }

    private static JSONArray request(String method, String endpoint, String accessToken, String jsonBody,
                                     String errorContext, boolean allowTokenRefresh) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("apikey", SupabaseConfig.getAnonKey());
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Prefer", "return=representation");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoInput(true);

            if (jsonBody != null) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = conn.getResponseCode();
            if (code == 401 && allowTokenRefresh) {
                String refreshedToken = SupabaseSessionRefresher.refreshAccessToken();
                if (refreshedToken != null && !refreshedToken.trim().isEmpty()
                        && !refreshedToken.trim().equals(accessToken == null ? "" : accessToken.trim())) {
                    return request(method, endpoint, refreshedToken, jsonBody, errorContext, false);
                }
            }
            String body = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Supabase " + errorContext + " request failed: HTTP " + code + " body=" + body);
            }

            if (body == null || body.trim().isEmpty()) return new JSONArray();
            if (body.trim().startsWith("[")) return new JSONArray(body);
            JSONArray wrapped = new JSONArray();
            wrapped.put(new JSONObject(body));
            return wrapped;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static JSONObject requestObject(String method, String endpoint, String accessToken, String jsonBody, String errorContext) throws Exception {
        JSONArray arr = request(method, endpoint, accessToken, jsonBody, errorContext);
        if (arr.length() == 0) return new JSONObject();
        return arr.getJSONObject(0);
    }

    public static String readBody(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    public static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}
