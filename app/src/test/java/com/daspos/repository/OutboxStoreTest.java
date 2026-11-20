package com.daspos.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class OutboxStoreTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        OutboxStore.clearPendingTransactions(context);
        OutboxStore.clearPendingProductOps(context);
    }

    @Test
    public void enqueueTransactionDeduplicatesAndRemovesLocalIds() {
        OutboxStore.enqueueTransaction(context, "2606020001");
        OutboxStore.enqueueTransaction(context, "2606020001");
        OutboxStore.enqueueTransaction(context, "2606020002");

        List<String> pending = OutboxStore.getPendingTransactions(context);
        assertEquals(2, pending.size());
        assertEquals("2606020001", pending.get(0));
        assertEquals("2606020002", pending.get(1));

        OutboxStore.removeTransaction(context, "2606020001");

        pending = OutboxStore.getPendingTransactions(context);
        assertEquals(1, pending.size());
        assertEquals("2606020002", pending.get(0));
    }

    @Test
    public void offlineClientTransactionIdUsesStableInstallPrefix() {
        String first = TransactionRepository.offlineClientTransactionId(context, "2606020001");
        String retry = TransactionRepository.offlineClientTransactionId(context, "2606020001");
        String second = TransactionRepository.offlineClientTransactionId(context, "2606020002");

        assertEquals(first, retry);
        assertTrue(first.endsWith(":2606020001"));
        assertTrue(second.endsWith(":2606020002"));
        assertEquals(first.substring(0, first.indexOf(':')), second.substring(0, second.indexOf(':')));
    }

    @Test
    public void computeNextRetryAtScalesExponentiallyAndCapsAtMax() {
        long now = 1_000_000L;
        assertEquals(now + 2_000L, OutboxStore.computeNextRetryAt(0, now));
        assertEquals(now + 4_000L, OutboxStore.computeNextRetryAt(1, now));
        assertEquals(now + 8_000L, OutboxStore.computeNextRetryAt(2, now));
        assertEquals(now + 300_000L, OutboxStore.computeNextRetryAt(15, now));
    }

    @Test
    public void recordFailureDefersDueRetrievalUntilBackoffExpires() {
        OutboxStore.enqueueTransaction(context, "2606020010");
        assertEquals(1, OutboxStore.getDuePendingTransactions(context, 10).size());

        OutboxStore.recordTransactionSyncFailure(context, "2606020010", "timeout");

        // Masih di dalam jendela backoff minimal 2 detik, belum due
        assertTrue(OutboxStore.getDuePendingTransactions(context, 10).isEmpty());
        // Tetap tercatat di antrean pending total
        assertEquals(1, OutboxStore.getPendingTransactionCount(context));
    }

    @Test
    public void getDuePendingTransactionsRespectsBatchLimit() {
        for (int i = 1; i <= 5; i++) {
            OutboxStore.enqueueTransaction(context, "26060200" + i);
        }

        List<String> batch = OutboxStore.getDuePendingTransactions(context, 3);
        assertEquals(3, batch.size());
        assertEquals("260602001", batch.get(0));
        assertEquals("260602002", batch.get(1));
        assertEquals("260602003", batch.get(2));
        assertFalse(batch.contains("260602004"));
    }
}
