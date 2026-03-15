package com.daspos.repository.legacy;

import android.content.Context;
import android.content.SharedPreferences;

import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.TransactionRecord;
import com.daspos.model.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LegacySeedRepository {
    private static final String PREF = "daspos_data";
    private static final String KEY_PRODUCTS = "products";
    private static final String KEY_USERS = "users";
    private static final String KEY_TRANSACTIONS = "transactions";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<Product> getProducts(Context context) {
        List<Product> list = new ArrayList<>();
        String raw = prefs(context).getString(KEY_PRODUCTS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Product(
                        o.getString("id"),
                        o.getString("name"),
                        o.getDouble("price"),
                        o.getInt("stock")
                ));
            }
        } catch (Exception ignored) { }
        return list;
    }

    public static List<User> getUsers(Context context) {
        List<User> list = new ArrayList<>();
        String raw = prefs(context).getString(KEY_USERS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new User(
                        o.getString("username"),
                        o.getString("role")
                ));
            }
        } catch (Exception ignored) { }
        return list;
    }

    public static List<TransactionRecord> getTransactions(Context context) {
        List<TransactionRecord> list = new ArrayList<>();
        String raw = prefs(context).getString(KEY_TRANSACTIONS, "[]");

        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.getJSONObject(i);
                List<CartItem> items = new ArrayList<>();
                JSONArray itemArray = o.optJSONArray("items");
                if (itemArray != null) {
                    for (int j = 0; j < itemArray.length(); j++) {
                        JSONObject it = itemArray.getJSONObject(j);
                        Product product = new Product(
                                it.optString("productId", UUID.randomUUID().toString()),
                                it.getString("productName"),
                                it.getDouble("price"),
                                0
                        );
                        items.add(new CartItem(product, it.getInt("qty")));
                    }
                }

                list.add(new TransactionRecord(
                        o.getString("id"),
                        o.getString("date"),
                        o.getString("time"),
                        o.getDouble("total"),
                        o.optDouble("pay", 0),
                        o.optDouble("change", 0),
                        items
                ));
            }
        } catch (Exception ignored) { }

        return list;
    }
}
