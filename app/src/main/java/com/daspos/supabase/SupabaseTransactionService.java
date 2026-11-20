package com.daspos.supabase;

import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.TransactionRecord;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SupabaseTransactionService {
    private static final Pattern TRX_NO_DATE_PREFIX = Pattern.compile("^\\d{6}.*$");
    private static final Pattern ISO_DATE_TIME_WITH_OPTIONAL_OFFSET = Pattern.compile(
            "^(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}(?::?\\d{2})?)?$"
    );

    private static final String SELECT_WITH_ITEMS =
            "id,trx_no,trx_at,total,pay,change_amount,discount,transaction_items(product_id,product_name,price,qty)";
    private static final String SELECT_HEADERS =
            "id,trx_no,trx_at,total,pay,change_amount,discount";

    private SupabaseTransactionService() {
    }

    public static String save(
            String outletId,
            String cashierId,
            String accessToken,
            List<CartItem> items,
            double total,
            double discount,
            double pay,
            double change,
            String clientTransactionId
    ) throws Exception {
        JSONArray itemRows = new JSONArray();
        for (CartItem item : items) {
            if (item == null || item.getProduct() == null) continue;
            if (!item.getProduct().isTrackStock()) continue;
            String productId = item.getProduct().getId();
            if (productId == null || productId.trim().isEmpty()) continue;
            JSONObject row = new JSONObject();
            row.put("product_id", productId);
            row.put("qty", item.getBaseQty());
            // Actual unit price charged — lets the RPC store tier prices
            // (RENTENG/PAK/KARTON) instead of the product's base ecer price.
            row.put("price", item.getUnitPrice());
            itemRows.put(row);
        }

        JSONObject payload = new JSONObject();
        payload.put("p_outlet_id", outletId);
        payload.put("p_cashier_id", cashierId);
        payload.put("p_total", total);
        payload.put("p_pay", pay);
        payload.put("p_change_amount", change);
        payload.put("p_discount", discount);
        payload.put("p_device_id", "android-app");
        payload.put("p_legacy_id", clientTransactionId == null ? "" : clientTransactionId.trim());
        payload.put("p_items", itemRows);

        String rpcUrl = SupabaseConfig.getUrl() + "/rest/v1/rpc/create_pos_transaction";
        JSONObject result;
        try {
            result = requestObject("POST", rpcUrl, accessToken, payload.toString());
        } catch (Exception e) {
            // Remote DB may not have run migration 12 yet (no p_discount param).
            if (!isMissingDiscountParamError(e)) throw e;
            payload.remove("p_discount");
            result = requestObject("POST", rpcUrl, accessToken, payload.toString());
        }
        // RPC returns the canonical remote row id under "transaction_id"
        // (fresh insert) or "id" (idempotent replay of p_legacy_id). The local
        // row must be rekeyed to this UUID — trx_no is only the receipt number
        // and never matches transactions.id. trx_no is kept as a last-resort
        // fallback for older RPC deployments that do not return the id.
        String remoteId = result.optString("transaction_id", "");
        if (remoteId == null || remoteId.trim().isEmpty()) {
            remoteId = result.optString("id", "");
        }
        if (remoteId == null || remoteId.trim().isEmpty()) {
            remoteId = result.optString("trx_no", "");
        }
        if (remoteId == null || remoteId.trim().isEmpty()) {
            throw new IllegalStateException("RPC create_pos_transaction returned no transaction id");
        }
        return remoteId.trim();
    }

    public static List<TransactionRecord> getAll(String outletId, String accessToken) throws Exception {
        JSONArray arr = new JSONArray();
        int pageSize = 100;
        int offset = 0;
        while (true) {
            String endpoint = SupabaseConfig.getUrl()
                    + "/rest/v1/transactions?outlet_id=eq." + encode(outletId)
                    + "&select=" + SELECT_WITH_ITEMS
                    + "&order=trx_at.desc"
                    + "&limit=" + pageSize
                    + "&offset=" + offset;
            JSONArray page = requestSelect(endpoint, accessToken);
            for (int i = 0; i < page.length(); i++) arr.put(page.getJSONObject(i));
            if (page.length() == 0) break;
            offset += page.length();
        }
        return toTransactionRecords(arr);
    }

    public static List<TransactionRecord> getSince(String outletId, String accessToken, long sinceEpochMillis) throws Exception {
        SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String sinceIso = utcFormat.format(new Date(sinceEpochMillis));
        JSONArray arr = new JSONArray();
        int pageSize = 100;
        int offset = 0;
        while (true) {
            String endpoint = SupabaseConfig.getUrl()
                    + "/rest/v1/transactions?outlet_id=eq." + encode(outletId)
                    + "&trx_at=gt." + encode(sinceIso)
                    + "&select=" + SELECT_WITH_ITEMS
                    + "&order=trx_at.asc,id.asc"
                    + "&limit=" + pageSize
                    + "&offset=" + offset;
            JSONArray page = requestSelect(endpoint, accessToken);
            for (int i = 0; i < page.length(); i++) arr.put(page.getJSONObject(i));
            if (page.length() == 0) break;
            offset += page.length();
        }
        return toTransactionRecords(arr);
    }

    private static List<TransactionRecord> toTransactionRecords(JSONArray arr) throws Exception {
        List<TransactionRecord> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject row = arr.getJSONObject(i);
            String transactionId = toDisplayTransactionId(row);
            List<CartItem> items = parseItems(row.optJSONArray("transaction_items"));
            DisplayDateTime dt = toDisplayDateTime(row.optString("trx_at", ""), row.optString("trx_no", ""));
            list.add(new TransactionRecord(
                    transactionId,
                    dt.date,
                    dt.time,
                    row.optDouble("total", 0),
                    row.optDouble("discount", 0),
                    row.optDouble("pay", 0),
                    row.optDouble("change_amount", 0),
                    items
            ));
        }
        return list;
    }

    public static List<TransactionRecord> getForReportPeriod(
            String outletId,
            String accessToken,
            java.util.Calendar selectedCalendar,
            boolean monthly
    ) throws Exception {
        List<TransactionRecord> allHeaders = getAllTransactionHeaders(outletId, accessToken);
        return filterRecordsForPeriod(allHeaders, selectedCalendar, monthly);
    }

    private static List<TransactionRecord> getAllTransactionHeaders(String outletId, String accessToken) throws Exception {
        JSONArray arr = new JSONArray();
        int pageSize = 200;
        int offset = 0;
        while (true) {
            String endpoint = SupabaseConfig.getUrl()
                    + "/rest/v1/transactions?outlet_id=eq." + encode(outletId)
                    + "&select=" + SELECT_HEADERS
                    + "&order=trx_at.desc,id.desc"
                    + "&limit=" + pageSize
                    + "&offset=" + offset;
            JSONArray page = requestSelect(endpoint, accessToken);
            for (int i = 0; i < page.length(); i++) arr.put(page.getJSONObject(i));
            if (page.length() == 0) break;
            offset += page.length();
        }
        return toTransactionRecordsWithoutItems(arr);
    }

    private static List<TransactionRecord> filterRecordsForPeriod(
            List<TransactionRecord> records,
            java.util.Calendar selectedCalendar,
            boolean monthly
    ) {
        List<TransactionRecord> out = new ArrayList<>();
        String dailyPrefix = new SimpleDateFormat("yyMMdd", Locale.US).format(selectedCalendar.getTime());
        String monthlyPrefix = new SimpleDateFormat("yyMM", Locale.US).format(selectedCalendar.getTime());

        for (TransactionRecord t : records) {
            String trxNo = t.getId() == null ? "" : t.getId().trim();
            boolean byTrxNo = monthly
                    ? trxNo.startsWith(monthlyPrefix)
                    : trxNo.startsWith(dailyPrefix);
            if (byTrxNo) {
                out.add(t);
                continue;
            }

            Calendar trxCal = parseDisplayCalendar(t.getDate(), t.getTime());
            if (trxCal == null) continue;
            boolean byDate = monthly
                    ? trxCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR)
                    && trxCal.get(Calendar.MONTH) == selectedCalendar.get(Calendar.MONTH)
                    : trxCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR)
                    && trxCal.get(Calendar.MONTH) == selectedCalendar.get(Calendar.MONTH)
                    && trxCal.get(Calendar.DAY_OF_MONTH) == selectedCalendar.get(Calendar.DAY_OF_MONTH);
            if (byDate) out.add(t);
        }
        return out;
    }

    private static List<TransactionRecord> toTransactionRecordsWithoutItems(JSONArray arr) {
        List<TransactionRecord> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject row = arr.getJSONObject(i);
                DisplayDateTime dt = toDisplayDateTime(row.optString("trx_at", ""), row.optString("trx_no", ""));
                list.add(new TransactionRecord(
                        toDisplayTransactionId(row),
                        dt.date,
                        dt.time,
                        row.optDouble("total", 0),
                        row.optDouble("discount", 0),
                        row.optDouble("pay", 0),
                        row.optDouble("change_amount", 0),
                        new ArrayList<>()
                ));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private static Calendar parseDisplayCalendar(String date, String time) {
        try {
            Date parsed = new SimpleDateFormat("dd MMM yyyy HH:mm", new Locale("id", "ID")).parse(date + " " + time);
            if (parsed == null) return null;
            Calendar cal = Calendar.getInstance();
            cal.setTime(parsed);
            return cal;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static TransactionRecord getById(String outletId, String accessToken, String transactionId) throws Exception {
        JSONObject row = getTransactionRowByRef(outletId, accessToken, transactionId);
        if (row == null) return null;
        List<CartItem> items = parseItems(row.optJSONArray("transaction_items"));
        DisplayDateTime dt = toDisplayDateTime(row.optString("trx_at", ""), row.optString("trx_no", ""));
        return new TransactionRecord(
                toDisplayTransactionId(row),
                dt.date,
                dt.time,
                row.optDouble("total", 0),
                row.optDouble("discount", 0),
                row.optDouble("pay", 0),
                row.optDouble("change_amount", 0),
                items
        );
    }

    public static boolean deleteById(String outletId, String accessToken, String transactionId) throws Exception {
        JSONObject row = getTransactionRowByRef(outletId, accessToken, transactionId);
        if (row == null) return false;
        String internalId = row.optString("id", "");
        if (internalId.isEmpty()) return false;
        String endpoint = SupabaseConfig.getUrl()
                + "/rest/v1/transactions?id=eq." + encode(internalId)
                + "&outlet_id=eq." + encode(outletId);
        request("DELETE", endpoint, accessToken, null);
        return true;
    }

    private static JSONObject getTransactionRowByRef(String outletId, String accessToken, String transactionRef) throws Exception {
        if (isUuid(transactionRef)) {
            String byId = SupabaseConfig.getUrl()
                    + "/rest/v1/transactions?id=eq." + encode(transactionRef)
                    + "&outlet_id=eq." + encode(outletId)
                    + "&select=" + SELECT_WITH_ITEMS + "&limit=1";
            JSONArray arr = requestSelect(byId, accessToken);
            if (arr.length() > 0) return arr.getJSONObject(0);
        }

        String byNo = SupabaseConfig.getUrl()
                + "/rest/v1/transactions?trx_no=eq." + encode(transactionRef)
                + "&outlet_id=eq." + encode(outletId)
                + "&select=" + SELECT_WITH_ITEMS + "&limit=1";
        JSONArray arrNo = requestSelect(byNo, accessToken);
        if (arrNo.length() == 0) return null;
        return arrNo.getJSONObject(0);
    }

    private static List<CartItem> parseItems(JSONArray arr) {
        List<CartItem> items = new ArrayList<>();
        if (arr == null) return items;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject item = arr.getJSONObject(i);
                items.add(new CartItem(
                        new Product(
                                item.optString("product_id", ""),
                                item.optString("product_name", ""),
                                item.optDouble("price", 0),
                                0
                        ),
                        item.optInt("qty", 0)
                ));
            } catch (Exception ignored) {
            }
        }
        return items;
    }

    private static DisplayDateTime toDisplayDateTime(String trxAtIso, String trxNo) {
        Date parsed = parseIsoDate(trxAtIso);
        if (parsed != null) {
            String date = dateFromTrxNoOrParsed(trxNo, parsed);
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsed);
            return new DisplayDateTime(date, time);
        }

        Date now = new Date();
        String fallbackDate = dateFromTrxNoOrParsed(trxNo, now);
        String fallbackTime = extractTimeFromRaw(trxAtIso);
        return new DisplayDateTime(fallbackDate, fallbackTime);
    }

    private static String dateFromTrxNoOrParsed(String trxNo, Date parsed) {
        if (trxNo != null && TRX_NO_DATE_PREFIX.matcher(trxNo).matches()) {
            try {
                String yymmdd = trxNo.substring(0, 6);
                Date fromTrxNo = new SimpleDateFormat("yyMMdd", Locale.US).parse(yymmdd);
                if (fromTrxNo != null) {
                    return new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID")).format(fromTrxNo);
                }
            } catch (Exception ignored) {
            }
        }
        return new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID")).format(parsed);
    }

    private static Date parseIsoDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = normalizeIsoDate(value);
        Date withOffset = parseIsoDateWithOffset(normalized);
        if (withOffset != null) return withOffset;

        return parseLocalDateTimeFallback(normalized);
    }

    private static Date parseIsoDateWithOffset(String value) {
        Matcher matcher = ISO_DATE_TIME_WITH_OPTIONAL_OFFSET.matcher(value);
        if (!matcher.matches()) return null;

        int year = parseIntSafe(matcher.group(1), -1);
        int month = parseIntSafe(matcher.group(2), -1);
        int day = parseIntSafe(matcher.group(3), -1);
        int hour = parseIntSafe(matcher.group(4), -1);
        int minute = parseIntSafe(matcher.group(5), -1);
        int second = parseIntSafe(matcher.group(6), -1);

        if (year < 0 || month < 1 || day < 1 || hour < 0 || minute < 0 || second < 0) return null;

        int millis = 0;
        String fraction = matcher.group(7);
        if (fraction != null && !fraction.isEmpty()) {
            String fracMillis = (fraction + "000").substring(0, 3);
            millis = parseIntSafe(fracMillis, 0);
        }

        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        utc.clear();
        utc.setLenient(false);
        utc.set(Calendar.YEAR, year);
        utc.set(Calendar.MONTH, month - 1);
        utc.set(Calendar.DAY_OF_MONTH, day);
        utc.set(Calendar.HOUR_OF_DAY, hour);
        utc.set(Calendar.MINUTE, minute);
        utc.set(Calendar.SECOND, second);
        utc.set(Calendar.MILLISECOND, millis);

        long epochMillis;
        try {
            epochMillis = utc.getTimeInMillis();
        } catch (Exception ignored) {
            return null;
        }

        String offsetRaw = matcher.group(8);
        if (offsetRaw != null && !offsetRaw.isEmpty() && !"Z".equalsIgnoreCase(offsetRaw)) {
            int sign = offsetRaw.charAt(0) == '-' ? -1 : 1;
            String raw = offsetRaw.substring(1).replace(":", "");
            int offsetHour = raw.length() >= 2 ? parseIntSafe(raw.substring(0, 2), 0) : 0;
            int offsetMinute = raw.length() >= 4 ? parseIntSafe(raw.substring(2, 4), 0) : 0;
            int totalOffsetMinutes = sign * (offsetHour * 60 + offsetMinute);
            epochMillis -= totalOffsetMinutes * 60_000L;
        }

        return new Date(epochMillis);
    }

    private static Date parseLocalDateTimeFallback(String value) {
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss.SSS",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                sdf.setLenient(false);
                return sdf.parse(value);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static int parseIntSafe(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String extractTimeFromRaw(String rawDateTime) {
        if (rawDateTime == null || rawDateTime.trim().isEmpty()) return "--:--";
        Matcher matcher = ISO_DATE_TIME_WITH_OPTIONAL_OFFSET.matcher(normalizeIsoDate(rawDateTime));
        if (!matcher.matches()) return "--:--";

        String hour = matcher.group(4);
        String minute = matcher.group(5);
        if (hour == null || minute == null) return "--:--";
        return hour + ":" + minute;
    }

    private static String normalizeIsoDate(String value) {
        String normalized = value.trim();

        if (normalized.indexOf(' ') > 0 && normalized.indexOf('T') < 0) {
            normalized = normalized.replace(' ', 'T');
        }

        // Supabase can emit microseconds; truncate to millis for SimpleDateFormat patterns.
        int dotIndex = normalized.indexOf('.');
        if (dotIndex > 0) {
            int fractionStart = dotIndex + 1;
            int fractionEnd = fractionStart;
            while (fractionEnd < normalized.length() && Character.isDigit(normalized.charAt(fractionEnd))) {
                fractionEnd++;
            }
            int fractionLength = fractionEnd - fractionStart;
            if (fractionLength > 3) {
                normalized = normalized.substring(0, fractionStart + 3) + normalized.substring(fractionEnd);
            }
        }

        // Normalize timezone suffixes like +00 -> +00:00 and +0700 -> +07:00.
        if (normalized.matches(".*[+-]\\d{2}$")) {
            normalized = normalized + ":00";
        } else if (normalized.matches(".*[+-]\\d{4}$")) {
            String tz = normalized.substring(normalized.length() - 5);
            normalized = normalized.substring(0, normalized.length() - 5)
                    + tz.substring(0, 3)
                    + ":"
                    + tz.substring(3);
        }

        return normalized;
    }

    private static JSONArray requestSelect(String endpoint, String accessToken) throws Exception {
        try {
            return request("GET", endpoint, accessToken, null);
        } catch (Exception e) {
            // Remote DB may not have run migration 12 yet (no discount column).
            if (!isMissingDiscountColumnError(e)) throw e;
            return request("GET", endpoint.replace(",discount", ""), accessToken, null);
        }
    }

    private static boolean isMissingDiscountColumnError(Exception error) {
        String msg = error == null ? "" : String.valueOf(error.getMessage());
        return msg.contains("discount")
                && (msg.contains("column") || msg.contains("PGRST") || msg.contains("schema cache"));
    }

    private static boolean isMissingDiscountParamError(Exception error) {
        String msg = error == null ? "" : String.valueOf(error.getMessage());
        return msg.contains("p_discount")
                || (msg.contains("Could not find the function") && msg.contains("create_pos_transaction"));
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
            conn.setReadTimeout(30000);
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
                throw new IllegalStateException("Supabase transactions request failed: HTTP " + code + " body=" + body);
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

    private static JSONObject requestObject(String method, String endpoint, String accessToken, String jsonBody) throws Exception {
        JSONArray arr = request(method, endpoint, accessToken, jsonBody);
        if (arr.length() == 0) return new JSONObject();
        return arr.getJSONObject(0);
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

    private static String toDisplayTransactionId(JSONObject row) {
        String trxNo = row.optString("trx_no", "");
        if (trxNo != null && !trxNo.trim().isEmpty()) return trxNo.trim();
        return row.optString("id", "");
    }

    private static boolean isUuid(String value) {
        if (value == null) return false;
        try {
            java.util.UUID.fromString(value.trim());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static final class DisplayDateTime {
        private final String date;
        private final String time;

        private DisplayDateTime(String date, String time) {
            this.date = date;
            this.time = time;
        }
    }
}
