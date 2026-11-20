package com.daspos.db;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AppDatabaseMigrationTest {
    private static final String TEST_DB = "migration-test.db";

    @Test
    public void migrationFrom1To2PreservesExistingDataAndAddsTransactionTables() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(TEST_DB);

        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(TEST_DB)
                        .callback(new SupportSQLiteOpenHelper.Callback(1) {
                            @Override
                            public void onCreate(SupportSQLiteDatabase db) {
                                db.execSQL("CREATE TABLE IF NOT EXISTS `products` (`id` TEXT NOT NULL, `name` TEXT, `price` REAL NOT NULL, `stock` INTEGER NOT NULL, PRIMARY KEY(`id`))");
                                db.execSQL("CREATE TABLE IF NOT EXISTS `users` (`username` TEXT NOT NULL, `role` TEXT, `passwordHash` TEXT, PRIMARY KEY(`username`))");
                            }

                            @Override
                            public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                            }
                        })
                        .build()
        );

        SupportSQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("INSERT INTO products (id, name, price, stock) VALUES ('p1', 'Produk A', 12000, 7)");
        db.execSQL("INSERT INTO users (username, role, passwordHash) VALUES ('kasir', 'kasir', 'hash')");
        AppDatabase.MIGRATION_1_2.migrate(db);

        assertEquals(1, countRows(db, "products"));
        assertEquals(1, countRows(db, "users"));
        assertEquals(0, countRows(db, "transactions"));
        assertEquals(0, countRows(db, "transaction_items"));

        helper.close();
        context.deleteDatabase(TEST_DB);
    }

    @Test
    public void fullMigrationChain1To6PreservesDataAndYieldsValidRoomV6Schema() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(TEST_DB);

        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(TEST_DB)
                        .callback(new SupportSQLiteOpenHelper.Callback(1) {
                            @Override
                            public void onCreate(SupportSQLiteDatabase db) {
                                db.execSQL("CREATE TABLE IF NOT EXISTS `products` (`id` TEXT NOT NULL, `name` TEXT, `price` REAL NOT NULL, `stock` INTEGER NOT NULL, PRIMARY KEY(`id`))");
                                db.execSQL("CREATE TABLE IF NOT EXISTS `users` (`username` TEXT NOT NULL, `role` TEXT, `passwordHash` TEXT, PRIMARY KEY(`username`))");
                            }

                            @Override
                            public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                            }
                        })
                        .build()
        );

        SupportSQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("INSERT INTO products (id, name, price, stock) VALUES ('p1', 'Gula', 15000, 42)");
        db.execSQL("INSERT INTO users (username, role, passwordHash) VALUES ('admin@x.dev', 'admin', 'hash')");

        AppDatabase.MIGRATION_1_2.migrate(db);
        db.execSQL("INSERT INTO transactions (id, date, time, timestamp, total, pay, changeAmount) VALUES ('t1', '16 Nov 2026', '08:00', 123456789, 25000, 30000, 5000)");
        db.execSQL("INSERT INTO transaction_items (id, transactionId, productId, productName, price, qty) VALUES ('i1', 't1', 'p1', 'Kopi', 5000, 2)");

        // rw folds min's 5_6 + 7_8 into MIGRATION_4_5 and keeps rw's index
        // migration as MIGRATION_5_6; the chain ends at schema v6.
        AppDatabase.MIGRATION_2_3.migrate(db);
        AppDatabase.MIGRATION_3_4.migrate(db);
        AppDatabase.MIGRATION_4_5.migrate(db);
        AppDatabase.MIGRATION_5_6.migrate(db);

        assertEquals(
                new HashSet<>(Arrays.asList("id", "name", "barcode", "price", "costPrice", "stock", "unitName", "trackStock")),
                columnNames(db, "products"));
        assertEquals(
                new HashSet<>(Arrays.asList("id", "date", "time", "timestamp", "total", "discount", "pay", "changeAmount")),
                columnNames(db, "transactions"));
        assertEquals(
                new HashSet<>(Arrays.asList("id", "transactionId", "productId", "productName", "price", "qty")),
                columnNames(db, "transaction_items"));
        assertEquals(
                new HashSet<>(Arrays.asList("id", "entityType", "operation", "entityId", "payloadJson", "createdAt", "attemptCount", "nextRetryAt", "lastError")),
                columnNames(db, "sync_outbox"));

        assertTrue(indexNames(db, "products").contains("index_products_name"));
        assertTrue(indexNames(db, "transactions").contains("index_transactions_timestamp"));
        assertTrue(indexNames(db, "transaction_items").containsAll(Arrays.asList(
                "index_transaction_items_transactionId",
                "index_transaction_items_productId"
        )));
        assertTrue(indexNames(db, "sync_outbox").containsAll(Arrays.asList(
                "index_sync_outbox_entityType_entityId",
                "index_sync_outbox_createdAt",
                "index_sync_outbox_entityType_nextRetryAt"
        )));

        Cursor cursor = db.query("SELECT price, costPrice, stock, trackStock, unitName FROM products WHERE id = 'p1'");
        try {
            cursor.moveToFirst();
            assertEquals(15000, cursor.getDouble(0), 0.001);
            assertEquals(0, cursor.getDouble(1), 0.001);
            assertEquals(42, cursor.getInt(2));
            assertEquals(1, cursor.getInt(3));
            assertEquals("pcs", cursor.getString(4));
        } finally {
            cursor.close();
        }

        assertEquals(1, countRows(db, "users"));
        assertEquals(1, countRows(db, "transactions"));
        assertEquals(1, countRows(db, "transaction_items"));

        db.execSQL("PRAGMA user_version = 6");
        helper.close();

        // Verify Room's strict schema validator accepts the migrated v6 database.
        AppDatabase roomDb = Room.databaseBuilder(context, AppDatabase.class, TEST_DB)
                .allowMainThreadQueries()
                .build();
        try {
            assertEquals(1, roomDb.productDao().count());
            assertEquals(1, roomDb.transactionDao().count());
            assertEquals(1, roomDb.userDao().count());
        } finally {
            roomDb.close();
            context.deleteDatabase(TEST_DB);
        }
    }

    private static Set<String> indexNames(SupportSQLiteDatabase db, String table) {
        Cursor cursor = db.query("PRAGMA index_list(" + table + ")");
        Set<String> indices = new HashSet<>();
        try {
            while (cursor.moveToNext()) {
                indices.add(cursor.getString(1));
            }
        } finally {
            cursor.close();
        }
        return indices;
    }

    private static Set<String> columnNames(SupportSQLiteDatabase db, String table) {
        Cursor cursor = db.query("PRAGMA table_info(" + table + ")");
        Set<String> columns = new HashSet<>();
        try {
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(1));
            }
        } finally {
            cursor.close();
        }
        return columns;
    }

    private static int countRows(SupportSQLiteDatabase db, String table) {
        Cursor cursor = db.query("SELECT COUNT(*) FROM " + table);
        try {
            cursor.moveToFirst();
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }
}
