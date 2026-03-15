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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TransactionRepository {
    public static void save(final Context context, final List<CartItem> items, final double total, final double pay, final double change) {
        DbExecutor.runBlocking(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            int next = db.transactionDao().count() + 1;
            String id = "#TRX" + String.format(Locale.getDefault(), "%03d", next);
            String date = new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID")).format(new Date());
            String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            long timestamp = System.currentTimeMillis();

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

    public static TransactionRecord getLast(Context context) {
        List<TransactionRecord> list = getAll(context);
        return list.isEmpty() ? null : list.get(0);
    }

    public static List<ReportItem> getReportItemsByPeriod(Context context, Calendar selectedCalendar, boolean monthly) {
        List<ReportItem> filtered = new ArrayList<>();
        for (TransactionRecord t : getAll(context)) {
            Calendar trxCal = parseTransactionCalendar(t.getDate(), t.getTime());
            if (trxCal == null) continue;
            boolean match = monthly
                    ? trxCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR)
                    && trxCal.get(Calendar.MONTH) == selectedCalendar.get(Calendar.MONTH)
                    : trxCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR)
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
}
