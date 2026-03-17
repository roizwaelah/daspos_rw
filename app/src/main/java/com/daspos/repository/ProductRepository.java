package com.daspos.repository;

import android.content.Context;

import com.daspos.db.AppDatabase;
import com.daspos.db.entity.ProductEntity;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.shared.util.DbExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ProductRepository {
    public static List<Product> getAll(final Context context) {
        return DbExecutor.runBlocking(() -> {
            List<Product> models = new ArrayList<>();
            for (ProductEntity e : AppDatabase.getInstance(context).productDao().getAll()) {
                models.add(new Product(e.id, e.name, e.price, e.stock));
            }
            return models;
        });
    }

    public static Product getById(final Context context, final String id) {
        return DbExecutor.runBlocking(() -> {
            ProductEntity e = AppDatabase.getInstance(context).productDao().getById(id);
            if (e == null) return null;
            return new Product(e.id, e.name, e.price, e.stock);
        });
    }

    public static List<Product> search(Context context, String query) {
        List<Product> all = getAll(context);
        if (query == null || query.trim().isEmpty()) return new ArrayList<>();
        List<Product> out = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT);
        for (Product p : all) {
            if (p.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(p);
        }
        return out;
    }

    public static void add(final Context context, final String name, final double price, final int stock) {
        DbExecutor.runBlocking(() -> AppDatabase.getInstance(context).productDao()
                .insert(new ProductEntity(UUID.randomUUID().toString(), name, price, stock)));
    }

    public static void update(final Context context, final Product product) {
        DbExecutor.runBlocking(() -> AppDatabase.getInstance(context).productDao()
                .update(new ProductEntity(product.getId(), product.getName(), product.getPrice(), product.getStock())));
    }

    public static void delete(final Context context, final String productId) {
        DbExecutor.runBlocking(() -> AppDatabase.getInstance(context).productDao().deleteById(productId));
    }

    public static boolean hasEnoughStock(Context context, List<CartItem> items) {
        for (CartItem item : items) {
            Product p = getById(context, item.getProduct().getId());
            if (p == null || p.getStock() < item.getQty()) return false;
        }
        return true;
    }

    public static void reduceStock(final Context context, final List<CartItem> items) {
        DbExecutor.runBlocking(() -> {
            for (CartItem item : items) {
                ProductEntity p = AppDatabase.getInstance(context).productDao().getById(item.getProduct().getId());
                if (p != null) {
                    p.stock = Math.max(0, p.stock - item.getQty());
                    AppDatabase.getInstance(context).productDao().update(p);
                }
            }
        });
    }


    public static void getAllAsync(final Context context, final DbExecutor.SuccessCallback<List<Product>> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getAll(context), onSuccess, onError);
    }

    public static void getByIdAsync(final Context context, final String id, final DbExecutor.SuccessCallback<Product> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getById(context, id), onSuccess, onError);
    }

    public static void searchAsync(final Context context, final String query, final DbExecutor.SuccessCallback<List<Product>> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> search(context, query), onSuccess, onError);
    }

    public static void addAsync(final Context context, final String name, final double price, final int stock, final Runnable onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> {
            add(context, name, price, stock);
            return null;
        }, ignored -> {
            if (onSuccess != null) onSuccess.run();
        }, onError);
    }

    public static void updateAsync(final Context context, final Product product, final Runnable onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> {
            update(context, product);
            return null;
        }, ignored -> {
            if (onSuccess != null) onSuccess.run();
        }, onError);
    }

    public static void deleteAsync(final Context context, final String productId, final Runnable onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> {
            delete(context, productId);
            return null;
        }, ignored -> {
            if (onSuccess != null) onSuccess.run();
        }, onError);
    }

    public static void hasEnoughStockAsync(final Context context, final List<CartItem> items, final DbExecutor.SuccessCallback<Boolean> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> hasEnoughStock(context, items), onSuccess, onError);
    }
}
