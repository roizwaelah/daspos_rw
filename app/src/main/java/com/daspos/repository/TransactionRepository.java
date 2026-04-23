package com.daspos.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

import com.daspos.db.AppDatabase;
import com.daspos.db.entity.TransactionEntity;
import com.daspos.db.entity.TransactionItemEntity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.model.BestSellerItem;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.ReportItem;
import com.daspos.model.TransactionRecord;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.NetworkUtils;
import com.daspos.shared.util.RestoreStateStore;
import com.daspos.supabase.SupabaseConfig;
import com.daspos.supabase.SupabaseTransactionService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TransactionRepository {
    private static final String CACHE_PREF = "supabase_cache_meta";
    private static final String KEY_TRANSACTIONS_LAST_REFRESH = "transactions_last_refresh";
    private static final String KEY_TRANSACTIONS_CACHE_OUTLET_ID = "transactions_cache_outlet_id";
    private static final long TRANSACTIONS_REFRESH_INTERVAL_MS = 20_000L;

    public static String save(final Context context, final List<CartItem> items, final double total, final double pay, final double change) {
        return DbExecutor.runBlocking(() -> {
            if (shouldUseSupabase(context)) {
                if (NetworkUtils.isOnline(context)) {
                    String transactionId = SupabaseTransactionService.save(
                            getOutletId(context),
                            getUserId(context),
                            getAccessToken(context),
                            items,
                            total,
                            pay,
                            change
                    );
                    // Keep local stock responsive right after successful remote checkout.
                    ProductRepository.applyLocalCheckoutStockReduction(context, items);
                    ProductRepository.triggerSyncIfPossible(context);
                    triggerTransactionsBackgroundRefresh(context, true);
                    return transactionId;
                }

                // Offline fallback: save locally first, then sync later.
                String localId = saveLocal(context, items, total, pay, change);
                PendingSyncStore.enqueueTransaction(context, localId);
                return localId;
            }
            return saveLocal(context, items, total, pay, change);
        });
    }

    public static List<TransactionRecord> getAll(final Context context) {
        return DbExecutor.runBlocking(() -> {
            if (shouldUseSupabase(context)) {
                List<TransactionRecord> cached = getAllLocal(context);
                triggerTransactionsBackgroundRefresh(context, false);
                if (!cached.isEmpty()) return cached;
                List<TransactionRecord> remote = SupabaseTransactionService.getAll(getOutletId(context), getAccessToken(context));
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
                TransactionRecord remote = SupabaseTransactionService.getById(getOutletId(context), getAccessToken(context), transactionId);
                if (remote != null) upsertLocalTransaction(context, remote);
                return remote;
            }
            return getByIdLocal(context, transactionId);
        });
    }

    public static TransactionRecord getLast(Context context) {
        List<TransactionRecord> list = getAll(context);
        return list.isEmpty() ? null : list.get(0);
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
            List<TransactionRecord> remote = SupabaseTransactionService.getAll(getOutletId(context), getAccessToken(context));
            replaceLocalTransactionCache(context, remote);
            markTransactionsRefreshed(context);
            return remote;
        } catch (Exception ignored) {
            return getAllLocal(context);
        }
    }

    private static List<TransactionRecord> getReportSourceRecords(Context context, Calendar selectedCalendar, boolean monthly) {
        try {
            return SupabaseTransactionService.getForReportPeriod(
                    getOutletId(context),
                    getAccessToken(context),
                    selectedCalendar,
                    monthly
            );
        } catch (Exception e) {
            throw new RuntimeException("Supabase report fetch failed", e);
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
                summary.totalQty += item.getQty();
                summary.totalRevenue += item.getProduct().getPrice() * item.getQty();
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

    private static String saveLocal(Context context, List<CartItem> items, double total, double pay, double change) {
        AppDatabase db = AppDatabase.getInstance(context);
        long timestamp = System.currentTimeMillis();
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
        String id = new SimpleDateFormat("yyMMdd", Locale.getDefault()).format(new Date(timestamp))
                + String.format(Locale.getDefault(), "%04d", next);
        String date = new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID")).format(new Date(timestamp));
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestamp));

        db.transactionDao().insertTransaction(new TransactionEntity(id, date, time, timestamp, total, pay, change));

        List<TransactionItemEntity> entities = new ArrayList<>();
        for (CartItem item : items) {
            entities.add(new TransactionItemEntity(
                    UUID.randomUUID().toString(),
                    id,
                    item.getProduct().getId(),
                    item.getProduct().getName(),
                    item.getProduct().getPrice(),
                    item.getQty()
            ));
        }
        db.transactionDao().insertTransactionItems(entities);
        ProductRepository.reduceStock(context, items);
        return id;
    }

    private static List<TransactionRecord> getAllLocal(Context context) {
        List<TransactionRecord> list = new ArrayList<>();
        AppDatabase db = AppDatabase.getInstance(context);
        for (TransactionEntity t : db.transactionDao().getAllTransactions()) {
            List<CartItem> items = new ArrayList<>();
            for (TransactionItemEntity item : db.transactionDao().getItemsByTransactionId(t.id)) {
                items.add(new CartItem(
                        new Product(item.productId, item.productName, item.price, 0),
                        item.qty
                ));
            }
            list.add(new TransactionRecord(t.id, t.date, t.time, t.total, t.pay, t.changeAmount, items));
        }
        return list;
    }

    private static TransactionRecord getByIdLocal(Context context, String transactionId) {
        AppDatabase db = AppDatabase.getInstance(context);
        TransactionEntity t = db.transactionDao().getTransactionById(transactionId);
        if (t == null) return null;

        List<CartItem> items = new ArrayList<>();
        for (TransactionItemEntity item : db.transactionDao().getItemsByTransactionId(t.id)) {
            items.add(new CartItem(
                    new Product(item.productId, item.productName, item.price, 0),
                    item.qty
            ));
        }
        return new TransactionRecord(t.id, t.date, t.time, t.total, t.pay, t.changeAmount, items);
    }

    private static boolean deleteByIdLocal(Context context, String transactionId) {
        AppDatabase db = AppDatabase.getInstance(context);
        TransactionEntity transaction = db.transactionDao().getTransactionById(transactionId);
        if (transaction == null) return false;

        List<TransactionItemEntity> itemEntities = db.transactionDao().getItemsByTransactionId(transactionId);
        List<CartItem> items = new ArrayList<>();
        for (TransactionItemEntity item : itemEntities) {
            items.add(new CartItem(
                    new Product(item.productId, item.productName, item.price, 0),
                    item.qty
            ));
        }

        db.runInTransaction(() -> {
            for (CartItem item : items) {
                com.daspos.db.entity.ProductEntity product = db.productDao().getById(item.getProduct().getId());
                if (product != null) {
                    product.stock = product.stock + item.getQty();
                    db.productDao().update(product);
                }
            }
            db.transactionDao().deleteItemsByTransactionId(transactionId);
            db.transactionDao().deleteTransactionById(transactionId);
        });
        return true;
    }

    private static boolean deleteByIdSupabase(Context context, String transactionId) {
        try {
            TransactionRecord record = SupabaseTransactionService.getById(
                    getOutletId(context),
                    getAccessToken(context),
                    transactionId
            );
            if (record == null) return false;

            ProductRepository.increaseStock(context, record.getItems());
            boolean deleted = SupabaseTransactionService.deleteById(getOutletId(context), getAccessToken(context), transactionId);
            if (deleted) {
                AppDatabase.getInstance(context).transactionDao().deleteItemsByTransactionId(record.getId());
                AppDatabase.getInstance(context).transactionDao().deleteTransactionById(record.getId());
                ProductRepository.triggerSyncIfPossible(context);
            }
            return deleted;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean shouldUseSupabase(Context context) {
        boolean active = SupabaseConfig.isConfigured()
                && "supabase".equalsIgnoreCase(AuthSessionStore.getSource(context))
                && !getAccessToken(context).isEmpty()
                && !getOutletId(context).isEmpty()
                && !getUserId(context).isEmpty();
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

        prefs(context).edit().putLong(KEY_TRANSACTIONS_LAST_REFRESH, now).apply();
        DbExecutor.runAsync(() -> {
            try {
                syncPendingTransactionsNow(context);
                List<TransactionRecord> remote = SupabaseTransactionService.getAll(getOutletId(context), getAccessToken(context));
                replaceLocalTransactionCache(context, remote);
                markTransactionsRefreshed(context);
            } catch (Exception ignored) {
            }
        });
    }

    public static void triggerSyncIfPossible(Context context) {
        DbExecutor.runAsync(() -> {
            if (!shouldUseSupabase(context)) return;
            if (RestoreStateStore.shouldDelaySupabaseSync(context)) return;
            if (!NetworkUtils.isOnline(context)) return;
            try {
                syncPendingTransactionsNow(context);
                List<TransactionRecord> remote = SupabaseTransactionService.getAll(getOutletId(context), getAccessToken(context));
                replaceLocalTransactionCache(context, remote);
                markTransactionsRefreshed(context);
            } catch (Exception ignored) {
            }
        });
    }

    private static void syncPendingTransactionsNow(Context context) throws Exception {
        List<String> pending = PendingSyncStore.getPendingTransactions(context);
        if (pending.isEmpty()) return;

        for (String localId : pending) {
            TransactionRecord local = getByIdLocal(context, localId);
            if (local == null) {
                PendingSyncStore.removeTransaction(context, localId);
                continue;
            }

            SupabaseTransactionService.save(
                    getOutletId(context),
                    getUserId(context),
                    getAccessToken(context),
                    local.getItems(),
                    local.getTotal(),
                    local.getPay(),
                    local.getChange()
            );
            PendingSyncStore.removeTransaction(context, localId);
        }
    }

    private static void markTransactionsRefreshed(Context context) {
        prefs(context).edit().putLong(KEY_TRANSACTIONS_LAST_REFRESH, System.currentTimeMillis()).apply();
    }

    private static void replaceLocalTransactionCache(Context context, List<TransactionRecord> records) {
        AppDatabase db = AppDatabase.getInstance(context);
        db.runInTransaction(() -> {
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
                        record.getPay(),
                        record.getChange()
                ));
                List<TransactionItemEntity> items = new ArrayList<>();
                for (CartItem item : record.getItems()) {
                    items.add(new TransactionItemEntity(
                            UUID.randomUUID().toString(),
                            record.getId(),
                            item.getProduct().getId(),
                            item.getProduct().getName(),
                            item.getProduct().getPrice(),
                            item.getQty()
                    ));
                }
                db.transactionDao().insertTransactionItems(items);
            }
        });
    }

    private static void upsertLocalTransaction(Context context, TransactionRecord record) {
        AppDatabase db = AppDatabase.getInstance(context);
        db.runInTransaction(() -> {
            long timestamp = toTimestamp(record.getDate(), record.getTime());
            db.transactionDao().insertTransaction(new TransactionEntity(
                    record.getId(),
                    record.getDate(),
                    record.getTime(),
                    timestamp,
                    record.getTotal(),
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
                        item.getProduct().getName(),
                        item.getProduct().getPrice(),
                        item.getQty()
                ));
            }
            db.transactionDao().insertTransactionItems(items);
        });
    }

    private static long toTimestamp(String date, String time) {
        try {
            Date parsed = new SimpleDateFormat("dd MMM yyyy HH:mm", new Locale("id", "ID")).parse(date + " " + time);
            return parsed == null ? System.currentTimeMillis() : parsed.getTime();
        } catch (Exception ignored) {
            return System.currentTimeMillis();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(CACHE_PREF, Context.MODE_PRIVATE);
    }

    private static void ensureCacheBoundToCurrentOutlet(Context context, String outletId) {
        if (outletId == null || outletId.trim().isEmpty()) return;
        SharedPreferences preferences = prefs(context);
        String cachedOutletId = preferences.getString(KEY_TRANSACTIONS_CACHE_OUTLET_ID, "");
        if (outletId.equals(cachedOutletId)) return;

        Runnable clearTask = () -> {
            AppDatabase db = AppDatabase.getInstance(context);
            db.runInTransaction(() -> {
                db.transactionDao().deleteAllItems();
                db.transactionDao().deleteAllTransactions();
            });
            PendingSyncStore.clearPendingTransactions(context);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            DbExecutor.runBlocking(clearTask);
        } else {
            clearTask.run();
        }
        preferences.edit()
                .putString(KEY_TRANSACTIONS_CACHE_OUTLET_ID, outletId)
                .putLong(KEY_TRANSACTIONS_LAST_REFRESH, 0L)
                .apply();
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

    public static void saveAsync(final Context context, final List<CartItem> items, final double total, final double pay, final double change, final DbExecutor.SuccessCallback<String> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> save(context, items, total, pay, change), onSuccess, onError);
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
}
