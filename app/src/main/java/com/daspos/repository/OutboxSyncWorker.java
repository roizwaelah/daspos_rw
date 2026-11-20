package com.daspos.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.daspos.db.AppDatabase;
import com.daspos.db.dao.OutboxDao;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.NetworkUtils;
import com.daspos.shared.util.RestoreStateStore;

public class OutboxSyncWorker extends Worker {
    private static final String TAG = "OutboxSyncWorker";

    public OutboxSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context appContext = getApplicationContext();
        if (RestoreStateStore.shouldDelaySupabaseSync(appContext)) {
            OutboxSyncScheduler.enqueueImmediate(appContext, 30_000L);
            return Result.success();
        }
        if (!NetworkUtils.isOnline(appContext)) {
            return Result.retry();
        }

        try {
            // Sinkronkan mutasi produk terlebih dahulu sebelum transaksi penjualan
            // agar referensi produk baru yang dibuat offline sudah tersedia di server.
            ProductRepository.flushPendingProductOpsForWorker(appContext);
            TransactionRepository.flushPendingTransactionsForWorker(appContext);
        } catch (Exception e) {
            Log.w(TAG, "Gagal menjalankan OutboxSyncWorker", e);
            return Result.retry();
        }

        scheduleRemainingIfNeeded(appContext);
        return Result.success();
    }

    private void scheduleRemainingIfNeeded(Context appContext) {
        try {
            long[] state = DbExecutor.runBlocking(() -> {
                OutboxDao dao = AppDatabase.getInstance(appContext).outboxDao();
                int remaining = dao.countAll();
                Long earliestRetry = remaining > 0 ? dao.getEarliestNextRetryAt() : null;
                return new long[]{remaining, earliestRetry == null ? 0L : earliestRetry};
            });

            int remaining = (int) state[0];
            long earliestRetry = state[1];
            if (remaining <= 0) {
                return;
            }

            long now = System.currentTimeMillis();
            long delayMs = Math.max(0L, earliestRetry - now);
            OutboxSyncScheduler.enqueueImmediate(appContext, delayMs);
        } catch (Exception e) {
            Log.w(TAG, "Gagal mengecek sisa antrean Outbox di worker", e);
        }
    }
}
