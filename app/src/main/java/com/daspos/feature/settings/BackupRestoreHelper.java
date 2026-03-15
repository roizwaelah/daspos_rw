package com.daspos.feature.settings;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.daspos.db.AppDatabase;
import com.daspos.db.entity.ProductEntity;
import com.daspos.db.entity.TransactionEntity;
import com.daspos.db.entity.TransactionItemEntity;
import com.daspos.db.entity.UserEntity;
import com.daspos.feature.printer.PrinterConfigStore;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.TransactionRecord;
import com.daspos.model.User;
import com.daspos.repository.ProductRepository;
import com.daspos.repository.TransactionRepository;
import com.daspos.repository.UserRepository;
import com.daspos.shared.util.DbExecutor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BackupRestoreHelper {
    private static final String TAG = "BackupRestoreHelper";

    public static boolean backup(Context context, Uri uri) {
        try {
            JSONObject root = new JSONObject();

            JSONObject meta = new JSONObject();
            meta.put("formatVersion", 2);
            meta.put("app", "DasPos");
            meta.put("createdAt", System.currentTimeMillis());
            root.put("meta", meta);

            JSONObject printer = new JSONObject();
            printer.put("type", PrinterConfigStore.getType(context));
            printer.put("btName", PrinterConfigStore.getBluetoothName(context));
            printer.put("btAddress", PrinterConfigStore.getBluetoothAddress(context));
            printer.put("ip", PrinterConfigStore.getIp(context));
            printer.put("port", PrinterConfigStore.getPort(context));
            root.put("printer", printer);

            JSONArray products = new JSONArray();
            for (Product p : ProductRepository.getAll(context)) {
                JSONObject o = new JSONObject();
                o.put("id", p.getId());
                o.put("name", p.getName());
                o.put("price", p.getPrice());
                o.put("stock", p.getStock());
                products.put(o);
            }
            root.put("products", products);

            JSONArray users = new JSONArray();
            for (User u : UserRepository.getAll(context)) {
                JSONObject o = new JSONObject();
                o.put("username", u.getUsername());
                o.put("role", u.getRole());
                o.put("passwordHash", UserRepository.getPasswordHash(context, u.getUsername()));
                users.put(o);
            }
            root.put("users", users);

            JSONArray transactions = new JSONArray();
            for (TransactionRecord t : TransactionRepository.getAll(context)) {
                JSONObject o = new JSONObject();
                o.put("id", t.getId());
                o.put("date", t.getDate());
                o.put("time", t.getTime());
                o.put("total", t.getTotal());
                o.put("pay", t.getPay());
                o.put("change", t.getChange());
                JSONArray items = new JSONArray();
                for (CartItem item : t.getItems()) {
                    JSONObject io = new JSONObject();
                    io.put("productId", item.getProduct().getId());
                    io.put("productName", item.getProduct().getName());
                    io.put("price", item.getProduct().getPrice());
                    io.put("qty", item.getQty());
                    items.put(io);
                }
                o.put("items", items);
                transactions.put(o);
            }
            root.put("transactions", transactions);

            OutputStream out = context.getContentResolver().openOutputStream(uri);
            if (out == null) {
                Log.e(TAG, "Backup gagal: output stream null");
                return false;
            }
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            out.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Backup gagal", e);
            return false;
        }
    }

    public static RestoreStatus restore(Context context, Uri uri) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getContentResolver().openInputStream(uri)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject root = new JSONObject(sb.toString());
            if (!root.has("products") || !root.has("users") || !root.has("transactions")) {
                Log.e(TAG, "Restore invalid: key wajib tidak ditemukan");
                return RestoreStatus.INVALID;
            }

            int version = 1;
            if (root.has("meta")) version = root.getJSONObject("meta").optInt("formatVersion", 1);
            if (version > 2) {
                Log.e(TAG, "Restore incompatible version: " + version);
                return RestoreStatus.INCOMPATIBLE_VERSION;
            }

            ParsedRestorePayload payload = parsePayload(root);
            if (payload == null) {
                Log.e(TAG, "Restore invalid: payload parsing gagal");
                return RestoreStatus.INVALID;
            }

            AppDatabase db = AppDatabase.getInstance(context);
            db.runInTransaction(() -> {
                db.clearAllTables();
                db.productDao().insertAll(payload.productEntities);
                db.userDao().insertAll(payload.userEntities);
                for (TransactionEntity tx : payload.transactionEntities) {
                    db.transactionDao().insertTransaction(tx);
                }
                for (List<TransactionItemEntity> items : payload.transactionItemGroups) {
                    db.transactionDao().insertTransactionItems(items);
                }
            });

            if (root.has("printer")) {
                JSONObject printer = root.getJSONObject("printer");
                PrinterConfigStore.save(
                        context,
                        printer.optString("type", "none"),
                        printer.optString("btName", ""),
                        printer.optString("btAddress", ""),
                        printer.optString("ip", ""),
                        printer.optString("port", "")
                );
            }

            return RestoreStatus.SUCCESS;
        } catch (Exception e) {
            Log.e(TAG, "Restore gagal", e);
            return RestoreStatus.INVALID;
        }
    }

    private static ParsedRestorePayload parsePayload(JSONObject root) {
        try {
            ParsedRestorePayload payload = new ParsedRestorePayload();

            JSONArray products = root.getJSONArray("products");
            for (int i = 0; i < products.length(); i++) {
                JSONObject o = products.getJSONObject(i);
                payload.productEntities.add(new ProductEntity(
                        o.getString("id"),
                        o.getString("name"),
                        o.getDouble("price"),
                        o.getInt("stock")
                ));
            }

            JSONArray users = root.getJSONArray("users");
            for (int i = 0; i < users.length(); i++) {
                JSONObject o = users.getJSONObject(i);
                payload.userEntities.add(new UserEntity(
                        o.getString("username"),
                        o.getString("role"),
                        o.optString("passwordHash", "")
                ));
            }

            JSONArray transactions = root.getJSONArray("transactions");
            for (int i = 0; i < transactions.length(); i++) {
                JSONObject o = transactions.getJSONObject(i);
                String id = o.getString("id");
                long ts = System.currentTimeMillis();
                payload.transactionEntities.add(new TransactionEntity(
                        id,
                        o.getString("date"),
                        o.getString("time"),
                        ts,
                        o.getDouble("total"),
                        o.optDouble("pay", 0),
                        o.optDouble("change", 0)
                ));
                JSONArray items = o.getJSONArray("items");
                List<TransactionItemEntity> itemEntities = new ArrayList<>();
                for (int j = 0; j < items.length(); j++) {
                    JSONObject io = items.getJSONObject(j);
                    itemEntities.add(new TransactionItemEntity(
                            UUID.randomUUID().toString(),
                            id,
                            io.optString("productId", ""),
                            io.getString("productName"),
                            io.getDouble("price"),
                            io.getInt("qty")
                    ));
                }
                payload.transactionItemGroups.add(itemEntities);
            }

            return payload;
        } catch (Exception e) {
            Log.e(TAG, "Parse payload restore gagal", e);
            return null;
        }
    }

    public static void resetAll(final Context context) {
        DbExecutor.runBlocking(() -> AppDatabase.getInstance(context).clearAllTables());
    }

    private static class ParsedRestorePayload {
        private final List<ProductEntity> productEntities = new ArrayList<>();
        private final List<UserEntity> userEntities = new ArrayList<>();
        private final List<TransactionEntity> transactionEntities = new ArrayList<>();
        private final List<List<TransactionItemEntity>> transactionItemGroups = new ArrayList<>();
    }

    public enum RestoreStatus {
        SUCCESS,
        INVALID,
        INCOMPATIBLE_VERSION
    }
}
