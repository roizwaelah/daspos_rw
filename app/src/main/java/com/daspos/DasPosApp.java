package com.daspos;

import android.app.Application;

import com.daspos.db.AppDatabase;
import com.daspos.db.RoomMigrationHelper;
import com.daspos.repository.ProductRepository;
import com.daspos.repository.TransactionRepository;
import com.daspos.shared.util.DbExecutor;

public class DasPosApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppDatabase.getInstance(this);
        DbExecutor.runBlocking(() -> RoomMigrationHelper.seedFromLegacyStorageIfNeeded(getApplicationContext()));
        ProductRepository.triggerSyncIfPossible(getApplicationContext());
        TransactionRepository.triggerSyncIfPossible(getApplicationContext());
    }
}
