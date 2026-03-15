package com.daspos;

import android.app.Application;

import com.daspos.db.AppDatabase;
import com.daspos.db.RoomMigrationHelper;

public class DasPosApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppDatabase.getInstance(this);
        RoomMigrationHelper.seedFromLegacyStorageIfNeeded(this);
    }
}
