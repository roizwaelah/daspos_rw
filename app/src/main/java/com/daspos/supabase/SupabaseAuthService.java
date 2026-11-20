package com.daspos.supabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SupabaseAuthService {
    private static final String EMAIL_REDIRECT_URL = "daspos://auth/callback";

    private SupabaseAuthService() {
    }

    public static AuthResult loginWithPassword(String emailOrUsername, String password) {
        if (!SupabaseConfig.isConfigured()) return AuthResult.failed("Supabase belum dikonfigurasi");
        if (emailOrUsername == null || !emailOrUsername.contains("@")) return AuthResult.failed("Format email tidak valid");

        HttpURLConnection conn = null;
        try {
            String endpoint = SupabaseConfig.getUrl() + "/auth/v1/token?grant_type=password";
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", SupabaseConfig.getAnonKey());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            JSONObject body = new JSONObject();
            body.put("email", emailOrUsername);
            body.put("password", password);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String response = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) {
                if (code == 400 || code == 401 || code == 403) {
                    return AuthResult.failed("Email atau password salah");
                }
                if (code == 429) return AuthResult.failed("Terlalu banyak percobaan, coba lagi nanti");
                return AuthResult.failed("Gagal terhubung ke server (HTTP " + code + ")");
            }

            JSONObject json = new JSONObject(response);
            String accessToken = json.optString("access_token", "");
            String refreshToken = json.optString("refresh_token", "");
            JSONObject user = json.optJSONObject("user");
            String userId = user == null ? "" : user.optString("id", "");
            String email = user == null ? emailOrUsername : user.optString("email", emailOrUsername);
            List<String> allowedMenus = parseAllowedMenus(user);
            ProfileResult profile = fetchProfile(userId, accessToken);
            if (!profile.isActive) return AuthResult.failed("Akun nonaktif atau belum memiliki profil");
            String role = profile.role.isEmpty() ? "admin" : profile.role;
            return AuthResult.success(
                    email,
                    role,
                    allowedMenus,
                    profile.outletId,
                    profile.outletName,
                    profile.outletAddress,
                    profile.outletPhone,
                    profile.outletEmail,
                    userId,
                    accessToken,
                    refreshToken
            );
        } catch (Exception e) {
            return AuthResult.failed("Tidak dapat terhubung ke server");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static TokenRefreshResult refreshTokens(String refreshToken) {
        if (!SupabaseConfig.isConfigured()) return null;
        if (refreshToken == null || refreshToken.trim().isEmpty()) return null;

        HttpURLConnection conn = null;
        try {
            String endpoint = SupabaseConfig.getUrl() + "/auth/v1/token?grant_type=refresh_token";
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", SupabaseConfig.getAnonKey());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            JSONObject body = new JSONObject();
            body.put("refresh_token", refreshToken.trim());
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String response = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) return null;

            JSONObject json = new JSONObject(response);
            String accessToken = json.optString("access_token", "");
            if (accessToken.trim().isEmpty()) return null;
            String newRefreshToken = json.optString("refresh_token", "");
            return new TokenRefreshResult(
                    accessToken,
                    newRefreshToken == null || newRefreshToken.trim().isEmpty() ? refreshToken.trim() : newRefreshToken.trim()
            );
        } catch (Exception ignored) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static final class TokenRefreshResult {
        public final String accessToken;
        public final String refreshToken;

        private TokenRefreshResult(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    public static SignupResult signupWithEmailActivation(String email, String password, String displayName) {
        if (!SupabaseConfig.isConfigured()) return SignupResult.failed("Supabase belum dikonfigurasi");
        if (email == null || !email.contains("@")) return SignupResult.failed("Email tidak valid");
        if (password == null || password.trim().isEmpty()) return SignupResult.failed("Password wajib diisi");

        HttpURLConnection conn = null;
        try {
            String endpoint = SupabaseConfig.getUrl() + "/auth/v1/signup";
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", SupabaseConfig.getAnonKey());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            JSONObject body = new JSONObject();
            body.put("email", email.trim());
            body.put("password", password);
            JSONObject data = new JSONObject();
            data.put("display_name", displayName == null ? "" : displayName.trim());
            data.put("outlet_name", displayName == null ? "" : displayName.trim());
            data.put("store_name", displayName == null ? "" : displayName.trim());
            data.put("role", "admin");
            body.put("data", data);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String response = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) {
                String msg = parseErrorMessage(response);
                if ((msg == null || msg.trim().isEmpty()) && code >= 500) {
                    msg = "Layanan pendaftaran sedang bermasalah (server email). Coba lagi beberapa menit lagi.";
                }
                return SignupResult.failed(normalizeSignupError(msg));
            }
            return SignupResult.success(true);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().trim();
            if (msg.isEmpty()) msg = "Pendaftaran gagal";
            return SignupResult.failed(normalizeSignupError(msg));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static SignupResult resendSignupVerification(String email) {
        if (!SupabaseConfig.isConfigured()) return SignupResult.failed("Supabase belum dikonfigurasi");
        if (email == null || !email.contains("@")) return SignupResult.failed("Email tidak valid");

        HttpURLConnection conn = null;
        try {
            String endpoint = SupabaseConfig.getUrl() + "/auth/v1/resend";
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", SupabaseConfig.getAnonKey());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            JSONObject body = new JSONObject();
            body.put("type", "signup");
            body.put("email", email.trim());
            JSONObject options = new JSONObject();
            options.put("email_redirect_to", EMAIL_REDIRECT_URL);
            options.put("emailRedirectTo", EMAIL_REDIRECT_URL);
            body.put("options", options);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            String response = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) {
                String msg = parseErrorMessage(response);
                return SignupResult.failed(normalizeSignupError(msg));
            }
            return SignupResult.success(true);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().trim();
            if (msg.isEmpty()) msg = "Gagal mengirim ulang email verifikasi";
            return SignupResult.failed(normalizeSignupError(msg));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static SignupResult signupCashierByAdmin(
            String adminAccessToken,
            String outletId,
            String email,
            String password,
            String displayName,
            List<String> allowedMenus
    ) {
        if (!SupabaseConfig.isConfigured()) return SignupResult.failed("Supabase belum dikonfigurasi");
        if (adminAccessToken == null || adminAccessToken.trim().isEmpty()) return SignupResult.failed("Sesi admin tidak valid");
        if (outletId == null || outletId.trim().isEmpty()) return SignupResult.failed("Outlet admin tidak ditemukan");
        if (email == null || !email.contains("@")) return SignupResult.failed("Email tidak valid");
        if (password == null || password.trim().isEmpty()) return SignupResult.failed("Password wajib diisi");
        return createCashierViaFunction(adminAccessToken, outletId, email, password, displayName, allowedMenus);
    }

    public static SignupResult deleteCashierByAdmin(String adminAccessToken, String email) {
        if (!SupabaseConfig.isConfigured()) return SignupResult.failed("Supabase belum dikonfigurasi");
        if (adminAccessToken == null || adminAccessToken.trim().isEmpty()) return SignupResult.failed("Sesi admin tidak valid");
        if (email == null || !email.contains("@")) return SignupResult.failed("Email kasir tidak valid");
        return deleteCashierByAdminViaFunction(adminAccessToken, email);
    }

    public static SignupResult deleteCurrentAccount(String accessToken, String userId, boolean purgeOutlet) {
        if (!SupabaseConfig.isConfigured()) return SignupResult.failed("Supabase belum dikonfigurasi");
        if (accessToken == null || accessToken.trim().isEmpty()) return SignupResult.failed("Sesi login tidak valid");
        if (userId == null || userId.trim().isEmpty()) return SignupResult.failed("ID user tidak valid");
        return deleteCurrentAccountViaFunction(accessToken, userId, purgeOutlet);
    }

    public static ListCashiersResult listCashiersByAdmin(String adminAccessToken) {
        String functionUrl = SupabaseConfig.getCreateCashierFunctionUrl();
        if (functionUrl.isEmpty()) {
            return ListCashiersResult.failed("Fungsi create-cashier belum dikonfigurasi");
        }
        if (adminAccessToken == null || adminAccessToken.trim().isEmpty()) {
            return ListCashiersResult.failed("Sesi admin tidak valid");
        }

        try {
            JSONObject body = new JSONObject();
            body.put("action", "list");
            AuthHttpResult result = sendAuthenticatedRequest("POST", functionUrl, adminAccessToken, body.toString(), true);
            int code = result.code;
            String response = result.body;
            if (code < 200 || code >= 300) {
                String msg = parseErrorMessage(response);
                if (msg.isEmpty()) msg = "Gagal mengambil daftar kasir";
                return ListCashiersResult.failed(msg);
            }
            JSONObject json = new JSONObject(response);
            JSONArray arr = json.optJSONArray("users");
            List<CashierUser> users = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject row = arr.optJSONObject(i);
                    if (row == null) continue;
                    String email = row.optString("email", "").trim();
                    if (email.isEmpty()) continue;
                    String role = row.optString("role", "kasir").trim();
                    JSONArray allowed = row.optJSONArray("allowed_menus");
                    List<String> menus = new ArrayList<>();
                    if (allowed != null) {
                        for (int j = 0; j < allowed.length(); j++) {
                            String menu = allowed.optString(j, "").trim().toLowerCase(Locale.ROOT);
                            if (!menu.isEmpty() && !menus.contains(menu)) menus.add(menu);
                        }
                    }
                    users.add(new CashierUser(email, role, menus));
                }
            }
            return ListCashiersResult.success(users);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().trim();
            if (msg.isEmpty()) msg = "Gagal mengambil daftar kasir";
            return ListCashiersResult.failed(msg);
        }
    }

    private static SignupResult createCashierViaFunction(
            String adminAccessToken,
            String outletId,
            String email,
            String password,
            String displayName,
            List<String> allowedMenus
    ) {
        String functionUrl = SupabaseConfig.getCreateCashierFunctionUrl();
        if (functionUrl.isEmpty()) {
            return SignupResult.failed("Fungsi create-cashier belum dikonfigurasi");
        }

        try {
            JSONObject body = new JSONObject();
            body.put("action", "create");
            body.put("email", email.trim());
            body.put("password", password);
            body.put("display_name", displayName == null ? "" : displayName.trim());
            body.put("outlet_id", outletId.trim());
            JSONArray allowed = new JSONArray();
            for (String menu : normalizeAllowedMenus(allowedMenus)) {
                allowed.put(menu);
            }
            body.put("allowed_menus", allowed);
            AuthHttpResult result = sendAuthenticatedRequest("POST", functionUrl, adminAccessToken, body.toString(), true);
            int code = result.code;
            String response = result.body;
            if (code < 200 || code >= 300) {
                String msg = parseErrorMessage(response);
                if (code == 404) {
                    msg = "Fungsi create-cashier belum di-deploy di Supabase";
                } else if ((msg == null || msg.trim().isEmpty()) && code >= 500) {
                    msg = "Layanan pendaftaran kasir sedang bermasalah. Coba lagi beberapa menit lagi.";
                }
                return SignupResult.failed(normalizeSignupError(msg));
            }
            return SignupResult.success(false);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().trim();
            if (msg.isEmpty()) msg = "Pendaftaran kasir gagal";
            return SignupResult.failed(normalizeSignupError(msg));
        }
    }

    private static ProfileResult fetchProfile(String userId, String accessToken) {
        if (userId == null || userId.isEmpty() || accessToken == null || accessToken.isEmpty()) return ProfileResult.empty();
        try {
            String endpoint = SupabaseConfig.getUrl() + "/rest/v1/profiles?id=eq." + userId + "&select=role,outlet_id,is_active";
            AuthHttpResult result = sendAuthenticatedRequest("GET", endpoint, accessToken, null, true);
            int code = result.code;
            String response = result.body;
            if (code < 200 || code >= 300) return ProfileResult.empty();

            JSONArray arr = new JSONArray(response);
            if (arr.length() == 0) return ProfileResult.empty();
            JSONObject row = arr.getJSONObject(0);
            String outletId = row.optString("outlet_id", "");
            OutletInfo outletInfo = fetchOutletInfo(outletId, accessToken);
            return new ProfileResult(
                    row.optString("role", ""),
                    outletId,
                    outletInfo.name,
                    outletInfo.address,
                    outletInfo.phone,
                    outletInfo.email,
                    row.optBoolean("is_active", true)
            );
        } catch (Exception ignored) {
            return ProfileResult.empty();
        }
    }

    private static OutletInfo fetchOutletInfo(String outletId, String accessToken) {
        if (outletId == null || outletId.trim().isEmpty()) return OutletInfo.empty();
        try {
            String endpoint = SupabaseConfig.getUrl()
                    + "/rest/v1/outlets?id=eq." + encode(outletId.trim())
                    + "&select=name,address,phone,email&limit=1";
            AuthHttpResult result = sendAuthenticatedRequest("GET", endpoint, accessToken, null, true);
            int code = result.code;
            String response = result.body;
            if (code < 200 || code >= 300) return fetchOutletInfoFallbackNameOnly(outletId, accessToken);
            JSONArray arr = new JSONArray(response);
            if (arr.length() == 0) return OutletInfo.empty();
            JSONObject row = arr.getJSONObject(0);
            return new OutletInfo(
                    row.optString("name", ""),
                    row.optString("address", ""),
                    row.optString("phone", ""),
                    row.optString("email", "")
            );
        } catch (Exception ignored) {
            return fetchOutletInfoFallbackNameOnly(outletId, accessToken);
        }
    }

    private static OutletInfo fetchOutletInfoFallbackNameOnly(String outletId, String accessToken) {
        try {
            String endpoint = SupabaseConfig.getUrl()
                    + "/rest/v1/outlets?id=eq." + encode(outletId.trim())
                    + "&select=name&limit=1";
            AuthHttpResult result = sendAuthenticatedRequest("GET", endpoint, accessToken, null, true);
            int code = result.code;
            String response = result.body;
            if (code < 200 || code >= 300) return OutletInfo.empty();
            JSONArray arr = new JSONArray(response);
            if (arr.length() == 0) return OutletInfo.empty();
            return new OutletInfo(arr.getJSONObject(0).optString("name", ""), "", "", "");
        } catch (Exception ignored) {
            return OutletInfo.empty();
        }
    }

    private static final class AuthHttpResult {
        final int code;
        final String body;

        private AuthHttpResult(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    // Terpusat: request REST/edge-function dengan Bearer token user.
    // HTTP 401 -> refresh session sekali -> retry dengan token baru.
    private static AuthHttpResult sendAuthenticatedRequest(
            String method,
            String url,
            String accessToken,
            String jsonBody,
            boolean allowTokenRefresh
    ) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("apikey", SupabaseConfig.getAnonKey());
            conn.setRequestProperty("Authorization", "Bearer " + accessToken.trim());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
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
                        && !refreshedToken.trim().equals(accessToken.trim())) {
                    return sendAuthenticatedRequest(method, url, refreshedToken, jsonBody, false);
                }
            }
            String responseBody = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            return new AuthHttpResult(code, responseBody);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readBody(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    public static final class AuthResult {
        public final boolean authenticated;
        public final String username;
        public final String role;
        public final List<String> allowedMenus;
        public final String outletId;
        public final String outletName;
        public final String outletAddress;
        public final String outletPhone;
        public final String outletEmail;
        public final String userId;
        public final String accessToken;
        public final String refreshToken;
        public final String message;

        private AuthResult(
                boolean authenticated,
                String username,
                String role,
                List<String> allowedMenus,
                String outletId,
                String outletName,
                String outletAddress,
                String outletPhone,
                String outletEmail,
                String userId,
                String accessToken,
                String refreshToken,
                String message
        ) {
            this.authenticated = authenticated;
            this.username = username;
            this.role = role;
            this.allowedMenus = allowedMenus == null ? new ArrayList<>() : new ArrayList<>(allowedMenus);
            this.outletId = outletId;
            this.outletName = outletName;
            this.outletAddress = outletAddress;
            this.outletPhone = outletPhone;
            this.outletEmail = outletEmail;
            this.userId = userId;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.message = message == null ? "" : message;
        }

        private static AuthResult success(
                String username,
                String role,
                List<String> allowedMenus,
                String outletId,
                String outletName,
                String outletAddress,
                String outletPhone,
                String outletEmail,
                String userId,
                String accessToken,
                String refreshToken
        ) {
            return new AuthResult(
                    true,
                    username,
                    role,
                    allowedMenus,
                    outletId,
                    outletName,
                    outletAddress,
                    outletPhone,
                    outletEmail,
                    userId,
                    accessToken,
                    refreshToken,
                    ""
            );
        }

        private static AuthResult failed(String message) {
            return new AuthResult(false, "", "", new ArrayList<>(), "", "", "", "", "", "", "", "", message);
        }
    }

    public static final class SignupResult {
        public final boolean success;
        public final boolean activationEmailSent;
        public final String message;

        private SignupResult(boolean success, boolean activationEmailSent, String message) {
            this.success = success;
            this.activationEmailSent = activationEmailSent;
            this.message = message;
        }

        private static SignupResult success(boolean activationEmailSent) {
            return new SignupResult(true, activationEmailSent, "");
        }

        private static SignupResult failed(String message) {
            return new SignupResult(false, false, message == null ? "" : message);
        }
    }

    public static final class CashierUser {
        public final String email;
        public final String role;
        public final List<String> allowedMenus;

        private CashierUser(String email, String role, List<String> allowedMenus) {
            this.email = email == null ? "" : email;
            this.role = role == null ? "" : role;
            this.allowedMenus = allowedMenus == null ? new ArrayList<>() : new ArrayList<>(allowedMenus);
        }
    }

    public static final class ListCashiersResult {
        public final boolean success;
        public final List<CashierUser> users;
        public final String message;

        private ListCashiersResult(boolean success, List<CashierUser> users, String message) {
            this.success = success;
            this.users = users == null ? new ArrayList<>() : users;
            this.message = message == null ? "" : message;
        }

        private static ListCashiersResult success(List<CashierUser> users) {
            return new ListCashiersResult(true, users, "");
        }

        private static ListCashiersResult failed(String message) {
            return new ListCashiersResult(false, new ArrayList<>(), message);
        }
    }

    private static final class ProfileResult {
        private final String role;
        private final String outletId;
        private final String outletName;
        private final String outletAddress;
        private final String outletPhone;
        private final String outletEmail;
        private final boolean isActive;

        private ProfileResult(
                String role,
                String outletId,
                String outletName,
                String outletAddress,
                String outletPhone,
                String outletEmail,
                boolean isActive
        ) {
            this.role = role;
            this.outletId = outletId;
            this.outletName = outletName;
            this.outletAddress = outletAddress;
            this.outletPhone = outletPhone;
            this.outletEmail = outletEmail;
            this.isActive = isActive;
        }

        private static ProfileResult empty() {
            return new ProfileResult("", "", "", "", "", "", true);
        }
    }

    private static final class OutletInfo {
        private final String name;
        private final String address;
        private final String phone;
        private final String email;

        private OutletInfo(String name, String address, String phone, String email) {
            this.name = name;
            this.address = address;
            this.phone = phone;
            this.email = email;
        }

        private static OutletInfo empty() {
            return new OutletInfo("", "", "", "");
        }
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private static String parseErrorMessage(String response) {
        try {
            JSONObject json = new JSONObject(response);
            String msg = json.optString("error", "");
            if (!msg.isEmpty()) return msg;
            msg = json.optString("msg", "");
            if (!msg.isEmpty()) return msg;
            msg = json.optString("message", "");
            if (!msg.isEmpty()) return msg;
            msg = json.optString("error_description", "");
            if (!msg.isEmpty()) return msg;
            JSONObject err = json.optJSONObject("error");
            if (err != null) {
                msg = err.optString("message", "");
                if (!msg.isEmpty()) return msg;
                msg = err.optString("description", "");
                if (!msg.isEmpty()) return msg;
            }
            return "";
        } catch (Exception ignored) {
            return response == null ? "" : response.trim();
        }
    }

    private static String normalizeSignupError(String rawMessage) {
        String msg = rawMessage == null ? "" : rawMessage.trim();
        if (msg.isEmpty()) return "Pendaftaran gagal";

        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("email rate limit exceeded")
                || (lower.contains("rate limit") && lower.contains("email"))) {
            return "Batas kirim email aktivasi tercapai. Coba lagi beberapa menit lagi.";
        }
        if (lower.contains("error sending confirmation email")
                || lower.contains("sending confirmation email")
                || (lower.contains("confirmation") && lower.contains("email") && lower.contains("error"))) {
            return "Gagal mengirim email aktivasi. Periksa konfigurasi SMTP Supabase atau coba lagi nanti.";
        }
        if (lower.contains("rate limit")) {
            return "Terlalu banyak percobaan. Coba lagi beberapa menit lagi.";
        }
        return msg;
    }

    private static List<String> parseAllowedMenus(JSONObject user) {
        List<String> out = new ArrayList<>();
        if (user == null) return out;
        JSONObject userMetadata = user.optJSONObject("user_metadata");
        if (userMetadata == null) return out;
        JSONArray arr = userMetadata.optJSONArray("allowed_menus");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            String menu = arr.optString(i, "").trim().toLowerCase(Locale.ROOT);
            if (menu.isEmpty()) continue;
            if (!out.contains(menu)) out.add(menu);
        }
        return out;
    }

    private static List<String> normalizeAllowedMenus(List<String> source) {
        Set<String> dedup = new LinkedHashSet<>();
        if (source != null) {
            for (String item : source) {
                String menu = item == null ? "" : item.trim().toLowerCase(Locale.ROOT);
                if (menu.isEmpty()) continue;
                dedup.add(menu);
            }
        }
        return new ArrayList<>(dedup);
    }

    private static SignupResult deleteCashierByAdminViaFunction(String adminAccessToken, String email) {
        String functionUrl = SupabaseConfig.getDeleteAccountFunctionUrl();
        if (functionUrl.isEmpty()) {
            return SignupResult.failed("Fungsi hapus akun belum dikonfigurasi");
        }

        try {
            JSONObject body = new JSONObject();
            body.put("target_email", email.trim());
            AuthHttpResult result = sendAuthenticatedRequest("POST", functionUrl, adminAccessToken, body.toString(), true);
            int code = result.code;
            String response = result.body;
            if (code < 200 || code >= 300) {
                String msg = parseErrorMessage(response);
                if (code == 404) {
                    msg = "Fungsi delete-account belum di-deploy di Supabase";
                } else if (msg.isEmpty()) {
                    msg = "Gagal menghapus kasir";
                }
                return SignupResult.failed(msg);
            }
            return SignupResult.success(true);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().trim();
            if (msg.isEmpty()) msg = "Gagal menghapus kasir";
            return SignupResult.failed(msg);
        }
    }

    private static SignupResult deleteCurrentAccountViaFunction(String accessToken, String userId, boolean purgeOutlet) {
        String functionUrl = SupabaseConfig.getDeleteAccountFunctionUrl();
        if (functionUrl.isEmpty()) {
            return SignupResult.failed("Fungsi hapus akun belum dikonfigurasi");
        }

        try {
            JSONObject body = new JSONObject();
            body.put("user_id", userId.trim());
            body.put("purge_outlet", purgeOutlet);
            AuthHttpResult result = sendAuthenticatedRequest("POST", functionUrl, accessToken, body.toString(), true);
            int code = result.code;
            String response = result.body;
            if (code < 200 || code >= 300) {
                String msg = parseErrorMessage(response);
                if (code == 404) {
                    msg = "Fungsi delete-account belum di-deploy di Supabase";
                } else if (msg.isEmpty()) {
                    msg = "Gagal menghapus akun permanen";
                }
                return SignupResult.failed(msg);
            }
            return SignupResult.success(true);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().trim();
            if (msg.isEmpty()) msg = "Gagal menghapus akun permanen";
            return SignupResult.failed(msg);
        }
    }
}
