package com.daspos.db;

import android.content.Context;

import com.daspos.db.entity.ProductEntity;
import com.daspos.db.entity.TransactionEntity;
import com.daspos.db.entity.TransactionItemEntity;
import com.daspos.db.entity.UserEntity;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.TransactionRecord;
import com.daspos.model.User;
import com.daspos.repository.legacy.LegacySeedRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class RoomMigrationHelper {
    public static void seedFromLegacyStorageIfNeeded(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        if (db.productDao().count() == 0) {
            List<ProductEntity> entities = new ArrayList<>();
            for (Product p : LegacySeedRepository.getProducts(context)) {
                entities.add(RoomMapper.toEntity(p));
            }
            db.productDao().insertAll(entities);
        }

        if (db.userDao().count() == 0) {
            List<UserEntity> entities = new ArrayList<>();
            for (User u : LegacySeedRepository.getUsers(context)) {
                entities.add(RoomMapper.toEntity(u));
            }
            db.userDao().insertAll(entities);
        }

        if (db.transactionDao().count() == 0) {
            List<TransactionRecord> records = LegacySeedRepository.getTransactions(context);
            for (TransactionRecord record : records) {
                long ts = System.currentTimeMillis();
                try {
                    ts = new SimpleDateFormat("dd MMM yyyy HH:mm", new Locale("id", "ID"))
                            .parse(record.getDate() + " " + record.getTime())
                            .getTime();
                } catch (Exception ignored) { }
                db.transactionDao().insertTransaction(new TransactionEntity(
                        record.getId(),
                        record.getDate(),
                        record.getTime(),
                        ts,
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
        }
    }
}
