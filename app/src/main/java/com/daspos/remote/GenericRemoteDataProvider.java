package com.daspos.remote;

import android.content.Context;

import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.SalesUnit;
import com.daspos.model.TransactionRecord;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class GenericRemoteDataProvider implements RemoteDataProvider {
    private final Context appContext;
    private final String type;

    public GenericRemoteDataProvider(Context context, String type) {
        this.appContext = context == null ? null : context.getApplicationContext();
        this.type = type == null || type.trim().isEmpty() ? "custom_api" : type.trim().toLowerCase();
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public boolean isConfigured(Context context) {
        return RemoteProviderConfigStore.isConfigured(context, type);
    }

    @Override
    public boolean requiresAccessToken() {
        return false;
    }

    @Override
    public boolean requiresOutletId() {
        return false;
    }

    @Override
    public boolean requiresUserId() {
        return false;
    }

    @Override
    public boolean supportsCashierManagement() {
        return true;
    }

    @Override
    public void addProduct(String outletId, String accessToken, Product product) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("product", toProductJson(product));
        invoke("products.add", outletId, "", accessToken, payload);
    }

    @Override
    public void updateProduct(String outletId, String accessToken, Product product) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("product", toProductJson(product));
        invoke("products.update", outletId, "", accessToken, payload);
    }

    @Override
    public void deleteProduct(String outletId, String accessToken, String productId) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("product_id", safe(productId));
        invoke("products.delete", outletId, "", accessToken, payload);
    }

    @Override
    public boolean hasEnoughStock(String outletId, String accessToken, List<CartItem> items) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("items", toItemsJson(items));
        JSONObject response = invoke("products.has_stock", outletId, "", accessToken, payload);
        JSONObject data = dataObject(response);
        if (data != null && data.has("has_enough_stock")) return data.optBoolean("has_enough_stock", false);
        if (data != null && data.has("enough")) return data.optBoolean("enough", false);
        return response.optBoolean("has_enough_stock", false);
    }

    @Override
    public void reduceStock(String outletId, String accessToken, List<CartItem> items) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("items", toItemsJson(items));
        invoke("products.reduce_stock", outletId, "", accessToken, payload);
    }

    @Override
    public void increaseStock(String outletId, String accessToken, List<CartItem> items) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("items", toItemsJson(items));
        invoke("products.increase_stock", outletId, "", accessToken, payload);
    }

    @Override
    public List<Product> getAllProducts(String outletId, String accessToken) throws Exception {
        JSONObject response = invoke("products.list", outletId, "", accessToken, new JSONObject());
        JSONArray data = dataArray(response);
        if (data == null) return Collections.emptyList();
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            Product parsed = parseProduct(data.optJSONObject(i));
            if (parsed != null) products.add(parsed);
        }
        return products;
    }

    @Override
    public Product getProductById(String outletId, String accessToken, String productId) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("product_id", safe(productId));
        JSONObject response = invoke("products.get_by_id", outletId, "", accessToken, payload);
        JSONObject data = dataObject(response);
        if (data == null && response.has("id")) data = response;
        return parseProduct(data);
    }

    @Override
    public String saveTransaction(
            String outletId,
            String userId,
            String accessToken,
            List<CartItem> items,
            double total,
            double discount,
            double pay,
            double change,
            String clientTransactionId
    ) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("items", toItemsJson(items));
        payload.put("total", total);
        payload.put("discount", discount);
        payload.put("pay", pay);
        payload.put("change", change);
        payload.put("client_transaction_id", safe(clientTransactionId));
        JSONObject response = invoke("transactions.save", outletId, userId, accessToken, payload);
        JSONObject data = dataObject(response);
        if (data != null) {
            String id = safe(data.optString("id"));
            if (!id.isEmpty()) return id;
            String transactionId = safe(data.optString("transaction_id"));
            if (!transactionId.isEmpty()) return transactionId;
        }
        String id = safe(response.optString("id"));
        if (!id.isEmpty()) return id;
        return safe(response.optString("transaction_id"));
    }

    @Override
    public List<TransactionRecord> getAllTransactions(String outletId, String accessToken) throws Exception {
        JSONObject response = invoke("transactions.list", outletId, "", accessToken, new JSONObject());
        JSONArray data = dataArray(response);
        if (data == null) return Collections.emptyList();
        List<TransactionRecord> records = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            TransactionRecord parsed = parseTransaction(data.optJSONObject(i));
            if (parsed != null) records.add(parsed);
        }
        return records;
    }

    @Override
    public TransactionRecord getTransactionById(String outletId, String accessToken, String transactionId) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("transaction_id", safe(transactionId));
        JSONObject response = invoke("transactions.get_by_id", outletId, "", accessToken, payload);
        JSONObject data = dataObject(response);
        if (data == null && response.has("id")) data = response;
        return parseTransaction(data);
    }

    @Override
    public boolean deleteTransactionById(String outletId, String accessToken, String transactionId) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("transaction_id", safe(transactionId));
        JSONObject response = invoke("transactions.delete", outletId, "", accessToken, payload);
        JSONObject data = dataObject(response);
        if (data != null && data.has("deleted")) return data.optBoolean("deleted", false);
        if (data != null && data.has("success")) return data.optBoolean("success", false);
        return response.optBoolean("deleted", response.optBoolean("success", true));
    }

    @Override
    public List<TransactionRecord> getTransactionsForReportPeriod(
            String outletId,
            String accessToken,
            Calendar selectedCalendar,
            boolean monthly
    ) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("monthly", monthly);
        payload.put("year", selectedCalendar.get(Calendar.YEAR));
        payload.put("month", selectedCalendar.get(Calendar.MONTH) + 1);
        payload.put("day", selectedCalendar.get(Calendar.DAY_OF_MONTH));
        JSONObject response = invoke("transactions.report_period", outletId, "", accessToken, payload);
        JSONArray data = dataArray(response);
        if (data == null) return Collections.emptyList();
        List<TransactionRecord> records = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            TransactionRecord parsed = parseTransaction(data.optJSONObject(i));
            if (parsed != null) records.add(parsed);
        }
        return records;
    }

    @Override
    public CreateCashierResult createCashierByAdmin(
            String accessToken,
            String outletId,
            String username,
            String password,
            String displayName,
            List<String> allowedMenus
    ) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("username", safe(username));
        payload.put("password", safe(password));
        payload.put("display_name", safe(displayName));
        payload.put("allowed_menus", new JSONArray(allowedMenus == null ? Collections.emptyList() : allowedMenus));
        JSONObject response = invoke("cashiers.create", outletId, "", accessToken, payload);
        String message = safe(response.optString("message"));
        JSONObject data = dataObject(response);
        if (message.isEmpty() && data != null) {
            message = safe(data.optString("message"));
        }
        return new CreateCashierResult(true, message);
    }

    @Override
    public ListCashiersResult listCashiersByAdmin(String accessToken) throws Exception {
        JSONObject response = invoke("cashiers.list", "", "", accessToken, new JSONObject());
        JSONArray data = dataArray(response);
        List<RemoteCashier> users = new ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                List<String> menus = new ArrayList<>();
                JSONArray rawMenus = item.optJSONArray("allowed_menus");
                if (rawMenus != null) {
                    for (int j = 0; j < rawMenus.length(); j++) {
                        String menu = safe(rawMenus.optString(j));
                        if (!menu.isEmpty()) menus.add(menu);
                    }
                }
                users.add(new RemoteCashier(
                        safe(item.optString("email")),
                        safe(item.optString("role")),
                        menus
                ));
            }
        }
        return new ListCashiersResult(true, users);
    }

    @Override
    public CreateCashierResult deleteCashierByAdmin(String accessToken, String email) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("email", safe(email));
        JSONObject response = invoke("cashiers.delete", "", "", accessToken, payload);
        String message = safe(response.optString("message"));
        JSONObject data = dataObject(response);
        if (message.isEmpty() && data != null) {
            message = safe(data.optString("message"));
        }
        return new CreateCashierResult(true, message);
    }

    private JSONObject invoke(
            String action,
            String outletId,
            String userId,
            String accessToken,
            JSONObject payload
    ) throws Exception {
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("Action remote tidak boleh kosong");
        }
        if (appContext == null) {
            throw new IllegalStateException("Context remote provider belum tersedia");
        }
        RemoteProviderConfigStore.RemoteConfig config = RemoteProviderConfigStore.get(appContext);
        String endpoint = config.edgeFunctionUrl;
        if (endpoint.isEmpty()) {
            throw new IllegalStateException("Endpoint Edge Function belum diisi untuk provider " + type);
        }

        JSONObject request = new JSONObject();
        request.put("provider", type);
        request.put("action", action);
        request.put("outlet_id", safe(outletId));
        request.put("user_id", safe(userId));
        request.put("base_url", config.baseUrl);
        request.put("payload", payload == null ? new JSONObject() : payload);

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(25000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            if (!config.apiKey.isEmpty()) {
                connection.setRequestProperty("x-api-key", config.apiKey);
            }
            if (!config.edgeFunctionSecret.isEmpty()) {
                connection.setRequestProperty("x-edge-secret", config.edgeFunctionSecret);
            }
            if (!safe(accessToken).isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + safe(accessToken));
            }

            byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body);
            }

            int status = connection.getResponseCode();
            String text = readBody(connection);
            JSONObject response = parseResponse(text);
            if (status >= 200 && status < 300) {
                if (response.has("success") && !response.optBoolean("success", true)) {
                    throw new IllegalStateException(resolveErrorMessage(response));
                }
                return response;
            }
            throw new IllegalStateException("HTTP " + status + ": " + resolveErrorMessage(response));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readBody(HttpURLConnection connection) {
        try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(
                        connection.getErrorStream() != null ? connection.getErrorStream() : connection.getInputStream(),
                        StandardCharsets.UTF_8
                )
        )) {
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

    private JSONObject parseResponse(String text) {
        String raw = safe(text);
        if (raw.isEmpty()) return new JSONObject();
        try {
            if (raw.startsWith("{")) return new JSONObject(raw);
            if (raw.startsWith("[")) {
                JSONObject wrapped = new JSONObject();
                wrapped.put("success", true);
                wrapped.put("data", new JSONArray(raw));
                return wrapped;
            }
            JSONObject wrapped = new JSONObject();
            wrapped.put("success", true);
            wrapped.put("message", raw);
            return wrapped;
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private JSONArray dataArray(JSONObject response) {
        if (response == null) return null;
        JSONArray data = response.optJSONArray("data");
        if (data != null) return data;
        if (response.has("items")) return response.optJSONArray("items");
        return null;
    }

    private JSONObject dataObject(JSONObject response) {
        if (response == null) return null;
        JSONObject data = response.optJSONObject("data");
        if (data != null) return data;
        if (response.has("result")) return response.optJSONObject("result");
        return null;
    }

    private String resolveErrorMessage(JSONObject response) {
        if (response == null) return "Remote call gagal";
        String message = safe(response.optString("message"));
        if (!message.isEmpty()) return message;
        message = safe(response.optString("error"));
        if (!message.isEmpty()) return message;
        JSONObject data = dataObject(response);
        if (data != null) {
            message = safe(data.optString("message"));
            if (!message.isEmpty()) return message;
            message = safe(data.optString("error"));
            if (!message.isEmpty()) return message;
        }
        return "Remote call gagal";
    }

    private JSONObject toProductJson(Product product) throws Exception {
        JSONObject json = new JSONObject();
        if (product == null) return json;
        json.put("id", safe(product.getId()));
        json.put("name", safe(product.getName()));
        json.put("price", product.getPrice());
        json.put("stock", product.getStock());
        json.put("price_ecer", product.getPriceEcer());
        json.put("price_renteng", product.getPriceRenteng());
        json.put("price_pak", product.getPricePak());
        json.put("price_karton", product.getPriceKarton());
        json.put("factor_renteng", product.getFactorRenteng());
        json.put("factor_pak", product.getFactorPak());
        json.put("factor_karton", product.getFactorKarton());
        return json;
    }

    private JSONArray toItemsJson(List<CartItem> items) throws Exception {
        JSONArray array = new JSONArray();
        if (items == null) return array;
        for (CartItem item : items) {
            if (item == null || item.getProduct() == null) continue;
            JSONObject json = new JSONObject();
            json.put("product_id", safe(item.getProduct().getId()));
            json.put("product_name", safe(item.getProduct().getName()));
            json.put("qty", item.getQty());
            json.put("unit_code", SalesUnit.normalize(item.getUnitCode()));
            json.put("unit_factor_to_base", Math.max(1, item.getUnitFactorToBase()));
            json.put("unit_price", item.getUnitPrice());
            json.put("subtotal", item.getSubtotal());
            json.put("base_qty", item.getBaseQty());
            array.put(json);
        }
        return array;
    }

    private Product parseProduct(JSONObject json) {
        if (json == null) return null;
        String id = safe(json.optString("id"));
        if (id.isEmpty()) id = safe(json.optString("product_id"));
        String name = safe(json.optString("name"));
        if (name.isEmpty()) name = safe(json.optString("product_name"));
        double price = json.optDouble("price", 0);
        double ecer = json.optDouble("price_ecer", price);
        double renteng = json.optDouble("price_renteng", ecer);
        double pak = json.optDouble("price_pak", ecer);
        double karton = json.optDouble("price_karton", ecer);
        int stock = json.optInt("stock", 0);
        int factorRenteng = json.optInt("factor_renteng", SalesUnit.defaultFactor(SalesUnit.RENTENG));
        int factorPak = json.optInt("factor_pak", SalesUnit.defaultFactor(SalesUnit.PAK));
        int factorKarton = json.optInt("factor_karton", SalesUnit.defaultFactor(SalesUnit.KARTON));
        return new Product(
                id,
                name,
                ecer,
                stock,
                ecer,
                renteng,
                pak,
                karton,
                factorRenteng,
                factorPak,
                factorKarton
        );
    }

    private TransactionRecord parseTransaction(JSONObject json) {
        if (json == null) return null;
        String id = safe(json.optString("id"));
        if (id.isEmpty()) id = safe(json.optString("transaction_id"));
        String date = safe(json.optString("date"));
        String time = safe(json.optString("time"));
        double total = json.optDouble("total", 0);
        double pay = json.optDouble("pay", 0);
        double change = json.optDouble("change", json.optDouble("change_amount", 0));
        List<CartItem> items = new ArrayList<>();
        JSONArray rawItems = json.optJSONArray("items");
        if (rawItems != null) {
            for (int i = 0; i < rawItems.length(); i++) {
                JSONObject itemJson = rawItems.optJSONObject(i);
                if (itemJson == null) continue;
                Product itemProduct = parseProduct(itemJson.optJSONObject("product"));
                if (itemProduct == null) {
                    String productId = safe(itemJson.optString("product_id"));
                    String productName = safe(itemJson.optString("product_name"));
                    double unitPrice = itemJson.optDouble("unit_price", itemJson.optDouble("price", 0));
                    itemProduct = new Product(productId, productName, unitPrice, 0);
                }
                int qty = itemJson.optInt("qty", 0);
                String unitCode = SalesUnit.normalize(itemJson.optString("unit_code"));
                int factor = itemJson.optInt("unit_factor_to_base", 1);
                double unitPrice = itemJson.optDouble("unit_price", itemJson.optDouble("price", 0));
                items.add(new CartItem(itemProduct, qty, unitCode, factor, unitPrice));
            }
        }
        double discount = json.optDouble("discount", 0);
        return new TransactionRecord(id, date, time, total, discount, pay, change, items);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

}
