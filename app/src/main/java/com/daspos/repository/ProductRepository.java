package com.daspos.repository;

import android.content.Context;

import com.daspos.db.AppDatabase;
import com.daspos.db.entity.ProductEntity;
import com.daspos.model.CartItem;
import com.daspos.model.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ProductRepository {
    public static List<Product> getAll(Context context) {
        List<Product> models = new ArrayList<>();
        for (ProductEntity e : AppDatabase.getInstance(context).productDao().getAll()) {
            models.add(new Product(e.id, e.name, e.price, e.stock));
        }
        return models;
    }

    public static Product getById(Context context, String id) {
        ProductEntity e = AppDatabase.getInstance(context).productDao().getById(id);
        if (e == null) return null;
        return new Product(e.id, e.name, e.price, e.stock);
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

    public static void add(Context context, String name, double price, int stock) {
        AppDatabase.getInstance(context).productDao()
                .insert(new ProductEntity(UUID.randomUUID().toString(), name, price, stock));
    }

    public static void update(Context context, Product product) {
        AppDatabase.getInstance(context).productDao()
                .update(new ProductEntity(product.getId(), product.getName(), product.getPrice(), product.getStock()));
    }

    public static void delete(Context context, String productId) {
        AppDatabase.getInstance(context).productDao().deleteById(productId);
    }

    public static boolean hasEnoughStock(Context context, List<CartItem> items) {
        for (CartItem item : items) {
            Product p = getById(context, item.getProduct().getId());
            if (p == null || p.getStock() < item.getQty()) return false;
        }
        return true;
    }

    public static void reduceStock(Context context, List<CartItem> items) {
        for (CartItem item : items) {
            ProductEntity p = AppDatabase.getInstance(context).productDao().getById(item.getProduct().getId());
            if (p != null) {
                p.stock = Math.max(0, p.stock - item.getQty());
                AppDatabase.getInstance(context).productDao().update(p);
            }
        }
    }
}
