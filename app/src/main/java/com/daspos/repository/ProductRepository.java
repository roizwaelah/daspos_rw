package com.daspos.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

import com.daspos.db.AppDatabase;
import com.daspos.db.entity.ProductEntity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.NetworkUtils;
import com.daspos.shared.util.RestoreStateStore;
import com.daspos.supabase.SupabaseConfig;
import com.daspos.supabase.SupabaseProductService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ProductRepository {
    private static final String CACHE_PREF = "supabase_cache_meta";
    private static final String KEY_PRODUCTS_LAST_REFRESH = "products_last_refresh";
    private static final String KEY_PRODUCTS_CACHE_OUTLET_ID = "products_cache_outlet_id";
    private static final long PRODUCTS_REFRESH_INTERVAL_MS = 30_000L;

    public static List<Product> getAll(final Context context) {
        return DbExecutor.runBlocking(() -> getAllInternal(context));
    }

    public static Product getById(final Context context, final String id) {
        return DbExecutor.runBlocking(() -> getByIdInternal(context, id));
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
        DbExecutor.runBlocking(() -> {
            String normalizedName = normalizeProductName(name);
            if (shouldUseSupabase(context)) {
                try {
                    if (NetworkUtils.isOnline(context)) {
                        SupabaseProductService.add(getOutletId(context), getAccessToken(context), normalizedName, price, stock);
                        refreshProductsNow(context);
                    } else {
                        String localId = UUID.randomUUID().toString();
                        Product local = new Product(localId, normalizedName, price, stock);
                        upsertLocalProduct(context, local);
                        PendingSyncStore.enqueueProductAdd(context, localId, normalizedName, price, stock);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return;
            }
            AppDatabase.getInstance(context).productDao()
                    .insert(new ProductEntity(UUID.randomUUID().toString(), normalizedName, price, stock));
        });
    }

    public static void update(final Context context, final Product product) {
        DbExecutor.runBlocking(() -> {
            Product normalizedProduct = new Product(
                    product.getId(),
                    normalizeProductName(product.getName()),
                    product.getPrice(),
                    product.getStock()
            );
            if (shouldUseSupabase(context)) {
                try {
                    if (NetworkUtils.isOnline(context)) {
                        syncPendingProductOpsNow(context);
                        SupabaseProductService.update(getOutletId(context), getAccessToken(context), normalizedProduct);
                    } else {
                        PendingSyncStore.enqueueProductUpdate(
                                context,
                                normalizedProduct.getId(),
                                normalizedProduct.getName(),
                                normalizedProduct.getPrice(),
                                normalizedProduct.getStock()
                        );
                    }
                    upsertLocalProduct(context, normalizedProduct);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return;
            }
            AppDatabase.getInstance(context).productDao()
                    .update(new ProductEntity(
                            normalizedProduct.getId(),
                            normalizedProduct.getName(),
                            normalizedProduct.getPrice(),
                            normalizedProduct.getStock()
                    ));
        });
    }

    public static void delete(final Context context, final String productId) {
        DbExecutor.runBlocking(() -> {
            if (shouldUseSupabase(context)) {
                try {
                    if (NetworkUtils.isOnline(context)) {
                        syncPendingProductOpsNow(context);
                        SupabaseProductService.delete(getOutletId(context), getAccessToken(context), productId);
                    } else {
                        PendingSyncStore.enqueueProductDelete(context, productId);
                    }
                    AppDatabase.getInstance(context).productDao().deleteById(productId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return;
            }
            AppDatabase.getInstance(context).productDao().deleteById(productId);
        });
    }

    public static boolean hasEnoughStock(Context context, List<CartItem> items) {
        if (shouldUseSupabase(context)) {
            return DbExecutor.runBlocking(() -> SupabaseProductService.hasEnoughStock(getOutletId(context), getAccessToken(context), items));
        }
        for (CartItem item : items) {
            Product p = getById(context, item.getProduct().getId());
            if (p == null || p.getStock() < item.getQty()) return false;
        }
        return true;
    }

    public static void reduceStock(final Context context, final List<CartItem> items) {
        DbExecutor.runBlocking(() -> {
            if (shouldUseSupabase(context)) {
                try {
                    SupabaseProductService.reduceStock(getOutletId(context), getAccessToken(context), items);
                    applyLocalStockDelta(context, items, -1);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return;
            }
            for (CartItem item : items) {
                ProductEntity p = AppDatabase.getInstance(context).productDao().getById(item.getProduct().getId());
                if (p != null) {
                    p.stock = Math.max(0, p.stock - item.getQty());
                    AppDatabase.getInstance(context).productDao().update(p);
                }
            }
        });
    }

    public static void increaseStock(final Context context, final List<CartItem> items) {
        DbExecutor.runBlocking(() -> {
            if (shouldUseSupabase(context)) {
                try {
                    SupabaseProductService.increaseStock(getOutletId(context), getAccessToken(context), items);
                    applyLocalStockDelta(context, items, 1);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return;
            }
            for (CartItem item : items) {
                ProductEntity p = AppDatabase.getInstance(context).productDao().getById(item.getProduct().getId());
                if (p != null) {
                    p.stock = p.stock + item.getQty();
                    AppDatabase.getInstance(context).productDao().update(p);
                }
            }
        });
    }

    private static List<Product> getAllInternal(Context context) throws Exception {
        if (shouldUseSupabase(context)) {
            List<Product> cached = getAllLocalProducts(context);
            triggerProductsBackgroundRefreshIfNeeded(context);
            if (!cached.isEmpty()) return cached;
            syncPendingProductOpsNow(context);
            List<Product> remote = SupabaseProductService.getAll(getOutletId(context), getAccessToken(context));
            replaceLocalProductCache(context, remote);
            markProductsRefreshed(context);
            return remote;
        }
        return getAllLocalProducts(context);
    }

    private static Product getByIdInternal(Context context, String id) throws Exception {
        if (shouldUseSupabase(context)) {
            Product local = getByIdLocalProduct(context, id);
            if (!NetworkUtils.isOnline(context)) return local;
            try {
                Product remote = SupabaseProductService.getById(getOutletId(context), getAccessToken(context), id);
                if (remote != null) {
                    upsertLocalProduct(context, remote);
                    return remote;
                }
            } catch (Exception ignored) {
                // Fallback to local cache when remote check fails.
            }
            return local;
        }
        return getByIdLocalProduct(context, id);
    }

    private static boolean shouldUseSupabase(Context context) {
        boolean active = SupabaseConfig.isConfigured()
                && "supabase".equalsIgnoreCase(AuthSessionStore.getSource(context))
                && !getAccessToken(context).isEmpty()
                && !getOutletId(context).isEmpty();
        if (active) {
            ensureCacheBoundToCurrentOutlet(context, getOutletId(context));
        }
        return active;
    }

    private static String getAccessToken(Context context) {
        String token = AuthSessionStore.getAccessToken(context);
        return token == null ? "" : token.trim();
    }

    private static String getOutletId(Context context) {
        String outletId = AuthSessionStore.getOutletId(context);
        return outletId == null ? "" : outletId.trim();
    }

    private static String normalizeProductName(String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }

    private static List<Product> getAllLocalProducts(Context context) {
        List<Product> models = new ArrayList<>();
        for (ProductEntity e : AppDatabase.getInstance(context).productDao().getAll()) {
            models.add(new Product(e.id, e.name, e.price, e.stock));
        }
        return models;
    }

    private static Product getByIdLocalProduct(Context context, String id) {
        ProductEntity e = AppDatabase.getInstance(context).productDao().getById(id);
        if (e == null) return null;
        return new Product(e.id, e.name, e.price, e.stock);
    }

    private static void replaceLocalProductCache(Context context, List<Product> products) {
        AppDatabase db = AppDatabase.getInstance(context);
        db.runInTransaction(() -> {
            db.productDao().deleteAll();
            List<ProductEntity> entities = new ArrayList<>();
            for (Product p : products) {
                entities.add(new ProductEntity(p.getId(), p.getName(), p.getPrice(), p.getStock()));
            }
            db.productDao().insertAll(entities);
        });
    }

    private static void upsertLocalProduct(Context context, Product product) {
        AppDatabase.getInstance(context).productDao()
                .insert(new ProductEntity(product.getId(), product.getName(), product.getPrice(), product.getStock()));
    }

    private static void applyLocalStockDelta(Context context, List<CartItem> items, int direction) {
        for (CartItem item : items) {
            ProductEntity p = AppDatabase.getInstance(context).productDao().getById(item.getProduct().getId());
            if (p == null) continue;
            int delta = item.getQty() * direction;
            p.stock = direction < 0 ? Math.max(0, p.stock + delta) : p.stock + delta;
            AppDatabase.getInstance(context).productDao().update(p);
        }
    }

    static void applyLocalCheckoutStockReduction(Context context, List<CartItem> items) {
        applyLocalStockDelta(context, items, -1);
    }

    private static void triggerProductsBackgroundRefreshIfNeeded(Context context) {
        if (!shouldUseSupabase(context)) return;
        if (RestoreStateStore.shouldDelaySupabaseSync(context)) return;
        if (!NetworkUtils.isOnline(context)) return;
        long now = System.currentTimeMillis();
        long last = prefs(context).getLong(KEY_PRODUCTS_LAST_REFRESH, 0L);
        if (now - last < PRODUCTS_REFRESH_INTERVAL_MS) return;

        prefs(context).edit().putLong(KEY_PRODUCTS_LAST_REFRESH, now).apply();
        DbExecutor.runAsync(() -> {
            try {
                syncPendingProductOpsNow(context);
                refreshProductsNow(context);
                markProductsRefreshed(context);
            } catch (Exception ignored) {
            }
        });
    }

    private static void refreshProductsNow(Context context) throws Exception {
        List<Product> remote = SupabaseProductService.getAll(getOutletId(context), getAccessToken(context));
        replaceLocalProductCache(context, remote);
    }

    private static void markProductsRefreshed(Context context) {
        prefs(context).edit().putLong(KEY_PRODUCTS_LAST_REFRESH, System.currentTimeMillis()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(CACHE_PREF, Context.MODE_PRIVATE);
    }

    private static void ensureCacheBoundToCurrentOutlet(Context context, String outletId) {
        if (outletId == null || outletId.trim().isEmpty()) return;
        SharedPreferences preferences = prefs(context);
        String cachedOutletId = preferences.getString(KEY_PRODUCTS_CACHE_OUTLET_ID, "");
        if (outletId.equals(cachedOutletId)) return;

        Runnable clearTask = () -> {
            AppDatabase.getInstance(context).productDao().deleteAll();
            PendingSyncStore.clearPendingProductOps(context);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            DbExecutor.runBlocking(clearTask);
        } else {
            clearTask.run();
        }
        preferences.edit()
                .putString(KEY_PRODUCTS_CACHE_OUTLET_ID, outletId)
                .putLong(KEY_PRODUCTS_LAST_REFRESH, 0L)
                .apply();
    }

    public static void triggerSyncIfPossible(Context context) {
        DbExecutor.runAsync(() -> {
            if (!shouldUseSupabase(context)) return;
            if (RestoreStateStore.shouldDelaySupabaseSync(context)) return;
            if (!NetworkUtils.isOnline(context)) return;
            try {
                syncPendingProductOpsNow(context);
                refreshProductsNow(context);
                markProductsRefreshed(context);
            } catch (Exception ignored) {
            }
        });
    }

    private static void syncPendingProductOpsNow(Context context) throws Exception {
        if (!shouldUseSupabase(context)) return;
        if (!NetworkUtils.isOnline(context)) return;
        List<PendingSyncStore.ProductSyncOp> ops = PendingSyncStore.getPendingProductOps(context);
        if (ops.isEmpty()) return;

        for (PendingSyncStore.ProductSyncOp op : ops) {
            if ("add".equals(op.op)) {
                SupabaseProductService.addWithId(
                        getOutletId(context),
                        getAccessToken(context),
                        op.id,
                        op.name,
                        op.price,
                        op.stock
                );
            } else if ("update".equals(op.op)) {
                try {
                    SupabaseProductService.update(
                            getOutletId(context),
                            getAccessToken(context),
                            new Product(op.id, op.name, op.price, op.stock)
                    );
                } catch (Exception e) {
                    SupabaseProductService.addWithId(
                            getOutletId(context),
                            getAccessToken(context),
                            op.id,
                            op.name,
                            op.price,
                            op.stock
                    );
                }
            } else if ("delete".equals(op.op)) {
                SupabaseProductService.delete(getOutletId(context), getAccessToken(context), op.id);
            }
            PendingSyncStore.removeFirstProductOp(context);
        }
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
