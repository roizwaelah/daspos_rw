package com.daspos.remote;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class RemoteAuthBridgeService {
    private RemoteAuthBridgeService() {
    }

    public static AuthResult login(android.content.Context context, String providerType, String username, String password) {
        RemoteProviderConfigStore.RemoteConfig config = RemoteProviderConfigStore.get(context);
        if (config.edgeFunctionUrl.isEmpty()) {
            return AuthResult.failed("URL Edge Function/Bridge belum diisi");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(config.edgeFunctionUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            if (!config.apiKey.isEmpty()) {
                connection.setRequestProperty("x-api-key", config.apiKey);
            }
            if (!config.edgeFunctionSecret.isEmpty()) {
                connection.setRequestProperty("x-edge-secret", config.edgeFunctionSecret);
            }

            JSONObject payload = new JSONObject();
            payload.put("username", safe(username));
            payload.put("email", safe(username));
            payload.put("password", safe(password));

            JSONObject body = new JSONObject();
            body.put("provider", safe(providerType));
            body.put("action", "auth.login");
            body.put("base_url", config.baseUrl);
            body.put("payload", payload);

            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(bytes);
            }

            int status = connection.getResponseCode();
            String raw = readBody(connection);
            JSONObject response = parseResponse(raw);
            if (status < 200 || status >= 300) {
                return AuthResult.failed("HTTP " + status + " " + resolveMessage(response, raw));
            }
            if (response.has("success") && !response.optBoolean("success", true)) {
                return AuthResult.failed(resolveMessage(response, raw));
            }
            JSONObject data = response.optJSONObject("data");
            if (data == null) data = response;
            String resolvedUsername = valueOf(data, "username", "email");
            if (resolvedUsername.isEmpty()) resolvedUsername = safe(username);
            if (resolvedUsername.isEmpty()) return AuthResult.failed("Response auth.login tidak memuat username/email");
            String resolvedRole = valueOf(data, "role");
            if (resolvedRole.isEmpty()) resolvedRole = "kasir";
            return AuthResult.success(
                    resolvedUsername,
                    resolvedRole,
                    valueOf(data, "user_id", "id"),
                    valueOf(data, "access_token", "token"),
                    valueOf(data, "refresh_token"),
                    valueOf(data, "outlet_id"),
                    valueOf(data, "outlet_name", "store_name"),
                    valueOf(data, "outlet_address", "store_address"),
                    valueOf(data, "outlet_phone", "store_phone"),
                    valueOf(data, "outlet_email", "store_email"),
                    parseAllowedMenus(data.optJSONArray("allowed_menus"))
            );
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Login remote gagal" : e.getMessage().trim();
            return AuthResult.failed(message);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static RegisterResult register(
            android.content.Context context,
            String providerType,
            String username,
            String password,
            String storeName
    ) {
        RemoteProviderConfigStore.RemoteConfig config = RemoteProviderConfigStore.get(context);
        if (config.edgeFunctionUrl.isEmpty()) {
            return RegisterResult.failed("URL Edge Function/Bridge belum diisi");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(config.edgeFunctionUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            if (!config.apiKey.isEmpty()) {
                connection.setRequestProperty("x-api-key", config.apiKey);
            }
            if (!config.edgeFunctionSecret.isEmpty()) {
                connection.setRequestProperty("x-edge-secret", config.edgeFunctionSecret);
            }

            JSONObject payload = new JSONObject();
            payload.put("username", safe(username));
            payload.put("email", safe(username));
            payload.put("password", safe(password));
            payload.put("display_name", safe(storeName));
            payload.put("store_name", safe(storeName));
            payload.put("outlet_name", safe(storeName));
            payload.put("role", "admin");

            JSONObject body = new JSONObject();
            body.put("provider", safe(providerType));
            body.put("action", "auth.register");
            body.put("base_url", config.baseUrl);
            body.put("payload", payload);

            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(bytes);
            }

            int status = connection.getResponseCode();
            String raw = readBody(connection);
            JSONObject response = parseResponse(raw);
            if (status < 200 || status >= 300) {
                return RegisterResult.failed("HTTP " + status + " " + resolveMessage(response, raw));
            }
            if (response.has("success") && !response.optBoolean("success", true)) {
                return RegisterResult.failed(resolveMessage(response, raw));
            }

            JSONObject data = response.optJSONObject("data");
            if (data == null) data = response;
            boolean requireVerification = data.optBoolean("requires_email_verification",
                    data.optBoolean("activation_email_sent", false));
            String message = resolveMessage(response, raw);
            return RegisterResult.success(requireVerification, message);
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Pendaftaran remote gagal" : e.getMessage().trim();
            return RegisterResult.failed(message);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readBody(HttpURLConnection connection) {
        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(
                connection.getErrorStream() != null ? connection.getErrorStream() : connection.getInputStream(),
                StandardCharsets.UTF_8
        ))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static JSONObject parseResponse(String raw) {
        String text = safe(raw);
        if (text.isEmpty()) return new JSONObject();
        try {
            if (text.startsWith("{")) return new JSONObject(text);
            if (text.startsWith("[")) {
                JSONObject wrapped = new JSONObject();
                wrapped.put("success", true);
                wrapped.put("data", new JSONArray(text));
                return wrapped;
            }
            JSONObject wrapped = new JSONObject();
            wrapped.put("success", true);
            wrapped.put("message", text);
            return wrapped;
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private static String resolveMessage(JSONObject response, String raw) {
        if (response != null) {
            String message = valueOf(response, "message", "error");
            if (!message.isEmpty()) return message;
            JSONObject data = response.optJSONObject("data");
            if (data != null) {
                message = valueOf(data, "message", "error");
                if (!message.isEmpty()) return message;
            }
        }
        return safe(raw).isEmpty() ? "Login remote gagal" : safe(raw);
    }

    private static List<String> parseAllowedMenus(JSONArray array) {
        List<String> out = new ArrayList<>();
        if (array == null) return out;
        for (int i = 0; i < array.length(); i++) {
            String menu = safe(array.optString(i));
            if (menu.isEmpty()) continue;
            if (!out.contains(menu)) out.add(menu);
        }
        return out;
    }

    private static String valueOf(JSONObject object, String... keys) {
        if (object == null || keys == null) return "";
        for (String key : keys) {
            if (key == null || key.trim().isEmpty()) continue;
            String value = safe(object.optString(key, ""));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class AuthResult {
        public final boolean authenticated;
        public final String username;
        public final String role;
        public final String userId;
        public final String accessToken;
        public final String refreshToken;
        public final String outletId;
        public final String outletName;
        public final String outletAddress;
        public final String outletPhone;
        public final String outletEmail;
        public final List<String> allowedMenus;
        public final String message;

        private AuthResult(
                boolean authenticated,
                String username,
                String role,
                String userId,
                String accessToken,
                String refreshToken,
                String outletId,
                String outletName,
                String outletAddress,
                String outletPhone,
                String outletEmail,
                List<String> allowedMenus,
                String message
        ) {
            this.authenticated = authenticated;
            this.username = username;
            this.role = role;
            this.userId = userId;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.outletId = outletId;
            this.outletName = outletName;
            this.outletAddress = outletAddress;
            this.outletPhone = outletPhone;
            this.outletEmail = outletEmail;
            this.allowedMenus = allowedMenus == null ? new ArrayList<>() : new ArrayList<>(allowedMenus);
            this.message = message == null ? "" : message;
        }

        private static AuthResult success(
                String username,
                String role,
                String userId,
                String accessToken,
                String refreshToken,
                String outletId,
                String outletName,
                String outletAddress,
                String outletPhone,
                String outletEmail,
                List<String> allowedMenus
        ) {
            return new AuthResult(
                    true,
                    username,
                    role,
                    userId,
                    accessToken,
                    refreshToken,
                    outletId,
                    outletName,
                    outletAddress,
                    outletPhone,
                    outletEmail,
                    allowedMenus,
                    ""
            );
        }

        private static AuthResult failed(String message) {
            return new AuthResult(
                    false,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    new ArrayList<>(),
                    message
            );
        }
    }

    public static final class RegisterResult {
        public final boolean success;
        public final boolean requiresEmailVerification;
        public final String message;

        private RegisterResult(boolean success, boolean requiresEmailVerification, String message) {
            this.success = success;
            this.requiresEmailVerification = requiresEmailVerification;
            this.message = message == null ? "" : message;
        }

        private static RegisterResult success(boolean requiresEmailVerification, String message) {
            return new RegisterResult(true, requiresEmailVerification, message);
        }

        private static RegisterResult failed(String message) {
            return new RegisterResult(false, false, message);
        }
    }
}
