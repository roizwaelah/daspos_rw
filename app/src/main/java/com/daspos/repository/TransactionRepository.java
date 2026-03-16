package com.daspos.repository;

import android.content.Context;

import com.daspos.db.AppDatabase;
import com.daspos.db.entity.TransactionEntity;
import com.daspos.db.entity.TransactionItemEntity;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.ReportItem;
import com.daspos.model.TransactionRecord;
import com.daspos.shared.util.DbExecutor;

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
    public static String save(final Context context, final List<CartItem> items, final double total, final double pay, final double change) {
        return DbExecutor.runBlocking(() -> {
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
        });
    }

    public static List<TransactionRecord> getAll(final Context context) {
        return DbExecutor.runBlocking(() -> {
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
        });
    }



    public static TransactionRecord getById(final Context context, final String transactionId) {
        if (transactionId == null || transactionId.trim().isEmpty()) return null;
        return DbExecutor.runBlocking(() -> {
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
        });
    }

    public static TransactionRecord getLast(Context context) {
        List<TransactionRecord> list = getAll(context);
        return list.isEmpty() ? null : list.get(0);
    }

    public static List<ReportItem> getReportItemsByPeriod(Context context, Calendar selectedCalendar, boolean monthly) {
        List<ReportItem> filtered = new ArrayList<>();
        if (monthly) {
            Map<Long, DailyTransactionSummary> summaries = new HashMap<>();
            for (TransactionRecord t : getAll(context)) {
                Calendar trxCal = parseTransactionCalendar(t.getDate(), t.getTime());
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

        for (TransactionRecord t : getAll(context)) {
            Calendar trxCal = parseTransactionCalendar(t.getDate(), t.getTime());
            if (trxCal == null) continue;
            boolean match = trxCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR)
                    && trxCal.get(Calendar.MONTH) == selectedCalendar.get(Calendar.MONTH)
                    && trxCal.get(Calendar.DAY_OF_MONTH) == selectedCalendar.get(Calendar.DAY_OF_MONTH);
            if (match) filtered.add(new ReportItem(t.getId(), t.getDate(), t.getTime(), t.getTotal()));
        }
        return filtered;
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
}
