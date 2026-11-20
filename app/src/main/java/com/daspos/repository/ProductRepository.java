package com.daspos.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.daspos.db.AppDatabase;
import com.daspos.db.entity.ProductEntity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.SalesUnit;
import com.daspos.remote.RemoteDataProvider;
import com.daspos.remote.RemoteDataProviderFactory;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.NetworkUtils;
import com.daspos.shared.util.RestoreStateStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProductRepository {
    private static final String TAG = "ProductRepository";
    private static final String CACHE_PREF = "supabase_cache_meta";
    private static final String KEY_PRODUCTS_LAST_REFRESH = "products_last_refresh";
    private static final String KEY_PRODUCTS_CACHE_OUTLET_ID = "products_cache_outlet_id";
    private static final long PRODUCTS_REFRESH_INTERVAL_MS = 30_000L;
    private static final AtomicBoolean PRODUCTS_SYNC_IN_FLIGHT = new AtomicBoolean(false);

    public static List<Product> getAll(final Context context) {
        return DbExecutor.runBlocking(() -> getAllInternal(context));
    }

    public static Product getById(final Context context, final String id) {
        return DbExecutor.runBlocking(() -> getByIdInternal(context, id));
    }

    public static int getCount(final Context context) {
        return DbExecutor.runBlocking(() -> {
            triggerProductsBackgroundRefreshIfNeeded(context);
            AppDatabase db = AppDatabase.getInstance(context);
            return db.productDao().count();
        });
    }

    // Optimized search - uses DB-level LIKE query
    public static List<Product> searchProducts(final Context context, final String query) {
        return searchProductsSorted(context, query, 0);
    }

    public static List<Product> searchProductsSorted(final Context context, final String query, final int sortPosition) {
        return DbExecutor.runBlocking(() -> {
            triggerProductsBackgroundRefreshIfNeeded(context);
            AppDatabase db = AppDatabase.getInstance(context);
            String q = query == null ? "" : query.trim();
            if (q.isEmpty()) {
                return getAllSorted(context, sortPosition);
            }
            List<ProductEntity> entities;
            switch (sortPosition) {
                case 0:
                case 1:
                    entities = db.productDao().searchProducts(q);
                    break;
                case 2:
                    entities = db.productDao().searchProductsByNameDesc(q);
                    break;
                case 3:
                    entities = db.productDao().searchProductsByPriceAsc(q);
                    break;
                case 4:
                    entities = db.productDao().searchProductsByPriceDesc(q);
                    break;
                case 5:
                    entities = db.productDao().searchProductsByStockDesc(q);
                    break;
                default:
                    entities = db.productDao().searchProducts(q);
                    break;
            }
            return mapEntitiesToModels(context, entities);
        });
    }

    // Optimized sorted queries
    public static List<Product> getAllSorted(final Context context, final int sortPosition) {
        return DbExecutor.runBlocking(() -> {
            triggerProductsBackgroundRefreshIfNeeded(context);
            AppDatabase db = AppDatabase.getInstance(context);
            List<ProductEntity> entities;
            switch (sortPosition) {
                case 0: // Name A-Z
                case 1: // Name A-Z (duplicate)
                    entities = db.productDao().getAllSortedByNameAsc();
                    break;
                case 2: // Name Z-A
                    entities = db.productDao().getAllSortedByNameDesc();
                    break;
                case 3: // Price Low-High
                    entities = db.productDao().getAllSortedByPriceAsc();
                    break;
                case 4: // Price High-Low
                    entities = db.productDao().getAllSortedByPriceDesc();
                    break;
                case 5: // Stock High-Low
                    entities = db.productDao().getAllSortedByStockDesc();
                    break;
                default:
                    entities = db.productDao().getAllSortedByNameAsc();
                    break;
            }
            return mapEntitiesToModels(context, entities);
        });
    }

    public static List<Product> getAllSortedPaged(final Context context, final int sortPosition, final int limit, final int offset) {
        return DbExecutor.runBlocking(() -> {
            triggerProductsBackgroundRefreshIfNeeded(context);
            AppDatabase db = AppDatabase.getInstance(context);
            List<ProductEntity> entities;
            switch (sortPosition) {
                case 0:
                case 1:
                    entities = db.productDao().getAllSortedByNameAscPaged(limit, offset);
                    break;
                case 2:
                    entities = db.productDao().getAllSortedByNameDescPaged(limit, offset);
                    break;
                case 3:
                    entities = db.productDao().getAllSortedByPriceAscPaged(limit, offset);
                    break;
                case 4:
                    entities = db.productDao().getAllSortedByPriceDescPaged(limit, offset);
                    break;
                case 5:
                    entities = db.productDao().getAllSortedByStockDescPaged(limit, offset);
                    break;
                default:
                    entities = db.productDao().getAllSortedByNameAscPaged(limit, offset);
                    break;
            }
            return mapEntitiesToModels(context, entities);
        });
    }

    public static List<Product> searchProductsSortedPaged(final Context context, final String query, final int sortPosition, final int limit, final int offset) {
        return DbExecutor.runBlocking(() -> {
            triggerProductsBackgroundRefreshIfNeeded(context);
            String q = query == null ? "" : query.trim();
            if (q.isEmpty()) {
                return getAllSortedPaged(context, sortPosition, limit, offset);
            }
            AppDatabase db = AppDatabase.getInstance(context);
            List<ProductEntity> entities;
            switch (sortPosition) {
                case 0:
                case 1:
                    entities = db.productDao().searchProductsByNameAscPaged(q, limit, offset);
                    break;
                case 2:
                    entities = db.productDao().searchProductsByNameDescPaged(q, limit, offset);
                    break;
                case 3:
                    entities = db.productDao().searchProductsByPriceAscPaged(q, limit, offset);
                    break;
                case 4:
                    entities = db.productDao().searchProductsByPriceDescPaged(q, limit, offset);
                    break;
                case 5:
                    entities = db.productDao().searchProductsByStockDescPaged(q, limit, offset);
                    break;
                default:
                    entities = db.productDao().searchProductsByNameAscPaged(q, limit, offset);
                    break;
            }
            return mapEntitiesToModels(context, entities);
        });
    }

    // Legacy search - kept for backward compatibility (uses in-memory filter,
    // includes barcode matching which the DB LIKE query already supports).
    public static List<Product> search(Context context, String query) {
        List<Product> all = getAll(context);
        if (query == null || query.trim().isEmpty()) return new ArrayList<>();
        List<Product> out = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT);
        for (Product p : all) {
            String name = p.getName() == null ? "" : p.getName().toLowerCase(Locale.ROOT);
            String barcode = p.getBarcode() == null ? "" : p.getBarcode().toLowerCase(Locale.ROOT);
            if (name.contains(q) || barcode.contains(q)) out.add(p);
        }
        return out;
    }

    public static void add(final Context context, final String name, final double price, final int stock) {
        add(
                context,
                name,
                stock,
                price,
                price * SalesUnit.defaultFactor(SalesUnit.RENTENG),
                price * SalesUnit.defaultFactor(SalesUnit.PAK),
                price * SalesUnit.defaultFactor(SalesUnit.KARTON),
                SalesUnit.defaultFactor(SalesUnit.RENTENG),
                SalesUnit.defaultFactor(SalesUnit.PAK),
                SalesUnit.defaultFactor(SalesUnit.KARTON)
        );
    }

    // min-compatible overload: (name, costPrice, stock, price, trackStock)
    public static void add(
            final Context context,
            final String name,
            final double costPrice,
            final int stock,
            final double price,
            final boolean trackStock
    ) {
        double safePrice = Math.max(0, price);
        add(
                context,
                name,
                "",
                costPrice,
                stock,
                "pcs",
                trackStock,
                safePrice,
                safePrice * SalesUnit.defaultFactor(SalesUnit.RENTENG),
                safePrice * SalesUnit.defaultFactor(SalesUnit.PAK),
                safePrice * SalesUnit.defaultFactor(SalesUnit.KARTON),
                SalesUnit.defaultFactor(SalesUnit.RENTENG),
                SalesUnit.defaultFactor(SalesUnit.PAK),
                SalesUnit.defaultFactor(SalesUnit.KARTON)
        );
    }

    public static void add(
            final Context context,
            final String name,
            final int stock,
            final double priceEcer,
            final double priceRenteng,
            final double pricePak,
            final double priceKarton
    ) {
        add(
                context,
                name,
                stock,
                priceEcer,
                priceRenteng,
                pricePak,
                priceKarton,
                SalesUnit.defaultFactor(SalesUnit.RENTENG),
                SalesUnit.defaultFactor(SalesUnit.PAK),
                SalesUnit.defaultFactor(SalesUnit.KARTON)
        );
    }

    public static void add(
            final Context context,
            final String name,
            final int stock,
            final double priceEcer,
            final double priceRenteng,
            final double pricePak,
            final double priceKarton,
            final int factorRenteng,
            final int factorPak,
            final int factorKarton
    ) {
        add(context, name, "", 0, stock, "pcs", true, priceEcer, priceRenteng, pricePak, priceKarton, factorRenteng, factorPak, factorKarton);
    }

    public static void add(
            final Context context,
            final String name,
            final String barcode,
            final double costPrice,
            final int stock,
            final String unitName,
            final boolean trackStock,
            final double priceEcer,
            final double priceRenteng,
            final double pricePak,
            final double priceKarton,
            final int factorRenteng,
            final int factorPak,
            final int factorKarton
    ) {
        DbExecutor.runBlocking(() -> {
            String normalizedName = normalizeProductName(name);
            assertProductNameAvailable(context, normalizedName, null);
            String productId = UUID.randomUUID().toString();
            int safeFactorRenteng = factorRenteng <= 0 ? SalesUnit.defaultFactor(SalesUnit.RENTENG) : factorRenteng;
            int safeFactorPak = factorPak <= 0 ? SalesUnit.defaultFactor(SalesUnit.PAK) : factorPak;
            int safeFactorKarton = factorKarton <= 0 ? SalesUnit.defaultFactor(SalesUnit.KARTON) : factorKarton;
            Product product = new Product(
                    productId,
                    normalizedName,
                    barcode,
                    Math.max(0, priceEcer),
                    Math.max(0, costPrice),
                    stock,
                    unitName,
                    trackStock,
                    Math.max(0, priceEcer),
                    Math.max(0, priceRenteng),
                    Math.max(0, pricePak),
                    Math.max(0, priceKarton),
                    safeFactorRenteng,
                    safeFactorPak,
                    safeFactorKarton
            );
            if (shouldUseSupabase(context)) {
                try {
                    if (NetworkUtils.isOnline(context)) {
                        getRemoteProvider(context).addProduct(getOutletId(context), getAccessToken(context), product);
                        upsertLocalProduct(context, product);
                        ProductUnitPriceStore.save(context, product);
                        triggerSyncIfPossible(context);
                    } else {
                        // Local product write + outbox row commit atomically in one Room transaction.
                        AppDatabase db = AppDatabase.getInstance(context);
                        OutboxStore.ensureLegacyMigrated(context);
                        db.runInTransaction(() -> {
                            upsertLocalProduct(context, product);
                            enqueueProductOpInTx(db, "add", product);
                        });
                        ProductUnitPriceStore.save(context, product);
                        OutboxSyncScheduler.enqueueImmediate(context, 0L);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return;
            }
            AppDatabase.getInstance(context).productDao().insert(toEntity(product));
            ProductUnitPriceStore.save(context, product);
        });
    }

    public static int addAll(final Context context, final List<Product> rawProducts) {
        return DbExecutor.runBlocking(() -> {
            if (rawProducts == null || rawProducts.isEmpty()) return 0;
            List<Product> normalizedList = new ArrayList<>();
            List<ProductEntity> entities = new ArrayList<>();
            for (Product raw : rawProducts) {
                if (raw == null) continue;
                String normalizedName = normalizeProductName(raw.getName());
                if (normalizedName.isEmpty()) continue;
                String productId = (raw.getId() != null && !raw.getId().trim().isEmpty())
                        ? raw.getId().trim()
                        : UUID.randomUUID().toString();
                int factorRenteng = raw.getFactorRenteng() <= 0 ? SalesUnit.defaultFactor(SalesUnit.RENTENG) : raw.getFactorRenteng();
                int factorPak = raw.getFactorPak() <= 0 ? SalesUnit.defaultFactor(SalesUnit.PAK) : raw.getFactorPak();
                int factorKarton = raw.getFactorKarton() <= 0 ? SalesUnit.defaultFactor(SalesUnit.KARTON) : raw.getFactorKarton();
                double priceEcer = Math.max(0, raw.getPriceEcer() > 0 ? raw.getPriceEcer() : raw.getPrice());
                Product product = new Product(
                        productId,
                        normalizedName,
                        raw.getBarcode(),
                        priceEcer,
                        Math.max(0, raw.getCostPrice()),
                        raw.getStock(),
                        raw.getUnitName(),
                        raw.isTrackStock(),
                        priceEcer,
                        Math.max(0, raw.getPriceRenteng() > 0 ? raw.getPriceRenteng() : priceEcer * factorRenteng),
                        Math.max(0, raw.getPricePak() > 0 ? raw.getPricePak() : priceEcer * factorPak),
                        Math.max(0, raw.getPriceKarton() > 0 ? raw.getPriceKarton() : priceEcer * factorKarton),
                        factorRenteng,
                        factorPak,
                        factorKarton
                );
                normalizedList.add(product);
                entities.add(toEntity(product));
            }
            if (normalizedList.isEmpty()) return 0;

            if (shouldUseSupabase(context)) {
                try {
                    if (NetworkUtils.isOnline(context)) {
                        getRemoteProvider(context).addProductsBatch(getOutletId(context), getAccessToken(context), normalizedList);
                        AppDatabase.getInstance(context).productDao().insertAll(entities);
                        for (Product product : normalizedList) {
                            ProductUnitPriceStore.save(context, product);
                        }
                        triggerSyncIfPossible(context);
                    } else {
                        AppDatabase db = AppDatabase.getInstance(context);
                        OutboxStore.ensureLegacyMigrated(context);
                        db.runInTransaction(() -> {
                            db.productDao().insertAll(entities);
                            for (Product product : normalizedList) {
                                enqueueProductOpInTx(db, "add", product);
                            }
                        });
                        for (Product product : normalizedList) {
                            ProductUnitPriceStore.save(context, product);
                        }
                        OutboxSyncScheduler.enqueueImmediate(context, 0L);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return normalizedList.size();
            }

            AppDatabase.getInstance(context).productDao().insertAll(entities);
            for (Product product : normalizedList) {
                ProductUnitPriceStore.save(context, product);
            }
            return normalizedList.size();
        });
    }

    public static void update(final Context context, final Product product) {
        DbExecutor.runBlocking(() -> {
            Product normalizedProduct = new Product(
                    product.getId(),
                    normalizeProductName(product.getName()),
                    product.getBarcode(),
                    product.getPrice(),
                    product.getCostPrice(),
                    product.getStock(),
                    product.getUnitName(),
                    product.isTrackStock(),
                    product.getPriceEcer(),
                    product.getPriceRenteng(),
                    product.getPricePak(),
                    product.getPriceKarton(),
                    product.getFactorRenteng(),
                    product.getFactorPak(),
                    product.getFactorKarton()
            );
            assertProductNameAvailable(context, normalizedProduct.getName(), normalizedProduct.getId());
            if (shouldUseSupabase(context)) {
                try {
                    boolean online = NetworkUtils.isOnline(context);
                    if (online) {
                        syncPendingProductOpsNow(context);
                        getRemoteProvider(context).updateProduct(getOutletId(context), getAccessToken(context), normalizedProduct);
                        upsertLocalProduct(context, normalizedProduct);
                    } else {
                        // Enqueue outbox + local upsert commit atomically in one Room transaction.
                        AppDatabase db = AppDatabase.getInstance(context);
                        OutboxStore.ensureLegacyMigrated(context);
                        db.runInTransaction(() -> {
                            enqueueProductOpInTx(db, "update", normalizedProduct);
                            upsertLocalProduct(context, normalizedProduct);
                        });
                        OutboxSyncScheduler.enqueueImmediate(context, 0L);
                    }
                    ProductUnitPriceStore.save(context, normalizedProduct);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return;
            }
            AppDatabase.getInstance(context).productDao().update(toEntity(normalizedProduct));
            ProductUnitPriceStore.save(context, normalizedProduct);
        });
    }

    public static void delete(final Context context, final String productId) {
        DbExecutor.runBlocking(() -> {
            if (shouldUseSupabase(context)) {
                try {
                    boolean online = NetworkUtils.isOnline(context);
                    if (online) {
                        syncPendingProductOpsNow(context);
                        getRemoteProvider(context).deleteProduct(getOutletId(context), getAccessToken(context), productId);
                        AppDatabase.getInstance(context).productDao().deleteById(productId);
                    } else {
                        // Enqueue outbox + local delete commit atomically in one Room transaction.
                        AppDatabase db = AppDatabase.getInstance(context);
                        OutboxStore.ensureLegacyMigrated(context);
                        db.runInTransaction(() -> {
                            OutboxStore.enqueueProductOpInTransaction(
                                    db, "delete", productId, "", "", 0, 0, 0, "pcs", true,
                                    0, 0, 0, 0, 10, 20, 100
                            );
                            db.productDao().deleteById(productId);
                        });
                        OutboxSyncScheduler.enqueueImmediate(context, 0L);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return;
            }
            AppDatabase.getInstance(context).productDao().deleteById(productId);
        });
    }

    public static boolean hasEnoughStock(Context context, List<CartItem> items) {
        if (items == null || items.isEmpty()) return true;
        return DbExecutor.runBlocking(() -> {
            // Local-first: validasi stok langsung ke Room agar checkout tidak pernah
            // tertahan latensi atau timeout jaringan Supabase.
            if (shouldUseSupabase(context)) {
                triggerProductsBackgroundRefreshIfNeeded(context);
            }
            Map<String, Integer> requiredBaseQty = aggregateBaseQty(items);
            AppDatabase db = AppDatabase.getInstance(context);
            for (Map.Entry<String, Integer> entry : requiredBaseQty.entrySet()) {
                ProductEntity p = db.productDao().getById(entry.getKey());
                if (p == null) return false;
                if (!p.trackStock) continue;
                if (p.stock < entry.getValue()) return false;
            }
            return true;
        });
    }

    public static void reduceStock(final Context context, final List<CartItem> items) {
        DbExecutor.runBlocking(() -> {
            if (shouldUseSupabase(context)) {
                try {
                    getRemoteProvider(context).reduceStock(getOutletId(context), getAccessToken(context), items);
                    applyLocalStockDelta(context, items, -1);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return;
            }
            for (CartItem item : items) {
                ProductEntity p = AppDatabase.getInstance(context).productDao().getById(item.getProduct().getId());
                if (p != null && p.trackStock) {
                    p.stock = Math.max(0, p.stock - item.getBaseQty());
                    AppDatabase.getInstance(context).productDao().update(p);
                }
            }
        });
    }

    public static void increaseStock(final Context context, final List<CartItem> items) {
        DbExecutor.runBlocking(() -> {
            if (shouldUseSupabase(context)) {
                try {
                    getRemoteProvider(context).increaseStock(getOutletId(context), getAccessToken(context), items);
                    applyLocalStockDelta(context, items, 1);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return;
            }
            for (CartItem item : items) {
                ProductEntity p = AppDatabase.getInstance(context).productDao().getById(item.getProduct().getId());
                if (p != null && p.trackStock) {
                    p.stock = p.stock + item.getBaseQty();
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
            List<Product> remote = getRemoteProvider(context).getAllProducts(getOutletId(context), getAccessToken(context));
            replaceLocalProductCache(context, remote);
            markProductsRefreshed(context);
            for (Product product : remote) {
                ProductUnitPriceStore.save(context, product);
            }
            return remote;
        }
        return getAllLocalProducts(context);
    }

    private static Product getByIdInternal(Context context, String id) throws Exception {
        if (shouldUseSupabase(context)) {
            Product local = getByIdLocalProduct(context, id);
            if (local != null) return local;
            Product remote = getRemoteProvider(context).getProductById(getOutletId(context), getAccessToken(context), id);
            if (remote != null) {
                upsertLocalProduct(context, remote);
                ProductUnitPriceStore.save(context, remote);
            }
            return remote;
        }
        return getByIdLocalProduct(context, id);
    }

    private static boolean shouldUseSupabase(Context context) {
        if (AuthSessionStore.isLocalDatabaseMode(context)) return false;
        RemoteDataProvider provider = getRemoteProvider(context);
        if (provider == null || !provider.isConfigured(context)) return false;
        if (provider.requiresAccessToken() && getAccessToken(context).isEmpty()) return false;
        if (provider.requiresOutletId() && getOutletId(context).isEmpty()) return false;
        ensureCacheBoundToCurrentOutlet(context, getOutletId(context));
        return true;
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

    // Harus dipanggil dari thread DB (di dalam DbExecutor.runBlocking).
    // Pengecekan terhadap cache Room lokal — bukan jaminan lintas device;
    // penegakan penuh butuh constraint unik di server.
    private static void assertProductNameAvailable(Context context, String normalizedName, String excludeId) {
        if (normalizedName == null || normalizedName.isEmpty()) return;
        for (ProductEntity existing : AppDatabase.getInstance(context).productDao().findByNameIgnoreCase(normalizedName)) {
            if (excludeId == null || !excludeId.equals(existing.id)) {
                throw new IllegalArgumentException("Nama produk sudah digunakan: " + existing.name);
            }
        }
    }

    private static List<Product> mapEntitiesToModels(Context context, List<ProductEntity> entities) {
        List<Product> models = new ArrayList<>();
        if (entities == null) return models;
        for (ProductEntity e : entities) {
            Product model = toModel(e);
            ProductUnitPriceStore.apply(context, model);
            models.add(model);
        }
        return models;
    }

    private static List<Product> getAllLocalProducts(Context context) {
        List<ProductEntity> entities = AppDatabase.getInstance(context).productDao().getAll();
        return mapEntitiesToModels(context, entities);
    }

    private static Product getByIdLocalProduct(Context context, String id) {
        ProductEntity e = AppDatabase.getInstance(context).productDao().getById(id);
        if (e == null) return null;
        Product model = toModel(e);
        ProductUnitPriceStore.apply(context, model);
        return model;
    }

    private static void replaceLocalProductCache(Context context, List<Product> products) {
        // Dipanggil dari thread NETWORK_IO (refresh background): arahkan ke DB_IO
        // agar tidak konkuren dengan transaksi Room di DB pool.
        DbExecutor.runBlocking(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            db.runInTransaction(() -> {
                db.productDao().deleteAll();
                List<ProductEntity> entities = new ArrayList<>();
                for (Product p : products) {
                    entities.add(toEntity(p));
                }
                db.productDao().insertAll(entities);
            });
        });
    }

    private static void upsertLocalProduct(Context context, Product product) {
        DbExecutor.runBlocking(() -> {
            AppDatabase.getInstance(context).productDao()
                    .insert(toEntity(product));
        });
    }

    private static void applyLocalStockDelta(Context context, List<CartItem> items, int direction) {
        for (CartItem item : items) {
            ProductEntity p = AppDatabase.getInstance(context).productDao().getById(item.getProduct().getId());
            if (p == null || !p.trackStock) continue;
            int delta = item.getBaseQty() * direction;
            p.stock = direction < 0 ? Math.max(0, p.stock + delta) : p.stock + delta;
            AppDatabase.getInstance(context).productDao().update(p);
        }
    }

    static void applyLocalCheckoutStockReduction(Context context, List<CartItem> items) {
        applyLocalStockDelta(context, items, -1);
    }

    private static Map<String, Integer> aggregateBaseQty(List<CartItem> items) {
        Map<String, Integer> out = new HashMap<>();
        if (items == null) return out;
        for (CartItem item : items) {
            if (item == null || item.getProduct() == null) continue;
            if (!item.getProduct().isTrackStock()) continue;
            String productId = item.getProduct().getId();
            if (productId == null || productId.trim().isEmpty()) continue;
            int baseQty = Math.max(0, item.getBaseQty());
            int previous = out.containsKey(productId) ? out.get(productId) : 0;
            out.put(productId, previous + baseQty);
        }
        return out;
    }

    private static ProductEntity toEntity(Product product) {
        return new ProductEntity(
                product.getId(),
                product.getName(),
                product.getBarcode(),
                product.getPrice(),
                product.getCostPrice(),
                product.getStock(),
                product.getUnitName(),
                product.isTrackStock()
        );
    }

    private static Product toModel(ProductEntity entity) {
        // Entity Room hanya menyimpan harga ecer; tier prices dan conversion
        // factors dipulihkan oleh ProductUnitPriceStore.apply pada caller.
        return new Product(
                entity.id,
                entity.name,
                entity.barcode,
                entity.price,
                entity.costPrice,
                entity.stock,
                entity.unitName,
                entity.trackStock,
                entity.price,
                entity.price,
                entity.price,
                entity.price,
                1,
                1,
                1
        );
    }

    // Enqueues a product op carrying the full retail field set inside an
    // already-open Room transaction. Caller must run ensureLegacyMigrated
    // before opening the transaction and schedule sync after commit.
    private static void enqueueProductOpInTx(AppDatabase db, String op, Product product) {
        OutboxStore.enqueueProductOpInTransaction(
                db,
                op,
                product.getId(),
                product.getName(),
                product.getBarcode(),
                product.getPriceEcer(),
                product.getCostPrice(),
                product.getStock(),
                product.getUnitName(),
                product.isTrackStock(),
                product.getPriceEcer(),
                product.getPriceRenteng(),
                product.getPricePak(),
                product.getPriceKarton(),
                product.getFactorRenteng(),
                product.getFactorPak(),
                product.getFactorKarton()
        );
    }

    private static void triggerProductsBackgroundRefreshIfNeeded(Context context) {
        if (!shouldUseSupabase(context)) return;
        if (RestoreStateStore.shouldDelaySupabaseSync(context)) return;
        if (!NetworkUtils.isOnline(context)) return;
        long now = System.currentTimeMillis();
        long last = prefs(context).getLong(KEY_PRODUCTS_LAST_REFRESH, 0L);
        if (now - last < PRODUCTS_REFRESH_INTERVAL_MS) return;
        if (!PRODUCTS_SYNC_IN_FLIGHT.compareAndSet(false, true)) return;

        final Context appContext = context.getApplicationContext();
        prefs(appContext).edit().putLong(KEY_PRODUCTS_LAST_REFRESH, now).apply();
        DbExecutor.runNetworkAsync(() -> {
            try {
                syncPendingProductOpsNow(appContext);
                refreshProductsNow(appContext);
                markProductsRefreshed(appContext);
            } catch (Exception e) {
                Log.w(TAG, "Gagal sinkronisasi produk Supabase", e);
            } finally {
                PRODUCTS_SYNC_IN_FLIGHT.set(false);
            }
        });
    }

    private static void refreshProductsNow(Context context) {
        try {
            List<Product> remote = getRemoteProvider(context).getAllProducts(getOutletId(context), getAccessToken(context));
            replaceLocalProductCache(context, remote);
            markProductsRefreshed(context);
            for (Product product : remote) {
                ProductUnitPriceStore.save(context, product);
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal memuat produk dari remote sync", e);
            // Jangan lempar exception ke luar - handling error di sini saja
            // agar sync tidak berhenti total dan bisa retry nanti
        }
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

        // Selalu lewat DB_IO (termasuk dari thread network) agar Room tidak
        // diakses konkuren di luar DB pool.
        DbExecutor.runBlocking(() -> {
            AppDatabase.getInstance(context).productDao().deleteAll();
            OutboxStore.clearPendingProductOps(context);
        });
        preferences.edit()
                .putString(KEY_PRODUCTS_CACHE_OUTLET_ID, outletId)
                .putLong(KEY_PRODUCTS_LAST_REFRESH, 0L)
                .apply();
    }

    public static void triggerSyncIfPossible(Context context) {
        // Route through the throttled refresh: this is called on every
        // BaseActivity.onStart, and an unthrottled full-cache replace here
        // caused severe UI jank on every screen transition.
        triggerProductsBackgroundRefreshIfNeeded(context);
    }

    public static void flushPendingProductOpsForWorker(Context context) {
        if (context == null) return;
        // Sama seperti flush transaksi: HTTP upload produk tidak boleh
        // menempati slot DB_IO — jalankan langsung di thread worker.
        syncPendingProductOpsNow(context.getApplicationContext());
    }

    private static void syncPendingProductOpsNow(Context context) {
        if (!shouldUseSupabase(context)) return;
        if (!NetworkUtils.isOnline(context)) return;
        List<OutboxStore.ProductSyncOp> ops = OutboxStore.getDuePendingProductOps(context, OutboxStore.DEFAULT_BATCH_SIZE);
        if (ops.isEmpty()) return;

        for (OutboxStore.ProductSyncOp op : ops) {
            boolean synced = false;
            String lastError = null;
            if ("add".equals(op.op)) {
                try {
                    getRemoteProvider(context).addProduct(getOutletId(context), getAccessToken(context), toProduct(op));
                    synced = true;
                } catch (Exception e) {
                    lastError = e.getMessage();
                    Log.e(TAG, "Gagal tambah produk", e);
                }
            } else if ("update".equals(op.op)) {
                try {
                    getRemoteProvider(context).updateProduct(getOutletId(context), getAccessToken(context), toProduct(op));
                    synced = true;
                } catch (Exception e) {
                    try {
                        getRemoteProvider(context).addProduct(getOutletId(context), getAccessToken(context), toProduct(op));
                        synced = true;
                    } catch (Exception e2) {
                        lastError = e2.getMessage();
                        Log.e(TAG, "Gagal update/add produk", e2);
                    }
                }
            } else if ("delete".equals(op.op)) {
                try {
                    getRemoteProvider(context).deleteProduct(getOutletId(context), getAccessToken(context), op.id);
                    synced = true;
                } catch (Exception e) {
                    lastError = e.getMessage();
                    Log.e(TAG, "Gagal hapus produk", e);
                }
            } else {
                // Unknown op: drop it instead of retrying forever.
                synced = true;
            }

            if (synced) {
                OutboxStore.removeProductOp(context, op.id);
            } else {
                OutboxStore.recordProductOpFailure(context, op.id, lastError);
            }

            // Yield point: allow other tasks to run and prevent thread saturation
            Thread.yield();
            // Small delay to prevent overwhelming the network/DB during large sync batches
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static RemoteDataProvider getRemoteProvider(Context context) {
        return RemoteDataProviderFactory.getProvider(context);
    }

    private static Product toProduct(OutboxStore.ProductSyncOp op) {
        double priceEcer = op.priceEcer > 0 ? op.priceEcer : op.price;
        int factorRenteng = op.factorRenteng <= 0 ? SalesUnit.defaultFactor(SalesUnit.RENTENG) : op.factorRenteng;
        int factorPak = op.factorPak <= 0 ? SalesUnit.defaultFactor(SalesUnit.PAK) : op.factorPak;
        int factorKarton = op.factorKarton <= 0 ? SalesUnit.defaultFactor(SalesUnit.KARTON) : op.factorKarton;
        double priceRenteng = op.priceRenteng > 0 ? op.priceRenteng : priceEcer * factorRenteng;
        double pricePak = op.pricePak > 0 ? op.pricePak : priceEcer * factorPak;
        double priceKarton = op.priceKarton > 0 ? op.priceKarton : priceEcer * factorKarton;
        return new Product(
                op.id,
                op.name,
                op.barcode,
                priceEcer,
                op.costPrice,
                op.stock,
                op.unitName,
                op.trackStock,
                priceEcer,
                priceRenteng,
                pricePak,
                priceKarton,
                factorRenteng,
                factorPak,
                factorKarton
        );
    }

    public static void getAllAsync(final Context context, final DbExecutor.SuccessCallback<List<Product>> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getAll(context), onSuccess, onError);
    }

    public static void getCountAsync(final Context context, final DbExecutor.SuccessCallback<Integer> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getCount(context), onSuccess, onError);
    }

    public static void searchProductsAsync(final Context context, final String query, final DbExecutor.SuccessCallback<List<Product>> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> searchProducts(context, query), onSuccess, onError);
    }

    public static void searchProductsAsync(final Context context, final String query, final int sortPosition, final DbExecutor.SuccessCallback<List<Product>> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> searchProductsSorted(context, query, sortPosition), onSuccess, onError);
    }

    public static void getAllSortedAsync(final Context context, final int sortPosition, final DbExecutor.SuccessCallback<List<Product>> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getAllSorted(context, sortPosition), onSuccess, onError);
    }

    public static void getAllSortedPagedAsync(final Context context, final int sortPosition, final int limit, final int offset, final DbExecutor.SuccessCallback<List<Product>> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getAllSortedPaged(context, sortPosition, limit, offset), onSuccess, onError);
    }

    public static void searchProductsSortedPagedAsync(final Context context, final String query, final int sortPosition, final int limit, final int offset, final DbExecutor.SuccessCallback<List<Product>> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> searchProductsSortedPaged(context, query, sortPosition, limit, offset), onSuccess, onError);
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

    // min-compatible async overload: (name, costPrice, stock, price, trackStock)
    public static void addAsync(
            final Context context,
            final String name,
            final double costPrice,
            final int stock,
            final double price,
            final boolean trackStock,
            final Runnable onSuccess,
            final DbExecutor.ErrorCallback onError
    ) {
        DbExecutor.runAsync(() -> {
            add(context, name, costPrice, stock, price, trackStock);
            return null;
        }, ignored -> {
            if (onSuccess != null) onSuccess.run();
        }, onError);
    }

    public static void addAsync(
            final Context context,
            final String name,
            final int stock,
            final double priceEcer,
            final double priceRenteng,
            final double pricePak,
            final double priceKarton,
            final Runnable onSuccess,
            final DbExecutor.ErrorCallback onError
    ) {
        addAsync(
                context,
                name,
                stock,
                priceEcer,
                priceRenteng,
                pricePak,
                priceKarton,
                SalesUnit.defaultFactor(SalesUnit.RENTENG),
                SalesUnit.defaultFactor(SalesUnit.PAK),
                SalesUnit.defaultFactor(SalesUnit.KARTON),
                onSuccess,
                onError
        );
    }

    public static void addAsync(
            final Context context,
            final String name,
            final int stock,
            final double priceEcer,
            final double priceRenteng,
            final double pricePak,
            final double priceKarton,
            final int factorRenteng,
            final int factorPak,
            final int factorKarton,
            final Runnable onSuccess,
            final DbExecutor.ErrorCallback onError
    ) {
        addAsync(
                context,
                name,
                "",
                0,
                stock,
                "pcs",
                true,
                priceEcer,
                priceRenteng,
                pricePak,
                priceKarton,
                factorRenteng,
                factorPak,
                factorKarton,
                onSuccess,
                onError
        );
    }

    public static void addAsync(
            final Context context,
            final String name,
            final String barcode,
            final double costPrice,
            final int stock,
            final String unitName,
            final boolean trackStock,
            final double priceEcer,
            final double priceRenteng,
            final double pricePak,
            final double priceKarton,
            final int factorRenteng,
            final int factorPak,
            final int factorKarton,
            final Runnable onSuccess,
            final DbExecutor.ErrorCallback onError
    ) {
        DbExecutor.runAsync(() -> {
            add(context, name, barcode, costPrice, stock, unitName, trackStock, priceEcer, priceRenteng, pricePak, priceKarton, factorRenteng, factorPak, factorKarton);
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
