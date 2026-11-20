package com.daspos.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.daspos.db.dao.OutboxDao;
import com.daspos.db.dao.ProductDao;
import com.daspos.db.dao.TransactionDao;
import com.daspos.db.dao.UserDao;
import com.daspos.db.entity.OutboxEntity;
import com.daspos.db.entity.ProductEntity;
import com.daspos.db.entity.TransactionEntity;
import com.daspos.db.entity.TransactionItemEntity;
import com.daspos.db.entity.UserEntity;

@Database(
        entities = {
                OutboxEntity.class,
                ProductEntity.class,
                UserEntity.class,
                TransactionEntity.class,
                TransactionItemEntity.class
        },
        version = 6,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;
    // Run Room-internal work inline on the calling thread so every SQLite
    // access stays on a single connection-owning thread.
    private static final java.util.concurrent.Executor DIRECT_EXECUTOR =
            command -> command.run();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `date` TEXT, `time` TEXT, `timestamp` INTEGER NOT NULL, `total` REAL NOT NULL, `pay` REAL NOT NULL, `changeAmount` REAL NOT NULL, PRIMARY KEY(`id`))");
            db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_items` (`id` TEXT NOT NULL, `transactionId` TEXT, `productId` TEXT, `productName` TEXT, `price` REAL NOT NULL, `qty` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `products` ADD COLUMN `barcode` TEXT");
            db.execSQL("ALTER TABLE `products` ADD COLUMN `costPrice` REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `products` ADD COLUMN `unitName` TEXT");
            db.execSQL("ALTER TABLE `products` ADD COLUMN `trackStock` INTEGER NOT NULL DEFAULT 1");
            db.execSQL("UPDATE `products` SET `barcode` = '' WHERE `barcode` IS NULL");
            db.execSQL("UPDATE `products` SET `unitName` = 'pcs' WHERE `unitName` IS NULL OR TRIM(`unitName`) = ''");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `discount` REAL NOT NULL DEFAULT 0");
        }
    };

    // Folds min's MIGRATION_5_6 + MIGRATION_7_8: creates sync_outbox with the
    // final schema (incl. nextRetryAt) and all indices in a single step.
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entityType` TEXT NOT NULL, `operation` TEXT NOT NULL, `entityId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `attemptCount` INTEGER NOT NULL DEFAULT 0, `nextRetryAt` INTEGER NOT NULL DEFAULT 0, `lastError` TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_entityType_entityId` ON `sync_outbox` (`entityType`, `entityId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_createdAt` ON `sync_outbox` (`createdAt`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_entityType_nextRetryAt` ON `sync_outbox` (`entityType`, `nextRetryAt`)");
        }
    };

    // rw-specific: performance indices on existing tables (min's MIGRATION_6_7 SQL).
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_items_transactionId` ON `transaction_items` (`transactionId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_items_productId` ON `transaction_items` (`productId`)");
        }
    };

    public abstract OutboxDao outboxDao();
    public abstract ProductDao productDao();
    public abstract UserDao userDao();
    public abstract TransactionDao transactionDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "daspos_room.db"
                            )
                                .addMigrations(
                                        MIGRATION_1_2,
                                        MIGRATION_2_3,
                                        MIGRATION_3_4,
                                        MIGRATION_4_5,
                                        MIGRATION_5_6
                                )
                                .setQueryExecutor(DIRECT_EXECUTOR)
                                .setTransactionExecutor(DIRECT_EXECUTOR)
                                .build();
                }
            }
        }
        return INSTANCE;
    }

    /** Test hook: drops the singleton so the next getInstance() reopens the DB. */
    public static void resetInstanceForTesting() {
        synchronized (AppDatabase.class) {
            if (INSTANCE != null) {
                try {
                    INSTANCE.close();
                } catch (Throwable ignored) {
                    // Stale native connection under Robolectric; discard anyway.
                }
                INSTANCE = null;
            }
        }
    }
}
