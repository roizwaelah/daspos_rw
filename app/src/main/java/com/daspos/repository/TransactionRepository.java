package com.daspos.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.util.Log;

import com.daspos.db.AppDatabase;
import com.daspos.db.entity.TransactionEntity;
import com.daspos.db.entity.TransactionItemEntity;
import com.daspos.db.model.TransactionWithItems;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.model.BestSellerItem;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.ReportItem;
import com.daspos.model.SalesUnit;
import com.daspos.model.TransactionRecord;
import com.daspos.remote.RemoteDataProvider;
import com.daspos.remote.RemoteDataProviderFactory;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.NetworkUtils;
import com.daspos.shared.util.RestoreStateStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TransactionRepository {
    private static final String TAG = "TransactionRepository";
    private static final String CACHE_PREF = "supabase_cache_meta";
    private static final String KEY_INSTALLATION_ID = "installation_id";
    private static final String KEY_TRANSACTIONS_LAST_REFRESH = "transactions_last_refresh";
    private static final String KEY_TRANSACTIONS_CACHE_OUTLET_ID = "transactions_cache_outlet_id";
    private static final long TRANSACTIONS_REFRESH_INTERVAL_MS = 10_000L;
    private static final long INCREMENTAL_OVERLAP_MS = 300_000L;
    private static final long FULL_RECONCILE_INTERVAL_MS = 30L * 60L * 1000L;
    private static final String KEY_TRANSACTIONS_LAST_FULL_RECONCILE = "transactions_last_full_reconcile";
    private static final java.util.concurrent.atomic.AtomicBoolean TRANSACTIONS_SYNC_IN_FLIGHT = new java.util.concurrent.atomic.AtomicBoolean(false);

    public static String save(final Context context, final List<CartItem> items, final double total, final double discount, final double pay, final double change) {
        return DbExecutor.runBlocking(() -> {
            if (shouldUseSupabase(context)) {
                AppDatabase db = AppDatabase.getInstance(context);
                long timestamp = System.currentTimeMillis();
                String pendingLocalId = nextLocalTransactionId(db, timestamp);

                // Migrasi SharedPreferences lama -> Room harus selesai SEBELUM transaksi
                // lokal+outbox dibuka agar tidak bercampur dalam satu transaksi Room.
                OutboxStore.ensureLegacyMigrated(context);

                // Local-first checkout: simpan transaksi, item, diskon, pengurangan stok
                // lokal, dan antrean Outbox dalam satu transaksi SQLite atomik tanpa
                // memblokir jaringan.
                final String[] savedIdHolder = new String[1];
                db.runInTransaction(() -> {
                    String localId = saveLocalWithId(context, pendingLocalId, timestamp, items, total, discount, pay, change);
                    OutboxStore.enqueueTransactionInTransaction(db, localId);
                    savedIdHolder[0] = localId;
                });

                // Picu drain Outbox segera setelah commit (bukan di dalam transaksi).
                OutboxSyncScheduler.enqueueImmediate(context, 0L);
                triggerTransactionsBackgroundRefresh(context, true);
                Log.i(TAG, "checkout saved localId=" + savedIdHolder[0] + " items=" + items.size());
                return savedIdHolder[0];
            }
            return saveLocal(context, items, total, discount, pay, change);
        });
    }

    public static String save(final Context context, final List<CartItem> items, final double total, final double pay, final double change) {
        return save(context, items, total, 0, pay, change);
    }

    public static List<TransactionRecord> getAll(final Context context) {
        return DbExecutor.runBlocking(() -> {
            if (shouldUseSupabase(context)) {
                List<TransactionRecord> cached = getAllLocal(context);
                triggerTransactionsBackgroundRefresh(context, false);
                if (!cached.isEmpty()) return cached;
                List<TransactionRecord> remote = getRemoteProvider(context).getAllTransactions(getOutletId(context), getAccessToken(context));
                replaceLocalTransactionCache(context, remote);
                markTransactionsRefreshed(context);
                return remote;
            }
            return getAllLocal(context);
        });
    }

    public static TransactionRecord getById(final Context context, final String transactionId) {
        if (transactionId == null || transactionId.trim().isEmpty()) return null;
        return DbExecutor.runBlocking(() -> {
            if (shouldUseSupabase(context)) {
                TransactionRecord local = getByIdLocal(context, transactionId);
                if (local != null) {
                    triggerTransactionsBackgroundRefresh(context, false);
                    return local;
                }
                TransactionRecord remote = getRemoteProvider(context).getTransactionById(getOutletId(context), getAccessToken(context), transactionId);
                if (remote != null) upsertLocalTransaction(context, remote);
                return remote;
            }
            return getByIdLocal(context, transactionId);
        });
    }

    public static TransactionRecord getLast(Context context) {
        // Ambil baris terbaru langsung via DAO (ORDER BY timestamp DESC LIMIT 1)
        // daripada memuat seluruh riwayat transaksi + items ke memori.
        return DbExecutor.runBlocking(() -> {
            if (shouldUseSupabase(context)) {
                TransactionRecord local = getLastLocal(context);
                if (local != null) {
                    triggerTransactionsBackgroundRefresh(context, false);
                    return local;
                }
                List<TransactionRecord> remote = getRemoteProvider(context).getAllTransactions(getOutletId(context), getAccessToken(context));
                replaceLocalTransactionCache(context, remote);
                markTransactionsRefreshed(context);
                return remote == null || remote.isEmpty() ? null : remote.get(0);
            }
            return getLastLocal(context);
        });
    }

    public static boolean deleteById(final Context context, final String transactionId) {
        if (transactionId == null || transactionId.trim().isEmpty()) return false;
        if (!canDeleteTransaction(context)) return false;
        return DbExecutor.runBlocking(() -> {
            if (shouldUseSupabase(context)) {
                return deleteByIdSupabase(context, transactionId);
            }
            return deleteByIdLocal(context, transactionId);
        });
    }

    public static List<ReportItem> getReportItemsByPeriod(Context context, Calendar selectedCalendar, boolean monthly) {
        boolean useSupabase = shouldUseSupabase(context);
        if (useSupabase) {
            triggerTransactionsBackgroundRefresh(context, false);
        }
        List<TransactionRecord> sourceRecords = useSupabase
                ? getReportSourceRecords(context, selectedCalendar, monthly)
                : getAll(context);
        List<ReportItem> filtered = new ArrayList<>();
        if (monthly) {
            Map<Long, DailyTransactionSummary> summaries = new HashMap<>();
            for (TransactionRecord t : sourceRecords) {
                Calendar trxCal = parseTransactionDayCalendar(t.getDate(), t.getTime());
                if (trxCal == null) continue;
                boolean matchMonth = trxCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR)
                        && trxCal.get(Calendar.MONTH) == selectedCalendar.get(Calendar.MONTH);
                if (!matchMonth) continue;

                Calendar dayCal = (Calendar) trxCal.clone();
                dayCal.set(Calendar.HOUR_OF_DAY, 0);
                dayCal.set(Calendar.MINUTE, 0);
                dayCal.set(Calendar.SECOND, 0);
                dayCal.set(Calendar.MILLISECOND, 0);
                long dayKey = dayCal.getTimeInMillis();

                DailyTransactionSummary summary = summaries.get(dayKey);
                if (summary == null) {
                    summaries.put(dayKey, new DailyTransactionSummary(t.getDate(), 1, t.getTotal()));
                } else {
                    summary.count += 1;
                    summary.total += t.getTotal();
                }
            }

            List<Long> sortedDays = new ArrayList<>(summaries.keySet());
            Collections.sort(sortedDays, (left, right) -> Long.compare(right, left));
            for (Long dayKey : sortedDays) {
                DailyTransactionSummary summary = summaries.get(dayKey);
                if (summary == null) continue;
                filtered.add(new ReportItem(summary.dateLabel, summary.count + " transaksi", "", summary.total));
            }
            return filtered;
        }

        for (TransactionRecord t : sourceRecords) {
            if (useSupabase) {
                filtered.add(new ReportItem(t.getId(), t.getDate(), t.getTime(), t.getTotal()));
                continue;
            }
            Calendar trxCal = parseTransactionCalendar(t.getDate(), t.getTime());
            if (trxCal == null) continue;
            boolean match = trxCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR)
                    && trxCal.get(Calendar.MONTH) == selectedCalendar.get(Calendar.MONTH)
                    && trxCal.get(Calendar.DAY_OF_MONTH) == selectedCalendar.get(Calendar.DAY_OF_MONTH);
            if (match) filtered.add(new ReportItem(t.getId(), t.getDate(), t.getTime(), t.getTotal()));
        }
        Collections.sort(filtered, (left, right) -> {
            Calendar leftCal = parseTransactionCalendar(left.getDate(), left.getTime());
            Calendar rightCal = parseTransactionCalendar(right.getDate(), right.getTime());
            if (leftCal != null && rightCal != null) {
                int byTime = Long.compare(rightCal.getTimeInMillis(), leftCal.getTimeInMillis());
                if (byTime != 0) return byTime;
            } else if (leftCal != null) {
                return -1;
            } else if (rightCal != null) {
                return 1;
            }
            return right.getId().compareToIgnoreCase(left.getId());
        });
        return filtered;
    }

    private static List<TransactionRecord> getAllFreshForReport(Context context) {
        try {
            List<TransactionRecord> remote = getRemoteProvider(context).getAllTransactions(getOutletId(context), getAccessToken(context));
            replaceLocalTransactionCache(context, remote);
            markTransactionsRefreshed(context);
            return remote;
        } catch (Exception e) {
            Log.w(TAG, "Gagal fetch remote untuk report, fallback ke cache lokal", e);
            return getAllLocal(context);
        }
    }

    private static List<TransactionRecord> getReportSourceRecords(Context context, Calendar selectedCalendar, boolean monthly) {
        try {
            return getRemoteProvider(context).getTransactionsForReportPeriod(
                    getOutletId(context),
                    getAccessToken(context),
                    selectedCalendar,
                    monthly
            );
        } catch (Exception e) {
            throw new RuntimeException("Remote report fetch failed", e);
        }
    }

    public static boolean isSupabaseSessionActive(Context context) {
        return shouldUseSupabase(context);
    }

    public static List<BestSellerItem> getBestSellerItems(Context context, Calendar selectedCalendar, boolean monthly) {
        Map<String, BestSellerSummary> summaries = new HashMap<>();
        for (TransactionRecord t : getAll(context)) {
            Calendar trxCal = parseTransactionCalendar(t.getDate(), t.getTime());
            if (trxCal == null) continue;
            boolean match = monthly
                    ? trxCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR)
                    && trxCal.get(Calendar.MONTH) == selectedCalendar.get(Calendar.MONTH)
                    : trxCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR)
                    && trxCal.get(Calendar.MONTH) == selectedCalendar.get(Calendar.MONTH)
                    && trxCal.get(Calendar.DAY_OF_MONTH) == selectedCalendar.get(Calendar.DAY_OF_MONTH);
            if (!match) continue;

            for (CartItem item : t.getItems()) {
                String productId = item.getProduct().getId();
                BestSellerSummary summary = summaries.get(productId);
                if (summary == null) {
                    summary = new BestSellerSummary(productId, item.getProduct().getName());
                    summaries.put(productId, summary);
                }
                summary.totalQty += item.getBaseQty();
                summary.totalRevenue += item.getSubtotal();
            }
        }

        List<BestSellerItem> items = new ArrayList<>();
        for (BestSellerSummary summary : summaries.values()) {
            items.add(new BestSellerItem(summary.productId, summary.productName, summary.totalQty, summary.totalRevenue));
        }
        Collections.sort(items, (left, right) -> {
            int byQty = Integer.compare(right.getTotalQty(), left.getTotalQty());
            if (byQty != 0) return byQty;
            int byRevenue = Double.compare(right.getTotalRevenue(), left.getTotalRevenue());
            if (byRevenue != 0) return byRevenue;
            return left.getProductName().compareToIgnoreCase(right.getProductName());
        });
        return items;
    }

    public static int getTodayCount(Context context) {
        String today = new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID")).format(new Date());
        int total = 0;
        for (TransactionRecord t : getAll(context)) if (today.equals(t.getDate())) total++;
        return total;
    }

    public static double getTodayIncome(Context context) {
        String today = new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID")).format(new Date());
        double total = 0;
        for (TransactionRecord t : getAll(context)) if (today.equals(t.getDate())) total += t.getTotal();
        return total;
    }

    public static int getCountByPeriod(Context context, Calendar cal, boolean monthly) {
        return getReportItemsByPeriod(context, cal, monthly).size();
    }

    public static double getIncomeByPeriod(Context context, Calendar cal, boolean monthly) {
        double total = 0;
        for (ReportItem item : getReportItemsByPeriod(context, cal, monthly)) total += item.getTotal();
        return total;
    }

    // Optimized methods using database-level aggregation (for dashboard performance)
    public static int getTodayCountOptimized(Context context) {
        triggerTransactionsBackgroundRefresh(context, false);
        return DbExecutor.runBlocking(() -> {
            long[] bounds = todayBounds();
            AppDatabase db = AppDatabase.getInstance(context);
            return db.transactionDao().countByTimestampRange(bounds[0], bounds[1]);
        });
    }

    public static double getTodayIncomeOptimized(Context context) {
        triggerTransactionsBackgroundRefresh(context, false);
        return DbExecutor.runBlocking(() -> {
            long[] bounds = todayBounds();
            AppDatabase db = AppDatabase.getInstance(context);
            Double sum = db.transactionDao().sumTotalByTimestampRange(bounds[0], bounds[1]);
            return sum != null ? sum : 0.0;
        });
    }

    public static double getIncomeByPeriodOptimized(Context context, Calendar cal, boolean monthly) {
        triggerTransactionsBackgroundRefresh(context, false);
        return DbExecutor.runBlocking(() -> {
            long[] bounds = resolvePeriodBounds(cal, monthly);
            AppDatabase db = AppDatabase.getInstance(context);
            Double sum = db.transactionDao().sumTotalByTimestampRange(bounds[0], bounds[1]);
            return sum != null ? sum : 0.0;
        });
    }

    // Optimized best seller - uses DB-level aggregation
    public static List<BestSellerItem> getBestSellerItemsOptimized(Context context, Calendar cal, boolean monthly) {
        triggerTransactionsBackgroundRefresh(context, false);
        return DbExecutor.runBlocking(() -> {
            long[] bounds = resolvePeriodBounds(cal, monthly);
            AppDatabase db = AppDatabase.getInstance(context);
            return db.transactionDao().getBestSellerItems(bounds[0], bounds[1]);
        });
    }

    // Optimized report items - uses DB-level queries
    public static List<ReportItem> getReportItemsByPeriodOptimized(Context context, Calendar cal, boolean monthly) {
        triggerTransactionsBackgroundRefresh(context, false);
        return DbExecutor.runBlocking(() -> {
            long[] bounds = resolvePeriodBounds(cal, monthly);
            AppDatabase db = AppDatabase.getInstance(context);
            if (monthly) {
                return db.transactionDao().getDailySummariesByTimestampRange(bounds[0], bounds[1]);
            } else {
                return db.transactionDao().getTransactionsByTimestampRange(bounds[0], bounds[1]);
            }
        });
    }

    public static final class ReportPageResult {
        public final List<ReportItem> items;
        public final int totalCount;
        public final double totalIncome;
        public final boolean hasMore;

        public ReportPageResult(List<ReportItem> items, int totalCount, double totalIncome, boolean hasMore) {
            this.items = items == null ? Collections.emptyList() : items;
            this.totalCount = totalCount;
            this.totalIncome = totalIncome;
            this.hasMore = hasMore;
        }
    }

    public static ReportPageResult getReportPageOptimized(Context context, Calendar cal, boolean monthly, int limit, int offset) {
        triggerTransactionsBackgroundRefresh(context, false);
        return DbExecutor.runBlocking(() -> {
            long[] bounds = resolvePeriodBounds(cal, monthly);
            AppDatabase db = AppDatabase.getInstance(context);
            if (monthly) {
                List<ReportItem> summaries = db.transactionDao().getDailySummariesByTimestampRange(bounds[0], bounds[1]);
                double income = 0.0;
                for (ReportItem item : summaries) {
                    income += item.getTotal();
                }
                return new ReportPageResult(summaries, summaries.size(), income, false);
            }

            List<ReportItem> pageItems = db.transactionDao().getTransactionsByTimestampRangePaged(bounds[0], bounds[1], limit, offset);
            int totalCount = 0;
            double totalIncome = 0.0;
            if (offset == 0) {
                totalCount = db.transactionDao().countByTimestampRange(bounds[0], bounds[1]);
                Double sum = db.transactionDao().sumTotalByTimestampRange(bounds[0], bounds[1]);
                totalIncome = sum != null ? sum : 0.0;
            }
            boolean hasMore = pageItems.size() >= limit;
            return new ReportPageResult(pageItems, totalCount, totalIncome, hasMore);
        });
    }

    private static long[] todayBounds() {
        Calendar todayStart = Calendar.getInstance();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);

        Calendar todayEnd = (Calendar) todayStart.clone();
        todayEnd.add(Calendar.DAY_OF_MONTH, 1);
        return new long[]{todayStart.getTimeInMillis(), todayEnd.getTimeInMillis()};
    }

    private static long[] resolvePeriodBounds(Calendar cal, boolean monthly) {
        Calendar periodStart = (Calendar) cal.clone();
        Calendar periodEnd = (Calendar) cal.clone();
        if (monthly) {
            periodStart.set(Calendar.DAY_OF_MONTH, 1);
            periodStart.set(Calendar.HOUR_OF_DAY, 0);
            periodStart.set(Calendar.MINUTE, 0);
            periodStart.set(Calendar.SECOND, 0);
            periodStart.set(Calendar.MILLISECOND, 0);

            periodEnd.set(Calendar.DAY_OF_MONTH, 1);
            periodEnd.add(Calendar.MONTH, 1);
            periodEnd.set(Calendar.HOUR_OF_DAY, 0);
            periodEnd.set(Calendar.MINUTE, 0);
            periodEnd.set(Calendar.SECOND, 0);
            periodEnd.set(Calendar.MILLISECOND, 0);
        } else {
            periodStart.set(Calendar.HOUR_OF_DAY, 0);
            periodStart.set(Calendar.MINUTE, 0);
            periodStart.set(Calendar.SECOND, 0);
            periodStart.set(Calendar.MILLISECOND, 0);

            periodEnd = (Calendar) periodStart.clone();
            periodEnd.add(Calendar.DAY_OF_MONTH, 1);
        }
        return new long[]{periodStart.getTimeInMillis(), periodEnd.getTimeInMillis()};
    }

    private static Calendar parseTransactionCalendar(String date, String time) {
        try {
            Date parsed = new SimpleDateFormat("dd MMM yyyy HH:mm", new Locale("id", "ID")).parse(date + " " + time);
            Calendar cal = Calendar.getInstance();
            cal.setTime(parsed);
            return cal;
        } catch (Exception e) {
            return null;
        }
    }

    private static Calendar parseTransactionDayCalendar(String date, String time) {
        Calendar full = parseTransactionCalendar(date, time);
        if (full != null) return full;
        try {
            Date parsed = new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID")).parse(date);
            if (parsed == null) return null;
            Calendar cal = Calendar.getInstance();
            cal.setTime(parsed);
            return cal;
        } catch (Exception e) {
            return null;
        }
    }

    private static String saveLocal(Context context, List<CartItem> items, double total, double discount, double pay, double change) {
        AppDatabase db = AppDatabase.getInstance(context);
        long timestamp = System.currentTimeMillis();
        String id = nextLocalTransactionId(db, timestamp);
        db.runInTransaction(() -> saveLocalWithId(context, id, timestamp, items, total, discount, pay, change));
        return id;
    }

    private static String nextLocalTransactionId(AppDatabase db, long timestamp) {
        Calendar monthStart = Calendar.getInstance();
        monthStart.setTimeInMillis(timestamp);
        monthStart.set(Calendar.DAY_OF_MONTH, 1);
        monthStart.set(Calendar.HOUR_OF_DAY, 0);
        monthStart.set(Calendar.MINUTE, 0);
        monthStart.set(Calendar.SECOND, 0);
        monthStart.set(Calendar.MILLISECOND, 0);

        Calendar nextMonthStart = (Calendar) monthStart.clone();
        nextMonthStart.add(Calendar.MONTH, 1);

        int next = db.transactionDao().countByTimestampRange(
                monthStart.getTimeInMillis(),
                nextMonthStart.getTimeInMillis()
        ) + 1;
        String idPrefix = new SimpleDateFormat("yyMMdd", Locale.getDefault()).format(new Date(timestamp));
        String id;
        do {
            id = idPrefix + String.format(Locale.getDefault(), "%04d", next++);
        } while (db.transactionDao().getTransactionById(id) != null);
        return id;
    }

    private static String saveLocalWithId(
            Context context,
            String id,
            long timestamp,
            List<CartItem> items,
            double total,
            double discount,
            double pay,
            double change
    ) {
        AppDatabase db = AppDatabase.getInstance(context);
        String date = new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID")).format(new Date(timestamp));
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));

        db.transactionDao().insertTransaction(new TransactionEntity(id, date, time, timestamp, total, discount, pay, change));

        List<TransactionItemEntity> entities = new ArrayList<>();
        for (CartItem item : items) {
            entities.add(new TransactionItemEntity(
                    UUID.randomUUID().toString(),
                    id,
                    item.getProduct().getId(),
                    toStoredProductName(item),
                    item.getUnitPrice(),
                    item.getQty()
            ));
        }
        db.transactionDao().insertTransactionItems(entities);

        // Mode local-first: perbarui stok lokal langsung tanpa panggilan RPC jaringan.
        // Sinkronisasi ke server terjadi lewat baris sync_outbox di transaksi yang sama.
        ProductRepository.applyLocalCheckoutStockReduction(context, items);
        return id;
    }

    private static boolean isLikelyNetworkFailure(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof java.io.IOException) {
                return true;
            }
            String msg = cursor.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("timeout")
                        || lower.contains("timed out")
                        || lower.contains("unable to resolve host")
                        || lower.contains("failed to connect")
                        || lower.contains("connection reset")
                        || lower.contains("network is unreachable")
                        || lower.contains("broken pipe")
                        || lower.contains("eof")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static List<TransactionRecord> getAllLocal(Context context) {
        List<TransactionRecord> list = new ArrayList<>();
        AppDatabase db = AppDatabase.getInstance(context);
        for (TransactionWithItems row : db.transactionDao().getAllTransactionsWithItems()) {
            TransactionEntity t = row.transaction;
            if (t == null) continue;
            List<CartItem> items = new ArrayList<>();
            if (row.items != null) {
                for (TransactionItemEntity item : row.items) {
                    items.add(toCartItem(item));
                }
            }
            list.add(new TransactionRecord(t.id, t.date, t.time, t.total, t.discount, t.pay, t.changeAmount, items));
        }
        return list;
    }

    private static TransactionRecord getLastLocal(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        TransactionEntity t = db.transactionDao().getLastTransaction();
        if (t == null) return null;

        List<CartItem> items = new ArrayList<>();
        for (TransactionItemEntity item : db.transactionDao().getItemsByTransactionId(t.id)) {
            items.add(toCartItem(item));
        }
        return new TransactionRecord(t.id, t.date, t.time, t.total, t.discount, t.pay, t.changeAmount, items);
    }

    private static TransactionRecord getByIdLocal(Context context, String transactionId) {
        AppDatabase db = AppDatabase.getInstance(context);
        TransactionEntity t = db.transactionDao().getTransactionById(transactionId);
        if (t == null) return null;

        List<CartItem> items = new ArrayList<>();
        for (TransactionItemEntity item : db.transactionDao().getItemsByTransactionId(t.id)) {
            items.add(toCartItem(item));
        }
        return new TransactionRecord(t.id, t.date, t.time, t.total, t.discount, t.pay, t.changeAmount, items);
    }

    private static CartItem toCartItem(TransactionItemEntity item) {
        ParsedStoredItem parsed = parseStoredProductName(item.productName);
        Product product = parsed.tierPricingEnabled
                ? new Product(item.productId, parsed.productName, item.price, 0)
                : createStoredProduct(item, parsed);
        return new CartItem(product, item.qty, parsed.unitCode, parsed.unitFactorToBase, item.price);
    }

    private static Product createStoredProduct(TransactionItemEntity item, ParsedStoredItem parsed) {
        return new Product(
                item.productId,
                parsed.productName,
                "",
                item.price,
                0,
                0,
                parsed.unitName,
                false,
                item.price,
                item.price,
                item.price,
                item.price,
                1,
                1,
                1
        );
    }

    private static boolean deleteByIdLocal(Context context, String transactionId) {
        AppDatabase db = AppDatabase.getInstance(context);
        TransactionEntity transaction = db.transactionDao().getTransactionById(transactionId);
        if (transaction == null) return false;

        List<TransactionItemEntity> itemEntities = db.transactionDao().getItemsByTransactionId(transactionId);
        List<CartItem> items = new ArrayList<>();
        for (TransactionItemEntity item : itemEntities) {
            items.add(toCartItem(item));
        }

        db.runInTransaction(() -> {
            for (CartItem item : items) {
                com.daspos.db.entity.ProductEntity product = db.productDao().getById(item.getProduct().getId());
                if (product != null && product.trackStock) {
                    product.stock = product.stock + item.getBaseQty();
                    db.productDao().update(product);
                }
            }
            db.transactionDao().deleteItemsByTransactionId(transactionId);
            db.transactionDao().deleteTransactionById(transactionId);
        });
        // Batalkan upload outbox jika transaksi lokal ini masih pending.
        OutboxStore.removeTransaction(context, transactionId);
        return true;
    }

    private static boolean deleteByIdSupabase(Context context, String transactionId) {
        try {
            TransactionRecord record = getRemoteProvider(context).getTransactionById(
                    getOutletId(context),
                    getAccessToken(context),
                    transactionId
            );
            if (record == null) {
                // Transaksi belum tersinkron (masih pending di outbox):
                // tidak ada yang bisa dihapus di remote — hapus lokal +
                // batalkan antrean upload agar tidak muncul kembali.
                return deleteByIdLocal(context, transactionId);
            }

            // Kembalikan stok (remote + lokal) sebelum hapus, sama seperti perilaku rw lama.
            ProductRepository.increaseStock(context, record.getItems());
            boolean deleted = getRemoteProvider(context).deleteTransactionById(getOutletId(context), getAccessToken(context), transactionId);
            if (deleted) {
                AppDatabase db = AppDatabase.getInstance(context);
                db.runInTransaction(() -> {
                    db.transactionDao().deleteItemsByTransactionId(record.getId());
                    db.transactionDao().deleteTransactionById(record.getId());
                });
                ProductRepository.triggerSyncIfPossible(context);
            }
            return deleted;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean shouldUseSupabase(Context context) {
        if (AuthSessionStore.isLocalDatabaseMode(context)) return false;
        RemoteDataProvider provider = getRemoteProvider(context);
        if (provider == null || !provider.isConfigured(context)) return false;
        if (provider.requiresAccessToken() && getAccessToken(context).isEmpty()) return false;
        if (provider.requiresOutletId() && getOutletId(context).isEmpty()) return false;
        if (provider.requiresUserId() && getUserId(context).isEmpty()) return false;
        boolean active = true;
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

    private static String getUserId(Context context) {
        String userId = AuthSessionStore.getUserId(context);
        return userId == null ? "" : userId.trim();
    }

    private static boolean canDeleteTransaction(Context context) {
        String role = AuthSessionStore.getRole(context);
        return "admin".equalsIgnoreCase(role) || "owner".equalsIgnoreCase(role);
    }

    private static void triggerTransactionsBackgroundRefresh(Context context, boolean force) {
        if (!shouldUseSupabase(context)) return;
        if (RestoreStateStore.shouldDelaySupabaseSync(context)) return;
        if (!NetworkUtils.isOnline(context)) return;
        long now = System.currentTimeMillis();
        long last = prefs(context).getLong(KEY_TRANSACTIONS_LAST_REFRESH, 0L);
        if (!force && now - last < TRANSACTIONS_REFRESH_INTERVAL_MS) return;
        if (!TRANSACTIONS_SYNC_IN_FLIGHT.compareAndSet(false, true)) return;

        final Context appContext = context.getApplicationContext();
        prefs(appContext).edit().putLong(KEY_TRANSACTIONS_LAST_REFRESH, now).apply();
        DbExecutor.runNetworkAsync(() -> {
            try {
                syncPendingTransactionsNow(appContext);
                RemoteDataProvider provider = getRemoteProvider(appContext);
                List<TransactionRecord> incremental = tryIncrementalTransactionRefresh(appContext, provider);
                if (incremental != null) {
                    upsertLocalTransactions(appContext, incremental);
                } else if (isTransactionFullReconcileDue(appContext)) {
                    // Full reconcile is a wipe+rewrite of the whole table - too
                    // heavy for every navigation-driven refresh. Limit to 30 min
                    // intervals or while the local cache is still empty.
                    List<TransactionRecord> remote = provider.getAllTransactions(getOutletId(appContext), getAccessToken(appContext));
                    replaceLocalTransactionCache(appContext, remote);
                    markTransactionsFullReconciled(appContext);
                }
                markTransactionsRefreshed(appContext);
            } catch (Exception e) {
                Log.w(TAG, "Gagal refresh transaksi Supabase di background", e);
            } finally {
                TRANSACTIONS_SYNC_IN_FLIGHT.set(false);
            }
        });
    }

    public static void triggerSyncIfPossible(Context context) {
        // Route through the throttled refresh: this is called on every
        // BaseActivity.onStart, and an unthrottled full-cache replace here
        // caused severe UI jank on every screen transition.
        triggerTransactionsBackgroundRefresh(context, false);
    }

    public static void flushPendingTransactionsForWorker(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        if (!shouldUseSupabase(appContext)) return;
        if (!NetworkUtils.isOnline(appContext)) return;
        // Jangan bungkus dengan DbExecutor.runBlocking: HTTP upload transaksi
        // akan menempati slot DB_IO selama beberapa detik dan membuat checkout
        // kasir antre. Thread worker cukup memanggil langsung — semua akses
        // Room di dalamnya sudah diarahkan ke DB_IO lewat runBlocking.
        syncPendingTransactionsNow(appContext);
    }

    private static void syncPendingTransactionsNow(Context context) {
        List<String> pending = OutboxStore.getDuePendingTransactions(context, OutboxStore.DEFAULT_BATCH_SIZE);
        if (pending.isEmpty()) return;

        for (String localId : pending) {
            TransactionRecord local = DbExecutor.runBlocking(() -> {
                return getByIdLocal(context, localId);
            });
            if (local == null) {
                OutboxStore.removeTransaction(context, localId);
                continue;
            }

            try {
                String remoteId = getRemoteProvider(context).saveTransaction(
                        getOutletId(context),
                        getUserId(context),
                        getAccessToken(context),
                        local.getItems(),
                        local.getTotal(),
                        local.getDiscount(),
                        local.getPay(),
                        local.getChange(),
                        offlineClientTransactionId(context, localId)
                );
                OutboxStore.removeTransaction(context, localId);
                Log.i(TAG, "outbox upload OK localId=" + localId + " remoteId=" + remoteId);
                rekeyLocalTransactionToRemoteId(context, localId, remoteId, local);
            } catch (Exception e) {
                OutboxStore.recordTransactionSyncFailure(context, localId, e.getMessage());
                Log.e(TAG, "Gagal sinkronisasi transaksi pending: " + localId, e);
            }
            Thread.yield();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void rekeyLocalTransactionToRemoteId(Context context, String localId, String remoteTrxNo, TransactionRecord local) {
        if (remoteTrxNo == null) return;
        String remoteId = remoteTrxNo.trim();
        if (remoteId.isEmpty() || remoteId.equals(localId)) return;
        DbExecutor.runBlocking(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            db.runInTransaction(() -> {
                TransactionRecord remapped = new TransactionRecord(
                        remoteId,
                        local.getDate(),
                        local.getTime(),
                        local.getTotal(),
                        local.getDiscount(),
                        local.getPay(),
                        local.getChange(),
                        local.getItems()
                );
                upsertLocalTransactionRecord(db, remapped);
                db.transactionDao().deleteItemsByTransactionId(localId);
                db.transactionDao().deleteTransactionById(localId);
            });
        });
    }

    private static RemoteDataProvider getRemoteProvider(Context context) {
        return RemoteDataProviderFactory.getProvider(context);
    }

    private static void markTransactionsRefreshed(Context context) {
        prefs(context).edit().putLong(KEY_TRANSACTIONS_LAST_REFRESH, System.currentTimeMillis()).apply();
    }

    private static void replaceLocalTransactionCache(Context context, List<TransactionRecord> records) {
        // Seluruh pekerjaan Room diarahkan ke DB_IO: method ini dipanggil dari
        // thread NETWORK_IO (background refresh) dan DB_IO (getter sinkron).
        DbExecutor.runBlocking(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            // Migrasi legacy SharedPreferences -> sync_outbox harus selesai sebelum transaksi.
            OutboxStore.ensureLegacyMigrated(context);

            Set<String> remoteIds = new HashSet<>();
            for (TransactionRecord record : records) {
                if (record != null && record.getId() != null) remoteIds.add(record.getId());
            }

            // Baca seluruh items DI LUAR transaksi tulis: sebelumnya pemetaan
            // ini berjalan di dalam runInTransaction sehingga write-lock SQLite
            // ditahan selama pembacaan + pembangunan map penuh, memblokir
            // checkout dan query dashboard selama hitungan detik.
            Map<String, List<TransactionItemEntity>> itemsByTransaction = new HashMap<>();
            for (TransactionItemEntity item : db.transactionDao().getAllTransactionItems()) {
                List<TransactionItemEntity> bucket = itemsByTransaction.get(item.transactionId);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    itemsByTransaction.put(item.transactionId, bucket);
                }
                bucket.add(item);
            }

            db.runInTransaction(() -> {
                // Preserve unsynced offline transactions: wiping them here would
                // permanently lose sales whose remote upload has not succeeded yet.
                // Pending ids + items diambil ulang DI DALAM transaksi agar checkout
                // yang commit di antara fase baca dan fase tulis tidak ikut terhapus.
                Map<String, TransactionEntity> pendingTransactions = new HashMap<>();
                Map<String, List<TransactionItemEntity>> pendingItems = new HashMap<>();
                List<String> pendingIdList = OutboxStore.getPendingTransactionIds(db);
                Log.i(TAG, "replaceCache: remote=" + records.size() + " pendingIds=" + pendingIdList.size() + " localTxBefore=" + db.transactionDao().count());
                for (String pendingId : pendingIdList) {
                    if (remoteIds.contains(pendingId)) continue;
                    TransactionEntity pendingTx = db.transactionDao().getTransactionById(pendingId);
                    if (pendingTx == null) continue;
                    pendingTransactions.put(pendingId, pendingTx);
                    List<TransactionItemEntity> pendingItemList = db.transactionDao().getItemsByTransactionId(pendingId);
                    if (pendingItemList != null && !pendingItemList.isEmpty()) {
                        pendingItems.put(pendingId, pendingItemList);
                    }
                }

                Map<String, List<TransactionItemEntity>> existingItemsByTransaction = itemsByTransaction;
                db.transactionDao().deleteAllItems();
                db.transactionDao().deleteAllTransactions();
                for (TransactionRecord record : records) {
                    long timestamp = toTimestamp(record.getDate(), record.getTime());
                    db.transactionDao().insertTransaction(new TransactionEntity(
                            record.getId(),
                            record.getDate(),
                            record.getTime(),
                            timestamp,
                            record.getTotal(),
                            record.getDiscount(),
                            record.getPay(),
                            record.getChange()
                    ));
                    List<TransactionItemEntity> existingItems = existingItemsByTransaction.get(record.getId());
                    if (existingItems != null && !existingItems.isEmpty()) {
                        db.transactionDao().insertTransactionItems(existingItems);
                        continue;
                    }

                    List<TransactionItemEntity> items = new ArrayList<>();
                    for (CartItem item : record.getItems()) {
                        items.add(new TransactionItemEntity(
                                UUID.randomUUID().toString(),
                                record.getId(),
                                item.getProduct().getId(),
                                toStoredProductName(item),
                                item.getUnitPrice(),
                                item.getQty()
                        ));
                    }
                    db.transactionDao().insertTransactionItems(items);
                }

                for (Map.Entry<String, TransactionEntity> entry : pendingTransactions.entrySet()) {
                    db.transactionDao().insertTransaction(entry.getValue());
                    List<TransactionItemEntity> items = pendingItems.get(entry.getKey());
                    if (items != null && !items.isEmpty()) {
                        db.transactionDao().insertTransactionItems(items);
                    }
                }
            });
        });
    }

    private static boolean isTransactionFullReconcileDue(Context context) {
        long lastFull = prefs(context).getLong(KEY_TRANSACTIONS_LAST_FULL_RECONCILE, 0L);
        if (lastFull <= 0L) return true;
        Long maxTimestamp = DbExecutor.runBlocking(() -> {
            return AppDatabase.getInstance(context).transactionDao().getMaxTimestamp();
        });
        if (maxTimestamp == null || maxTimestamp <= 0) return true;
        return System.currentTimeMillis() - lastFull >= FULL_RECONCILE_INTERVAL_MS;
    }

    private static void markTransactionsFullReconciled(Context context) {
        prefs(context).edit().putLong(KEY_TRANSACTIONS_LAST_FULL_RECONCILE, System.currentTimeMillis()).apply();
    }

    private static List<TransactionRecord> tryIncrementalTransactionRefresh(Context context, RemoteDataProvider provider) {
        try {
            Long maxTimestamp = DbExecutor.runBlocking(() -> {
                return AppDatabase.getInstance(context).transactionDao().getMaxTimestamp();
            });
            if (maxTimestamp == null || maxTimestamp <= 0) return null;

            // Local timestamps are minute-precision; the overlap window guards
            // against same-minute transactions and device clock skew.
            return provider.getTransactionsSince(
                    getOutletId(context),
                    getAccessToken(context),
                    maxTimestamp - INCREMENTAL_OVERLAP_MS
            );
        } catch (Exception e) {
            Log.w(TAG, "Incremental refresh transaksi gagal, fallback ke full sync", e);
            return null;
        }
    }

    private static void upsertLocalTransactions(Context context, List<TransactionRecord> records) {
        if (records == null || records.isEmpty()) return;
        DbExecutor.runBlocking(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            db.runInTransaction(() -> {
                for (TransactionRecord record : records) {
                    if (record == null || record.getId() == null) continue;
                    upsertLocalTransactionRecord(db, record);
                }
            });
        });
    }

    private static void upsertLocalTransaction(Context context, TransactionRecord record) {
        DbExecutor.runBlocking(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            db.runInTransaction(() -> upsertLocalTransactionRecord(db, record));
        });
    }

    private static void upsertLocalTransactionRecord(AppDatabase db, TransactionRecord record) {
        long timestamp = toTimestamp(record.getDate(), record.getTime());
        db.transactionDao().insertTransaction(new TransactionEntity(
                record.getId(),
                record.getDate(),
                record.getTime(),
                timestamp,
                record.getTotal(),
                record.getDiscount(),
                record.getPay(),
                record.getChange()
        ));
        db.transactionDao().deleteItemsByTransactionId(record.getId());
        List<TransactionItemEntity> items = new ArrayList<>();
        for (CartItem item : record.getItems()) {
            items.add(new TransactionItemEntity(
                    UUID.randomUUID().toString(),
                    record.getId(),
                    item.getProduct().getId(),
                    toStoredProductName(item),
                    item.getUnitPrice(),
                    item.getQty()
            ));
        }
        db.transactionDao().insertTransactionItems(items);
    }

    private static long toTimestamp(String date, String time) {
        try {
            Date parsed = new SimpleDateFormat("dd MMM yyyy HH:mm", new Locale("id", "ID")).parse(date + " " + time);
            return parsed == null ? System.currentTimeMillis() : parsed.getTime();
        } catch (Exception e) {
            Log.w(TAG, "Gagal parse timestamp transaksi: " + date + " " + time, e);
            return System.currentTimeMillis();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(CACHE_PREF, Context.MODE_PRIVATE);
    }

    static synchronized String getInstallationId(Context context) {
        SharedPreferences preferences = prefs(context);
        String existing = preferences.getString(KEY_INSTALLATION_ID, "");
        if (existing != null && !existing.trim().isEmpty()) return existing;

        String generated = UUID.randomUUID().toString();
        preferences.edit().putString(KEY_INSTALLATION_ID, generated).apply();
        return generated;
    }

    static String offlineClientTransactionId(Context context, String localId) {
        return getInstallationId(context) + ":" + localId;
    }

    private static void ensureCacheBoundToCurrentOutlet(Context context, String outletId) {
        if (outletId == null || outletId.trim().isEmpty()) return;
        SharedPreferences preferences = prefs(context);
        String cachedOutletId = preferences.getString(KEY_TRANSACTIONS_CACHE_OUTLET_ID, "");
        if (outletId.equals(cachedOutletId)) return;
        Log.w(TAG, "cache outlet rebind: '" + cachedOutletId + "' -> '" + outletId + "' (wipe transactions+outbox)");

        Runnable clearTask = () -> {
            AppDatabase db = AppDatabase.getInstance(context);
            db.runInTransaction(() -> {
                db.transactionDao().deleteAllItems();
                db.transactionDao().deleteAllTransactions();
            });
            OutboxStore.clearPendingTransactions(context);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            DbExecutor.runBlocking(clearTask);
        } else {
            clearTask.run();
        }
        preferences.edit()
                .putString(KEY_TRANSACTIONS_CACHE_OUTLET_ID, outletId)
                .putLong(KEY_TRANSACTIONS_LAST_REFRESH, 0L)
                .putLong(KEY_TRANSACTIONS_LAST_FULL_RECONCILE, 0L)
                .apply();
    }

    private static void cacheSavedSupabaseTransaction(
            Context context,
            String transactionId,
            List<CartItem> items,
            double total,
            double discount,
            double pay,
            double change
    ) {
        if (transactionId == null || transactionId.trim().isEmpty()) return;
        long timestamp = System.currentTimeMillis();
        String date = new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID")).format(new Date(timestamp));
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));

        AppDatabase db = AppDatabase.getInstance(context);
        db.runInTransaction(() -> {
            db.transactionDao().insertTransaction(new TransactionEntity(
                    transactionId,
                    date,
                    time,
                    timestamp,
                    total,
                    discount,
                    pay,
                    change
            ));
            db.transactionDao().deleteItemsByTransactionId(transactionId);
            List<TransactionItemEntity> entities = new ArrayList<>();
            for (CartItem item : items) {
                entities.add(new TransactionItemEntity(
                        UUID.randomUUID().toString(),
                        transactionId,
                        item.getProduct().getId(),
                        toStoredProductName(item),
                        item.getUnitPrice(),
                        item.getQty()
                ));
            }
            db.transactionDao().insertTransactionItems(entities);
        });
    }

    private static String toStoredProductName(CartItem item) {
        if (item == null || item.getProduct() == null) return "";
        String baseName = item.getProduct().getName() == null ? "" : item.getProduct().getName();
        return baseName
                + "~~~"
                + SalesUnit.normalize(item.getUnitCode())
                + "~~~"
                + Math.max(1, item.getUnitFactorToBase())
                + "~~~"
                + ((item.getProduct() != null && item.getProduct().isTierPricingEnabled()) ? "1" : "0")
                + "~~~"
                + (item.getProduct().getUnitName() == null ? "pcs" : item.getProduct().getUnitName());
    }

    private static ParsedStoredItem parseStoredProductName(String raw) {
        if (raw == null) {
            return new ParsedStoredItem("", SalesUnit.ECER, 1, true, "pcs");
        }
        String[] parts = raw.split("~~~");
        if (parts.length < 3) {
            // Baris legacy tanpa encoding unit: buang sisa suffix ~~~ bila ada.
            return new ParsedStoredItem(storedDisplayName(raw), SalesUnit.ECER, 1, true, "pcs");
        }
        String name = parts[0];
        String unitCode = SalesUnit.normalize(parts[1]);
        int factor = 1;
        try {
            factor = Integer.parseInt(parts[2].trim());
        } catch (Exception e) {
            Log.w(TAG, "Gagal parse unit factor dari item transaksi: " + parts[2], e);
        }
        boolean tierPricingEnabled = true;
        if (parts.length >= 4) {
            String rawFlag = parts[3] == null ? "" : parts[3].trim();
            tierPricingEnabled = !"0".equals(rawFlag) && !"false".equalsIgnoreCase(rawFlag);
        }
        String unitName = parts.length >= 5 && parts[4] != null && !parts[4].trim().isEmpty() ? parts[4].trim() : "pcs";
        return new ParsedStoredItem(name, unitCode, Math.max(1, factor), tierPricingEnabled, unitName);
    }

    // Rows written by older app versions stored unit/tier metadata after a
    // "~~~" separator; keep only the display name for those legacy rows.
    private static String storedDisplayName(String raw) {
        if (raw == null) return "";
        int sep = raw.indexOf("~~~");
        return sep >= 0 ? raw.substring(0, sep) : raw;
    }

    private static class BestSellerSummary {
        private final String productId;
        private final String productName;
        private int totalQty;
        private double totalRevenue;

        private BestSellerSummary(String productId, String productName) {
            this.productId = productId;
            this.productName = productName;
        }
    }

    private static class DailyTransactionSummary {
        private final String dateLabel;
        private int count;
        private double total;

        private DailyTransactionSummary(String dateLabel, int count, double total) {
            this.dateLabel = dateLabel;
            this.count = count;
            this.total = total;
        }
    }

    private static class ParsedStoredItem {
        private final String productName;
        private final String unitCode;
        private final int unitFactorToBase;
        private final boolean tierPricingEnabled;
        private final String unitName;

        private ParsedStoredItem(String productName, String unitCode, int unitFactorToBase, boolean tierPricingEnabled, String unitName) {
            this.productName = productName == null ? "" : productName;
            this.unitCode = unitCode == null ? SalesUnit.ECER : unitCode;
            this.unitFactorToBase = unitFactorToBase <= 0 ? 1 : unitFactorToBase;
            this.tierPricingEnabled = tierPricingEnabled;
            this.unitName = unitName == null || unitName.trim().isEmpty() ? "pcs" : unitName.trim();
        }
    }

    public static void saveAsync(final Context context, final List<CartItem> items, final double total, final double pay, final double change, final DbExecutor.SuccessCallback<String> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> save(context, items, total, pay, change), onSuccess, onError);
    }

    public static void saveAsync(final Context context, final List<CartItem> items, final double total, final double discount, final double pay, final double change, final DbExecutor.SuccessCallback<String> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> save(context, items, total, discount, pay, change), onSuccess, onError);
    }

    public static void getAllAsync(final Context context, final DbExecutor.SuccessCallback<List<TransactionRecord>> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getAll(context), onSuccess, onError);
    }

    public static void getByIdAsync(final Context context, final String transactionId, final DbExecutor.SuccessCallback<TransactionRecord> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getById(context, transactionId), onSuccess, onError);
    }

    public static void getLastAsync(final Context context, final DbExecutor.SuccessCallback<TransactionRecord> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getLast(context), onSuccess, onError);
    }

    public static void deleteByIdAsync(final Context context, final String transactionId, final DbExecutor.SuccessCallback<Boolean> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> deleteById(context, transactionId), onSuccess, onError);
    }

    public static void getBestSellerItemsAsync(final Context context, final Calendar selectedCalendar, final boolean monthly, final DbExecutor.SuccessCallback<List<BestSellerItem>> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getBestSellerItems(context, selectedCalendar, monthly), onSuccess, onError);
    }

    public static void getTodayCountAsync(final Context context, final DbExecutor.SuccessCallback<Integer> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getTodayCount(context), onSuccess, onError);
    }

    public static void getTodayIncomeAsync(final Context context, final DbExecutor.SuccessCallback<Double> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getTodayIncome(context), onSuccess, onError);
    }

    public static void getCountByPeriodAsync(final Context context, final Calendar cal, final boolean monthly, final DbExecutor.SuccessCallback<Integer> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getCountByPeriod(context, cal, monthly), onSuccess, onError);
    }

    public static void getIncomeByPeriodAsync(final Context context, final Calendar cal, final boolean monthly, final DbExecutor.SuccessCallback<Double> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getIncomeByPeriod(context, cal, monthly), onSuccess, onError);
    }

    public static void getReportItemsByPeriodAsync(final Context context, final Calendar selectedCalendar, final boolean monthly, final DbExecutor.SuccessCallback<List<ReportItem>> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getReportItemsByPeriod(context, selectedCalendar, monthly), onSuccess, onError);
    }

    public static void getTodayCountOptimizedAsync(final Context context, final DbExecutor.SuccessCallback<Integer> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getTodayCountOptimized(context), onSuccess, onError);
    }

    public static void getTodayIncomeOptimizedAsync(final Context context, final DbExecutor.SuccessCallback<Double> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getTodayIncomeOptimized(context), onSuccess, onError);
    }

    public static void getIncomeByPeriodOptimizedAsync(final Context context, final Calendar cal, final boolean monthly, final DbExecutor.SuccessCallback<Double> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getIncomeByPeriodOptimized(context, cal, monthly), onSuccess, onError);
    }

    public static void getBestSellerItemsOptimizedAsync(final Context context, final Calendar cal, final boolean monthly, final DbExecutor.SuccessCallback<List<BestSellerItem>> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getBestSellerItemsOptimized(context, cal, monthly), onSuccess, onError);
    }

    public static void getReportItemsByPeriodOptimizedAsync(final Context context, final Calendar cal, final boolean monthly, final DbExecutor.SuccessCallback<List<ReportItem>> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getReportItemsByPeriodOptimized(context, cal, monthly), onSuccess, onError);
    }

    public static void getReportPageOptimizedAsync(final Context context, final Calendar cal, final boolean monthly, final int limit, final int offset, final DbExecutor.SuccessCallback<ReportPageResult> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getReportPageOptimized(context, cal, monthly, limit, offset), onSuccess, onError);
    }
}
