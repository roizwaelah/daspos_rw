package com.daspos;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.StrictMode;

import com.daspos.db.AppDatabase;
import com.daspos.db.RoomMigrationHelper;
import com.daspos.repository.OutboxSyncScheduler;
import com.daspos.repository.ProductRepository;
import com.daspos.repository.TransactionRepository;
import com.daspos.shared.util.DbExecutor;
import com.daspos.supabase.SupabaseConfig;

public class DasPosApp extends Application {
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build());
        }
        SupabaseConfig.init(this);
        // Lazy init: buka Room DB + seed migrasi + picu sync di background executor
        // agar onCreate tidak melakukan disk I/O di main thread (startup jank).
        DbExecutor.runAsync(() -> {
            AppDatabase.getInstance(getApplicationContext());
            RoomMigrationHelper.seedFromLegacyStorageIfNeeded(getApplicationContext());
            ProductRepository.triggerSyncIfPossible(getApplicationContext());
            TransactionRepository.triggerSyncIfPossible(getApplicationContext());
            registerNetworkSyncTrigger();
        });
        OutboxSyncScheduler.schedulePeriodic(this);
    }

    // Event-driven sync trigger: fires once when connectivity is (re)gained so
    // the outbox drains without polling on every Activity transition. The
    // 15-minute periodic work remains as a safety-net fallback.
    private void registerNetworkSyncTrigger() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Context app = getApplicationContext();
                DbExecutor.runAsync(() -> {
                    OutboxSyncScheduler.enqueueImmediate(app, 0L);
                    ProductRepository.triggerSyncIfPossible(app);
                    TransactionRepository.triggerSyncIfPossible(app);
                });
            }
        };
        cm.registerDefaultNetworkCallback(networkCallback);
    }
}
