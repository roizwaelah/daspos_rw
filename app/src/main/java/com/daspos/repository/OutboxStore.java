package com.daspos.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.daspos.db.AppDatabase;
import com.daspos.db.dao.OutboxDao;
import com.daspos.db.entity.OutboxEntity;
import com.daspos.shared.util.DbExecutor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single durable synchronization queue facade backed by the Room
 * {@code sync_outbox} table via {@link OutboxDao}.
 *
 * <p>Two layers are exposed:
 * <ul>
 *   <li>Context-level facade methods (used by sync workers, tests, and
 *       repository code that is NOT inside a Room transaction). They run
 *       their own {@link DbExecutor#runBlocking} block, perform the legacy
 *       SharedPreferences migration once, and schedule the immediate
 *       WorkManager drain after commit.</li>
 *   <li>Transaction-safe primitives (suffix {@code InTransaction}) that take
 *       an {@link AppDatabase} and must be invoked inside an already-open
 *       {@code db.runInTransaction} block so the local entity write and the
 *       outbox row commit atomically. They never schedule work; callers
 *       trigger synchronization after the transaction commits.</li>
 * </ul>
 *
 * <p>Legacy data from the old {@code "pending_sync"} SharedPreferences file
 * is migrated into Room exactly once (guarded by
 * {@code migrated_to_room_outbox_v1}); SharedPreferences is never a durable
 * queue anymore.
 *
 * <p>Product ops carry the full retail field set written by the legacy
 * {@code PendingSyncStore}: op, id, name, barcode, price, cost_price, stock,
 * unit_name, track_stock, price_ecer, price_renteng, price_pak, price_karton,
 * factor_renteng, factor_pak, factor_karton. Parsing is tolerant so payloads
 * written by the older minimal format (no barcode/unit/pricing-tier keys)
 * still deserialize with sane defaults.
 */
final class OutboxStore {
    private static final String PREF = "pending_sync";
    private static final String KEY_TRANSACTION_IDS = "pending_transaction_ids";
    private static final String KEY_PRODUCT_OPS = "pending_product_ops";
    private static final String KEY_MIGRATED_TO_ROOM = "migrated_to_room_outbox_v1";

    static final String ENTITY_TRANSACTION = "transaction";
    static final String ENTITY_PRODUCT = "product";
    static final int DEFAULT_BATCH_SIZE = 20;

    private static final long BASE_BACKOFF_MS = 2_000L;
    private static final long MAX_BACKOFF_MS = 300_000L; // 5 menit

    // Dedicated monitor for the one-time migration. Must NOT be the class
    // monitor: facade methods hold the class monitor across DbExecutor.runBlocking
    // while the migration runs on a pool thread -> class-monitor sync would deadlock.
    private static final Object MIGRATION_LOCK = new Object();

    private OutboxStore() {
    }

    static long computeNextRetryAt(int currentAttemptCount, long nowMillis) {
        int exponent = Math.min(Math.max(0, currentAttemptCount), 10);
        long delay = BASE_BACKOFF_MS * (1L << exponent);
        if (delay > MAX_BACKOFF_MS) {
            delay = MAX_BACKOFF_MS;
        }
        return nowMillis + delay;
    }

    // ------------------------------------------------------------------
    // Transaction-safe primitives (call inside db.runInTransaction)
    // ------------------------------------------------------------------

    /**
     * Enqueues a transaction outbox row using an already-open Room
     * transaction. Does not run the legacy migration and does not schedule
     * the worker; callers must invoke {@link #ensureLegacyMigrated(Context)}
     * before opening the transaction and schedule sync after commit.
     */
    static void enqueueTransactionInTransaction(AppDatabase db, String localTransactionId) {
        if (db == null || localTransactionId == null || localTransactionId.trim().isEmpty()) return;
        String normalizedId = localTransactionId.trim();
        OutboxDao dao = db.outboxDao();
        if (dao.findByEntity(ENTITY_TRANSACTION, normalizedId) != null) return;
        dao.insert(new OutboxEntity(
                ENTITY_TRANSACTION,
                "create",
                normalizedId,
                "{}",
                System.currentTimeMillis(),
                0,
                0L,
                null
        ));
    }

    /**
     * Enqueues (or merges) a product outbox op inside an already-open Room
     * transaction. Same dedup/merge semantics as the context-level facade.
     * Accepts the full retail field set (barcode, unit, pricing tiers,
     * conversion factors) so the payload round-trips losslessly.
     */
    static void enqueueProductOpInTransaction(
            AppDatabase db,
            String op,
            String id,
            String name,
            String barcode,
            double price,
            double costPrice,
            int stock,
            String unitName,
            boolean trackStock,
            double priceEcer,
            double priceRenteng,
            double pricePak,
            double priceKarton,
            int factorRenteng,
            int factorPak,
            int factorKarton
    ) {
        if (db == null || id == null || id.trim().isEmpty()) return;
        String normalizedId = id.trim();
        enqueueProductOp(
                db,
                op,
                normalizedId,
                name,
                barcode,
                price,
                costPrice,
                stock,
                unitName,
                trackStock,
                priceEcer,
                priceRenteng,
                pricePak,
                priceKarton,
                factorRenteng,
                factorPak,
                factorKarton
        );
    }

    /**
     * Returns pending transaction entity ids using the given database
     * instance, safe to call inside an open Room transaction.
     */
    static List<String> getPendingTransactionIds(AppDatabase db) {
        if (db == null) return new ArrayList<>();
        return extractUniqueIds(db.outboxDao().getByEntityType(ENTITY_TRANSACTION));
    }

    /**
     * Runs the one-time SharedPreferences -> Room migration. Idempotent and
     * cheap after the first run. Repositories MUST call this before opening
     * a {@code runInTransaction} block that will write outbox rows.
     *
     * <p>Product op payloads are stored verbatim ({@code o.toString()}), so
     * every key written by the legacy store — including barcode, unit_name,
     * the four price tiers and the three conversion factors — survives the
     * migration untouched.
     */
    static void ensureLegacyMigrated(Context context) {
        SharedPreferences p = prefs(context);
        boolean hasLegacyTx = p.contains(KEY_TRANSACTION_IDS);
        boolean hasLegacyProd = p.contains(KEY_PRODUCT_OPS);
        if (p.getBoolean(KEY_MIGRATED_TO_ROOM, false) && !hasLegacyTx && !hasLegacyProd) {
            return;
        }

        synchronized (MIGRATION_LOCK) {
        AppDatabase db = AppDatabase.getInstance(context);
        db.runInTransaction(() -> {
            OutboxDao dao = db.outboxDao();
            long now = System.currentTimeMillis();

            if (hasLegacyTx) {
                String rawTx = p.getString(KEY_TRANSACTION_IDS, "[]");
                try {
                    JSONArray arr = new JSONArray(rawTx);
                    for (int i = 0; i < arr.length(); i++) {
                        String id = arr.optString(i, "").trim();
                        if (!id.isEmpty() && dao.findByEntity(ENTITY_TRANSACTION, id) == null) {
                            dao.insert(new OutboxEntity(
                                    ENTITY_TRANSACTION,
                                    "create",
                                    id,
                                    "{}",
                                    now + i,
                                    0,
                                    0L,
                                    null
                            ));
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (hasLegacyProd) {
                String rawProd = p.getString(KEY_PRODUCT_OPS, "[]");
                try {
                    JSONArray arr = new JSONArray(rawProd);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        String op = o.optString("op", "").trim();
                        String id = o.optString("id", "").trim();
                        if (op.isEmpty() || id.isEmpty()) continue;
                        if (dao.findByEntity(ENTITY_PRODUCT, id) == null) {
                            dao.insert(new OutboxEntity(
                                    ENTITY_PRODUCT,
                                    op,
                                    id,
                                    o.toString(),
                                    now + i,
                                    0,
                                    0L,
                                    null
                            ));
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        });

        p.edit()
                .remove(KEY_TRANSACTION_IDS)
                .remove(KEY_PRODUCT_OPS)
                .putBoolean(KEY_MIGRATED_TO_ROOM, true)
                .apply();
        }
    }

    // ------------------------------------------------------------------
    // Context-level facade (own DbExecutor block + post-commit scheduling)
    // ------------------------------------------------------------------

    static void enqueueTransaction(Context context, String localTransactionId) {
        if (localTransactionId == null || localTransactionId.trim().isEmpty()) return;
        final String normalizedId = localTransactionId.trim();
        DbExecutor.runBlocking(() -> {
            ensureLegacyMigrated(context);
            enqueueTransactionInTransaction(AppDatabase.getInstance(context), normalizedId);
            return null;
        });
        OutboxSyncScheduler.enqueueImmediate(context, 0L);
    }

    static List<String> getPendingTransactions(Context context) {
        return DbExecutor.runBlocking(() -> {
            ensureLegacyMigrated(context);
            List<OutboxEntity> rows = AppDatabase.getInstance(context)
                    .outboxDao()
                    .getByEntityType(ENTITY_TRANSACTION);
            return extractUniqueIds(rows);
        });
    }

    static List<String> getDuePendingTransactions(Context context, int limit) {
        final int effectiveLimit = limit > 0 ? limit : DEFAULT_BATCH_SIZE;
        return DbExecutor.runBlocking(() -> {
            ensureLegacyMigrated(context);
            List<OutboxEntity> rows = AppDatabase.getInstance(context)
                    .outboxDao()
                    .getDueByEntityType(ENTITY_TRANSACTION, System.currentTimeMillis(), effectiveLimit);
            return extractUniqueIds(rows);
        });
    }

    private static List<String> extractUniqueIds(List<OutboxEntity> rows) {
        List<String> out = new ArrayList<>(rows.size());
        Set<String> seen = new HashSet<>();
        for (OutboxEntity row : rows) {
            if (row.entityId != null && !row.entityId.trim().isEmpty() && seen.add(row.entityId)) {
                out.add(row.entityId);
            }
        }
        return out;
    }

    static int getPendingTransactionCount(Context context) {
        return DbExecutor.runBlocking(() -> {
            ensureLegacyMigrated(context);
            return AppDatabase.getInstance(context)
                    .outboxDao()
                    .countByEntityType(ENTITY_TRANSACTION);
        });
    }

    static void recordTransactionSyncFailure(Context context, String localTransactionId, String errorMessage) {
        if (localTransactionId == null || localTransactionId.trim().isEmpty()) return;
        final String normalizedId = localTransactionId.trim();
        DbExecutor.runBlocking(() -> {
            ensureLegacyMigrated(context);
            OutboxDao dao = AppDatabase.getInstance(context).outboxDao();
            OutboxEntity existing = dao.findByEntity(ENTITY_TRANSACTION, normalizedId);
            if (existing != null) {
                long nextRetryAt = computeNextRetryAt(existing.attemptCount, System.currentTimeMillis());
                dao.recordFailure(existing.id, nextRetryAt, errorMessage);
            }
            return null;
        });
    }

    static void removeTransaction(Context context, String localTransactionId) {
        if (localTransactionId == null || localTransactionId.trim().isEmpty()) return;
        final String normalizedId = localTransactionId.trim();
        DbExecutor.runBlocking(() -> {
            ensureLegacyMigrated(context);
            AppDatabase.getInstance(context)
                    .outboxDao()
                    .deleteByEntity(ENTITY_TRANSACTION, normalizedId);
            return null;
        });
    }

    static void clearPendingTransactions(Context context) {
        DbExecutor.runBlocking(() -> {
            prefs(context).edit().remove(KEY_TRANSACTION_IDS).apply();
            AppDatabase.getInstance(context)
                    .outboxDao()
                    .deleteByEntityType(ENTITY_TRANSACTION);
            return null;
        });
    }

    static void enqueueProductAdd(
            Context context,
            String id,
            String name,
            String barcode,
            double price,
            double costPrice,
            int stock,
            String unitName,
            boolean trackStock,
            double priceEcer,
            double priceRenteng,
            double pricePak,
            double priceKarton,
            int factorRenteng,
            int factorPak,
            int factorKarton
    ) {
        enqueueProductOp(
                context,
                "add",
                id,
                name,
                barcode,
                price,
                costPrice,
                stock,
                unitName,
                trackStock,
                priceEcer,
                priceRenteng,
                pricePak,
                priceKarton,
                factorRenteng,
                factorPak,
                factorKarton
        );
    }

    static void enqueueProductUpdate(
            Context context,
            String id,
            String name,
            String barcode,
            double price,
            double costPrice,
            int stock,
            String unitName,
            boolean trackStock,
            double priceEcer,
            double priceRenteng,
            double pricePak,
            double priceKarton,
            int factorRenteng,
            int factorPak,
            int factorKarton
    ) {
        enqueueProductOp(
                context,
                "update",
                id,
                name,
                barcode,
                price,
                costPrice,
                stock,
                unitName,
                trackStock,
                priceEcer,
                priceRenteng,
                pricePak,
                priceKarton,
                factorRenteng,
                factorPak,
                factorKarton
        );
    }

    static void enqueueProductDelete(Context context, String id) {
        // Defaults mirror PendingSyncStore.enqueueProductDelete so delete ops
        // carry a well-formed payload even though no product fields apply.
        enqueueProductOp(
                context,
                "delete",
                id,
                "",
                "",
                0,
                0,
                0,
                "pcs",
                true,
                0,
                0,
                0,
                0,
                10,
                20,
                100
        );
    }

    static List<ProductSyncOp> getPendingProductOps(Context context) {
        return DbExecutor.runBlocking(() -> {
            ensureLegacyMigrated(context);
            List<OutboxEntity> rows = AppDatabase.getInstance(context)
                    .outboxDao()
                    .getByEntityType(ENTITY_PRODUCT);
            return mapProductOps(rows);
        });
    }

    static List<ProductSyncOp> getDuePendingProductOps(Context context, int limit) {
        final int effectiveLimit = limit > 0 ? limit : DEFAULT_BATCH_SIZE;
        return DbExecutor.runBlocking(() -> {
            ensureLegacyMigrated(context);
            List<OutboxEntity> rows = AppDatabase.getInstance(context)
                    .outboxDao()
                    .getDueByEntityType(ENTITY_PRODUCT, System.currentTimeMillis(), effectiveLimit);
            return mapProductOps(rows);
        });
    }

    private static List<ProductSyncOp> mapProductOps(List<OutboxEntity> rows) {
        List<ProductSyncOp> out = new ArrayList<>(rows.size());
        for (OutboxEntity row : rows) {
            ProductSyncOp parsed = fromOutboxEntity(row);
            if (parsed != null) out.add(parsed);
        }
        return out;
    }

    static void recordProductOpFailure(Context context, String productId, String errorMessage) {
        if (productId == null || productId.trim().isEmpty()) return;
        final String normalizedId = productId.trim();
        DbExecutor.runBlocking(() -> {
            ensureLegacyMigrated(context);
            OutboxDao dao = AppDatabase.getInstance(context).outboxDao();
            OutboxEntity existing = dao.findByEntity(ENTITY_PRODUCT, normalizedId);
            if (existing != null) {
                long nextRetryAt = computeNextRetryAt(existing.attemptCount, System.currentTimeMillis());
                dao.recordFailure(existing.id, nextRetryAt, errorMessage);
            }
            return null;
        });
    }

    static void removeProductOp(Context context, String productId) {
        if (productId == null || productId.trim().isEmpty()) return;
        final String normalizedId = productId.trim();
        DbExecutor.runBlocking(() -> {
            ensureLegacyMigrated(context);
            AppDatabase.getInstance(context)
                    .outboxDao()
                    .deleteByEntity(ENTITY_PRODUCT, normalizedId);
            return null;
        });
    }

    static void replaceProductOps(Context context, List<ProductSyncOp> ops) {
        DbExecutor.runBlocking(() -> {
            ensureLegacyMigrated(context);
            AppDatabase db = AppDatabase.getInstance(context);
            db.runInTransaction(() -> {
                OutboxDao dao = db.outboxDao();
                dao.deleteByEntityType(ENTITY_PRODUCT);
                if (ops != null) {
                    long baseTime = System.currentTimeMillis();
                    int idx = 0;
                    for (ProductSyncOp op : ops) {
                        if (op == null || op.id == null || op.id.trim().isEmpty()) continue;
                        try {
                            dao.insert(new OutboxEntity(
                                    ENTITY_PRODUCT,
                                    op.op,
                                    op.id.trim(),
                                    toJson(op).toString(),
                                    baseTime + (idx++),
                                    0,
                                    0L,
                                    null
                            ));
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
            return null;
        });
        if (ops != null && !ops.isEmpty()) {
            OutboxSyncScheduler.enqueueImmediate(context, 0L);
        }
    }

    static void clearPendingProductOps(Context context) {
        DbExecutor.runBlocking(() -> {
            prefs(context).edit().remove(KEY_PRODUCT_OPS).apply();
            AppDatabase.getInstance(context)
                    .outboxDao()
                    .deleteByEntityType(ENTITY_PRODUCT);
            return null;
        });
    }

    private static void enqueueProductOp(
            Context context,
            String op,
            String id,
            String name,
            String barcode,
            double price,
            double costPrice,
            int stock,
            String unitName,
            boolean trackStock,
            double priceEcer,
            double priceRenteng,
            double pricePak,
            double priceKarton,
            int factorRenteng,
            int factorPak,
            int factorKarton
    ) {
        if (id == null || id.trim().isEmpty()) return;
        final String normalizedId = id.trim();
        DbExecutor.runBlocking(() -> {
            ensureLegacyMigrated(context);
            AppDatabase db = AppDatabase.getInstance(context);
            db.runInTransaction(() -> {
                enqueueProductOp(
                        db,
                        op,
                        normalizedId,
                        name,
                        barcode,
                        price,
                        costPrice,
                        stock,
                        unitName,
                        trackStock,
                        priceEcer,
                        priceRenteng,
                        pricePak,
                        priceKarton,
                        factorRenteng,
                        factorPak,
                        factorKarton
                );
            });
            return null;
        });
        OutboxSyncScheduler.enqueueImmediate(context, 0L);
    }

    /**
     * Core product-op merge logic operating on a given {@link AppDatabase}.
     * Safe to call inside an already-open Room transaction.
     *
     * <p>Delete-then-merge semantics: any prior row for the same product id
     * is removed first; a "delete" op cancels a pending "add" entirely,
     * an "update" is promoted to "add" when the prior op was "add", and
     * otherwise the new op is inserted with the supplied field set.
     */
    private static void enqueueProductOp(
            AppDatabase db,
            String op,
            String normalizedId,
            String name,
            String barcode,
            double price,
            double costPrice,
            int stock,
            String unitName,
            boolean trackStock,
            double priceEcer,
            double priceRenteng,
            double pricePak,
            double priceKarton,
            int factorRenteng,
            int factorPak,
            int factorKarton
    ) {
        OutboxDao dao = db.outboxDao();
        OutboxEntity prior = dao.findByEntity(ENTITY_PRODUCT, normalizedId);
        String priorOp = prior != null ? prior.operation : null;
        dao.deleteByEntity(ENTITY_PRODUCT, normalizedId);

        if ("delete".equals(op)) {
            // Jika produk dibuat offline dan dihapus sebelum pernah sinkron, batalkan keduanya.
            if (priorOp == null || !"add".equals(priorOp)) {
                ProductSyncOp deleteOp = new ProductSyncOp(
                        "delete",
                        normalizedId,
                        "",
                        "",
                        0,
                        0,
                        0,
                        "pcs",
                        true,
                        0,
                        0,
                        0,
                        0,
                        10,
                        20,
                        100
                );
                try {
                    dao.insert(new OutboxEntity(
                            ENTITY_PRODUCT,
                            "delete",
                            normalizedId,
                            toJson(deleteOp).toString(),
                            System.currentTimeMillis(),
                            0,
                            0L,
                            null
                    ));
                } catch (Exception ignored) {
                }
            }
        } else if ("update".equals(op)) {
            String effectiveOp = "add".equals(priorOp) ? "add" : "update";
            ProductSyncOp nextOp = new ProductSyncOp(
                    effectiveOp,
                    normalizedId,
                    name == null ? "" : name,
                    barcode == null ? "" : barcode,
                    price,
                    costPrice,
                    stock,
                    unitName,
                    trackStock,
                    priceEcer,
                    priceRenteng,
                    pricePak,
                    priceKarton,
                    factorRenteng,
                    factorPak,
                    factorKarton
            );
            try {
                dao.insert(new OutboxEntity(
                        ENTITY_PRODUCT,
                        effectiveOp,
                        normalizedId,
                        toJson(nextOp).toString(),
                        System.currentTimeMillis(),
                        0,
                        0L,
                        null
                ));
            } catch (Exception ignored) {
            }
        } else {
            ProductSyncOp addOp = new ProductSyncOp(
                    op,
                    normalizedId,
                    name == null ? "" : name,
                    barcode == null ? "" : barcode,
                    price,
                    costPrice,
                    stock,
                    unitName,
                    trackStock,
                    priceEcer,
                    priceRenteng,
                    pricePak,
                    priceKarton,
                    factorRenteng,
                    factorPak,
                    factorKarton
            );
            try {
                dao.insert(new OutboxEntity(
                        ENTITY_PRODUCT,
                        op,
                        normalizedId,
                        toJson(addOp).toString(),
                        System.currentTimeMillis(),
                        0,
                        0L,
                        null
                ));
            } catch (Exception ignored) {
            }
        }
    }

    private static ProductSyncOp fromOutboxEntity(OutboxEntity row) {
        if (row == null || row.entityId == null || row.entityId.trim().isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(row.payloadJson);
            String op = row.operation.isEmpty() ? o.optString("op", "") : row.operation;
            if (op.isEmpty()) return null;
            double price = o.has("price_ecer") && !o.has("price")
                    ? o.optDouble("price_ecer", 0)
                    : o.optDouble("price", o.optDouble("price_ecer", 0));
            return new ProductSyncOp(
                    op,
                    row.entityId,
                    o.optString("name", ""),
                    o.optString("barcode", ""),
                    price,
                    o.optDouble("cost_price", 0),
                    o.optInt("stock", 0),
                    o.optString("unit_name", "pcs"),
                    o.optBoolean("track_stock", true),
                    o.has("price_ecer") ? o.optDouble("price_ecer", price) : price,
                    o.optDouble("price_renteng", 0),
                    o.optDouble("price_pak", 0),
                    o.optDouble("price_karton", 0),
                    Math.max(1, o.optInt("factor_renteng", 10)),
                    Math.max(1, o.optInt("factor_pak", 20)),
                    Math.max(1, o.optInt("factor_karton", 100)),
                    row.attemptCount,
                    row.nextRetryAt
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JSONObject toJson(ProductSyncOp op) throws Exception {
        JSONObject o = new JSONObject();
        o.put("op", op.op);
        o.put("id", op.id);
        o.put("name", op.name == null ? "" : op.name);
        o.put("barcode", op.barcode == null ? "" : op.barcode);
        o.put("price", op.price);
        o.put("cost_price", op.costPrice);
        o.put("stock", op.stock);
        o.put("unit_name", op.unitName == null || op.unitName.trim().isEmpty() ? "pcs" : op.unitName.trim());
        o.put("track_stock", op.trackStock);
        o.put("price_ecer", op.priceEcer);
        o.put("price_renteng", op.priceRenteng);
        o.put("price_pak", op.pricePak);
        o.put("price_karton", op.priceKarton);
        o.put("factor_renteng", op.factorRenteng);
        o.put("factor_pak", op.factorPak);
        o.put("factor_karton", op.factorKarton);
        return o;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static final class ProductSyncOp {
        final String op;
        final String id;
        final String name;
        final String barcode;
        final double price;
        final double costPrice;
        final int stock;
        final String unitName;
        final boolean trackStock;
        final double priceEcer;
        final double priceRenteng;
        final double pricePak;
        final double priceKarton;
        final int factorRenteng;
        final int factorPak;
        final int factorKarton;
        final int attemptCount;
        final long nextRetryAt;

        ProductSyncOp(
                String op,
                String id,
                String name,
                String barcode,
                double price,
                double costPrice,
                int stock,
                String unitName,
                boolean trackStock,
                double priceEcer,
                double priceRenteng,
                double pricePak,
                double priceKarton,
                int factorRenteng,
                int factorPak,
                int factorKarton
        ) {
            this(
                    op, id, name, barcode, price, costPrice, stock, unitName,
                    trackStock, priceEcer, priceRenteng, pricePak, priceKarton,
                    factorRenteng, factorPak, factorKarton, 0, 0L
            );
        }

        ProductSyncOp(
                String op,
                String id,
                String name,
                String barcode,
                double price,
                double costPrice,
                int stock,
                String unitName,
                boolean trackStock,
                double priceEcer,
                double priceRenteng,
                double pricePak,
                double priceKarton,
                int factorRenteng,
                int factorPak,
                int factorKarton,
                int attemptCount,
                long nextRetryAt
        ) {
            this.op = op;
            this.id = id;
            this.name = name;
            this.barcode = barcode;
            this.price = price;
            this.costPrice = costPrice;
            this.stock = stock;
            this.unitName = unitName;
            this.trackStock = trackStock;
            this.priceEcer = priceEcer;
            this.priceRenteng = priceRenteng;
            this.pricePak = pricePak;
            this.priceKarton = priceKarton;
            this.factorRenteng = factorRenteng;
            this.factorPak = factorPak;
            this.factorKarton = factorKarton;
            this.attemptCount = attemptCount;
            this.nextRetryAt = nextRetryAt;
        }
    }
}
