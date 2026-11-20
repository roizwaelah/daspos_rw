package com.daspos.supabase;

import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.SalesUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SupabaseProductService {
    private static final String SELECT_FULL =
            "id,name,barcode,price,cost_price,stock,unit_name,track_stock,price_ecer,price_renteng,price_pak,price_karton,factor_renteng,factor_pak,factor_karton";
    private static final String SELECT_LEGACY = "id,name,price,stock";
    private static final int PRODUCT_PAGE_SIZE = 500;

    private SupabaseProductService() {
    }

    public static List<Product> getAll(String outletId, String accessToken) throws Exception {
        JSONArray arr = new JSONArray();
        int offset = 0;
        while (true) {
            JSONArray page = requestProductsPage(outletId, accessToken, PRODUCT_PAGE_SIZE, offset);
            for (int i = 0; i < page.length(); i++) arr.put(page.getJSONObject(i));
            if (page.length() < PRODUCT_PAGE_SIZE) break;
            offset += page.length();
        }
        List<Product> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.add(parseProduct(o));
        }
        return out;
    }

    private static JSONArray requestProductsPage(String outletId, String accessToken, int limit, int offset) throws Exception {
        String endpoint = SupabaseConfig.getUrl()
                + "/rest/v1/products?outlet_id=eq." + encode(outletId)
                + "&select=" + SELECT_FULL
                + "&order=name.asc"
                + "&limit=" + limit
                + "&offset=" + offset;
        try {
            return request("GET", endpoint, accessToken, null);
        } catch (Exception e) {
            if (!isMissingTierColumnError(e)) throw e;
            String legacyEndpoint = SupabaseConfig.getUrl()
                    + "/rest/v1/products?outlet_id=eq." + encode(outletId)
                    + "&select=" + SELECT_LEGACY
                    + "&order=name.asc"
                    + "&limit=" + limit
                    + "&offset=" + offset;
            return request("GET", legacyEndpoint, accessToken, null);
        }
    }

    public static Product getById(String outletId, String accessToken, String productId) throws Exception {
        JSONArray arr;
        try {
            String endpoint = SupabaseConfig.getUrl()
                    + "/rest/v1/products?id=eq." + encode(productId)
                    + "&outlet_id=eq." + encode(outletId)
                    + "&select=" + SELECT_FULL + "&limit=1";
            arr = request("GET", endpoint, accessToken, null);
        } catch (Exception e) {
            if (!isMissingTierColumnError(e)) throw e;
            String endpoint = SupabaseConfig.getUrl()
                    + "/rest/v1/products?id=eq." + encode(productId)
                    + "&outlet_id=eq." + encode(outletId)
                    + "&select=" + SELECT_LEGACY + "&limit=1";
            arr = request("GET", endpoint, accessToken, null);
        }
        if (arr.length() == 0) return null;
        return parseProduct(arr.getJSONObject(0));
    }

    public static void add(String outletId, String accessToken, String name, double price, int stock) throws Exception {
        addWithId(outletId, accessToken, new Product("", name, price, stock));
    }

    public static void addWithId(String outletId, String accessToken, String id, String name, double price, int stock) throws Exception {
        Product product = new Product(id == null ? "" : id, name, price, stock);
        addWithId(outletId, accessToken, product);
    }

    public static void addWithId(String outletId, String accessToken, Product product) throws Exception {
        JSONObject payload = new JSONObject();
        if (product.getId() != null && !product.getId().trim().isEmpty()) payload.put("id", product.getId().trim());
        payload.put("outlet_id", outletId);
        payload.put("name", product.getName());
        payload.put("barcode", product.getBarcode());
        payload.put("price", product.getPriceEcer());
        payload.put("cost_price", product.getCostPrice());
        payload.put("stock", product.getStock());
        payload.put("unit_name", product.getUnitName());
        payload.put("track_stock", product.isTrackStock());
        payload.put("price_ecer", product.getPriceEcer());
        payload.put("price_renteng", product.getPriceRenteng());
        payload.put("price_pak", product.getPricePak());
        payload.put("price_karton", product.getPriceKarton());
        payload.put("factor_renteng", product.getFactorRenteng());
        payload.put("factor_pak", product.getFactorPak());
        payload.put("factor_karton", product.getFactorKarton());
        try {
            request("POST", SupabaseConfig.getUrl() + "/rest/v1/products", accessToken, payload.toString());
        } catch (Exception e) {
            if (!isMissingTierColumnError(e)) throw e;
            JSONObject legacyPayload = new JSONObject();
            if (product.getId() != null && !product.getId().trim().isEmpty()) legacyPayload.put("id", product.getId().trim());
            legacyPayload.put("outlet_id", outletId);
            legacyPayload.put("name", product.getName());
            legacyPayload.put("price", product.getPriceEcer());
            legacyPayload.put("stock", product.getStock());
            request("POST", SupabaseConfig.getUrl() + "/rest/v1/products", accessToken, legacyPayload.toString());
        }
    }

    public static void addBatch(String outletId, String accessToken, List<Product> products) throws Exception {
        if (products == null || products.isEmpty()) return;
        int chunkSize = 200;
        for (int start = 0; start < products.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, products.size());
            List<Product> slice = products.subList(start, end);
            JSONArray fullArray = new JSONArray();
            for (Product product : slice) {
                fullArray.put(buildProductPayload(outletId, product));
            }
            try {
                request("POST", SupabaseConfig.getUrl() + "/rest/v1/products", accessToken, fullArray.toString());
            } catch (Exception e) {
                if (!isMissingTierColumnError(e)) throw e;
                JSONArray legacyArray = new JSONArray();
                for (Product product : slice) {
                    legacyArray.put(buildLegacyProductPayload(outletId, product));
                }
                request("POST", SupabaseConfig.getUrl() + "/rest/v1/products", accessToken, legacyArray.toString());
            }
        }
    }

    private static JSONObject buildProductPayload(String outletId, Product product) throws JSONException {
        JSONObject payload = new JSONObject();
        if (product.getId() != null && !product.getId().trim().isEmpty()) payload.put("id", product.getId().trim());
        payload.put("outlet_id", outletId);
        payload.put("name", product.getName());
        payload.put("barcode", product.getBarcode());
        payload.put("price", product.getPriceEcer());
        payload.put("cost_price", product.getCostPrice());
        payload.put("stock", product.getStock());
        payload.put("unit_name", product.getUnitName());
        payload.put("track_stock", product.isTrackStock());
        payload.put("price_ecer", product.getPriceEcer());
        payload.put("price_renteng", product.getPriceRenteng());
        payload.put("price_pak", product.getPricePak());
        payload.put("price_karton", product.getPriceKarton());
        payload.put("factor_renteng", product.getFactorRenteng());
        payload.put("factor_pak", product.getFactorPak());
        payload.put("factor_karton", product.getFactorKarton());
        return payload;
    }

    private static JSONObject buildLegacyProductPayload(String outletId, Product product) throws JSONException {
        JSONObject legacyPayload = new JSONObject();
        if (product.getId() != null && !product.getId().trim().isEmpty()) legacyPayload.put("id", product.getId().trim());
        legacyPayload.put("outlet_id", outletId);
        legacyPayload.put("name", product.getName());
        legacyPayload.put("price", product.getPriceEcer());
        legacyPayload.put("stock", product.getStock());
        return legacyPayload;
    }

    public static void update(String outletId, String accessToken, Product product) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("name", product.getName());
        payload.put("barcode", product.getBarcode());
        payload.put("price", product.getPriceEcer());
        payload.put("cost_price", product.getCostPrice());
        payload.put("stock", product.getStock());
        payload.put("unit_name", product.getUnitName());
        payload.put("track_stock", product.isTrackStock());
        payload.put("price_ecer", product.getPriceEcer());
        payload.put("price_renteng", product.getPriceRenteng());
        payload.put("price_pak", product.getPricePak());
        payload.put("price_karton", product.getPriceKarton());
        payload.put("factor_renteng", product.getFactorRenteng());
        payload.put("factor_pak", product.getFactorPak());
        payload.put("factor_karton", product.getFactorKarton());
        String endpoint = SupabaseConfig.getUrl()
                + "/rest/v1/products?id=eq." + encode(product.getId())
                + "&outlet_id=eq." + encode(outletId);
        try {
            request("PATCH", endpoint, accessToken, payload.toString());
        } catch (Exception e) {
            if (!isMissingTierColumnError(e)) throw e;
            JSONObject legacyPayload = new JSONObject();
            legacyPayload.put("name", product.getName());
            legacyPayload.put("price", product.getPriceEcer());
            legacyPayload.put("stock", product.getStock());
            request("PATCH", endpoint, accessToken, legacyPayload.toString());
        }
    }

    public static void delete(String outletId, String accessToken, String productId) throws Exception {
        String endpoint = SupabaseConfig.getUrl()
                + "/rest/v1/products?id=eq." + encode(productId)
                + "&outlet_id=eq." + encode(outletId);
        request("DELETE", endpoint, accessToken, null);
    }

    public static boolean hasEnoughStock(String outletId, String accessToken, List<CartItem> items) throws Exception {
        Map<String, Product> productsById = getByIds(outletId, accessToken, collectProductIds(items));
        for (CartItem item : items) {
            if (item == null || item.getProduct() == null) continue;
            if (!item.getProduct().isTrackStock()) continue;
            Product p = productsById.get(item.getProduct().getId());
            if (p == null) return false;
            if (p.getStock() < item.getBaseQty()) return false;
        }
        return true;
    }

    public static void reduceStock(String outletId, String accessToken, List<CartItem> items) throws Exception {
        applyStockDelta(outletId, accessToken, items, -1);
    }

    public static void increaseStock(String outletId, String accessToken, List<CartItem> items) throws Exception {
        applyStockDelta(outletId, accessToken, items, 1);
    }

    private static void applyStockDelta(String outletId, String accessToken, List<CartItem> items, int direction) throws Exception {
        Map<String, Integer> qtyByProduct = aggregateQtyByProduct(items);
        Map<String, Product> productsById = getByIds(outletId, accessToken, qtyByProduct.keySet());

        for (Map.Entry<String, Integer> entry : qtyByProduct.entrySet()) {
            Product current = productsById.get(entry.getKey());
            if (current == null) continue;
            if (!current.isTrackStock()) continue;
            int delta = entry.getValue() * direction;
            int targetStock = delta < 0
                    ? Math.max(0, current.getStock() + delta)
                    : current.getStock() + delta;
            Product updated = new Product(
                    current.getId(),
                    current.getName(),
                    current.getBarcode(),
                    current.getPrice(),
                    current.getCostPrice(),
                    targetStock,
                    current.getUnitName(),
                    current.isTrackStock(),
                    current.getPriceEcer(),
                    current.getPriceRenteng(),
                    current.getPricePak(),
                    current.getPriceKarton(),
                    current.getFactorRenteng(),
                    current.getFactorPak(),
                    current.getFactorKarton()
            );
            update(outletId, accessToken, updated);
        }
    }

    private static Map<String, Product> getByIds(String outletId, String accessToken, Iterable<String> productIds) throws Exception {
        List<String> ids = new ArrayList<>();
        for (String id : productIds) {
            if (id != null && !id.trim().isEmpty()) ids.add(id.trim());
        }
        Map<String, Product> out = new HashMap<>();
        if (ids.isEmpty()) return out;

        String joined = String.join(",", ids);
        JSONArray arr;
        try {
            String endpoint = SupabaseConfig.getUrl()
                    + "/rest/v1/products?outlet_id=eq." + encode(outletId)
                    + "&id=in.(" + joined + ")"
                    + "&select=" + SELECT_FULL;
            arr = request("GET", endpoint, accessToken, null);
        } catch (Exception e) {
            if (!isMissingTierColumnError(e)) throw e;
            String endpoint = SupabaseConfig.getUrl()
                    + "/rest/v1/products?outlet_id=eq." + encode(outletId)
                    + "&id=in.(" + joined + ")"
                    + "&select=" + SELECT_LEGACY;
            arr = request("GET", endpoint, accessToken, null);
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Product p = parseProduct(o);
            out.put(p.getId(), p);
        }
        return out;
    }

    private static List<String> collectProductIds(List<CartItem> items) {
        List<String> ids = new ArrayList<>();
        for (CartItem item : items) {
            if (item == null || item.getProduct() == null) continue;
            if (!item.getProduct().isTrackStock()) continue;
            String productId = item.getProduct().getId();
            if (productId == null || productId.trim().isEmpty()) continue;
            ids.add(productId);
        }
        return ids;
    }

    private static Map<String, Integer> aggregateQtyByProduct(List<CartItem> items) {
        Map<String, Integer> out = new HashMap<>();
        for (CartItem item : items) {
            if (item == null || item.getProduct() == null) continue;
            if (!item.getProduct().isTrackStock()) continue;
            String productId = item.getProduct().getId();
            if (productId == null || productId.trim().isEmpty()) continue;
            int prev = out.containsKey(productId) ? out.get(productId) : 0;
            out.put(productId, prev + Math.max(0, item.getBaseQty()));
        }
        return out;
    }

    private static JSONArray request(String method, String endpoint, String accessToken, String jsonBody) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("apikey", SupabaseConfig.getAnonKey());
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Prefer", "return=representation");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoInput(true);

            if (jsonBody != null) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = conn.getResponseCode();
            String body = readBody(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Supabase products request failed: HTTP " + code + " body=" + body);
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

    private static String readBody(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static Product parseProduct(JSONObject o) {
        String id = o.optString("id", "");
        String name = o.optString("name", "");
        double basePrice = o.optDouble("price", 0);
        String barcode = o.optString("barcode", "");
        double costPrice = o.has("cost_price") ? o.optDouble("cost_price", 0) : 0;
        int stock = o.optInt("stock", 0);
        String unitName = o.optString("unit_name", "pcs");
        boolean trackStock = !o.has("track_stock") || o.optBoolean("track_stock", true);

        double priceEcer = o.has("price_ecer") ? o.optDouble("price_ecer", basePrice) : basePrice;
        if (priceEcer <= 0) priceEcer = basePrice;
        if (priceEcer <= 0) priceEcer = 0;

        int factorRenteng = Math.max(1, o.has("factor_renteng")
                ? o.optInt("factor_renteng", SalesUnit.defaultFactor(SalesUnit.RENTENG))
                : SalesUnit.defaultFactor(SalesUnit.RENTENG));
        int factorPak = Math.max(1, o.has("factor_pak")
                ? o.optInt("factor_pak", SalesUnit.defaultFactor(SalesUnit.PAK))
                : SalesUnit.defaultFactor(SalesUnit.PAK));
        int factorKarton = Math.max(1, o.has("factor_karton")
                ? o.optInt("factor_karton", SalesUnit.defaultFactor(SalesUnit.KARTON))
                : SalesUnit.defaultFactor(SalesUnit.KARTON));

        double priceRenteng = o.has("price_renteng")
                ? o.optDouble("price_renteng", priceEcer * factorRenteng)
                : priceEcer * factorRenteng;
        double pricePak = o.has("price_pak")
                ? o.optDouble("price_pak", priceEcer * factorPak)
                : priceEcer * factorPak;
        double priceKarton = o.has("price_karton")
                ? o.optDouble("price_karton", priceEcer * factorKarton)
                : priceEcer * factorKarton;

        return new Product(
                id,
                name,
                barcode,
                priceEcer,
                costPrice,
                stock,
                unitName,
                trackStock,
                priceEcer,
                Math.max(0, priceRenteng),
                Math.max(0, pricePak),
                Math.max(0, priceKarton),
                factorRenteng,
                factorPak,
                factorKarton
        );
    }

    private static boolean isMissingTierColumnError(Exception error) {
        String msg = error == null || error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        return msg.contains("price_ecer")
                || msg.contains("barcode")
                || msg.contains("cost_price")
                || msg.contains("unit_name")
                || msg.contains("track_stock")
                || msg.contains("price_renteng")
                || msg.contains("price_pak")
                || msg.contains("price_karton")
                || msg.contains("factor_renteng")
                || msg.contains("factor_pak")
                || msg.contains("factor_karton")
                || msg.contains("column");
    }
}
