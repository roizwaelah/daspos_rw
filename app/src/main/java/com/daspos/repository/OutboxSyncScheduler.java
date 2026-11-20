package com.daspos.repository;

import android.content.Context;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class OutboxSyncScheduler {
    private static final String TAG = "OutboxSyncScheduler";
    static final String UNIQUE_PERIODIC_WORK_NAME = "daspos_outbox_periodic_sync";
    static final String UNIQUE_IMMEDIATE_WORK_NAME = "daspos_outbox_immediate_sync";

    private OutboxSyncScheduler() {
    }

    public static void schedulePeriodic(Context context) {
        if (context == null) return;
        try {
            Context appContext = context.getApplicationContext();
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                    OutboxSyncWorker.class,
                    15,
                    TimeUnit.MINUTES
            )
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .build();

            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
            );
        } catch (IllegalStateException e) {
            Log.w(TAG, "WorkManager belum diinisialisasi saat schedulePeriodic", e);
        }
    }

    public static void enqueueImmediate(Context context, long delayMillis) {
        if (context == null) return;
        try {
            Context appContext = context.getApplicationContext();
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(OutboxSyncWorker.class)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS);

            if (delayMillis > 0L) {
                builder.setInitialDelay(delayMillis, TimeUnit.MILLISECONDS);
            }

            WorkManager.getInstance(appContext).enqueueUniqueWork(
                    UNIQUE_IMMEDIATE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    builder.build()
            );
        } catch (IllegalStateException e) {
            Log.w(TAG, "WorkManager belum diinisialisasi saat enqueueImmediate", e);
        }
    }
}
