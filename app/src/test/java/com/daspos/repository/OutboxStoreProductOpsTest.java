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

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class OutboxStoreProductOpsTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        OutboxStore.clearPendingProductOps(context);
    }

    @Test
    public void enqueueAndReadProductOps() {
        enqueueProductAdd("p1", "Gula", 15000, 12000, 50);
        enqueueProductUpdate("p2", "Beras", 12000, 10000, 20);
        OutboxStore.enqueueProductDelete(context, "p3");

        List<OutboxStore.ProductSyncOp> ops = OutboxStore.getPendingProductOps(context);
        assertEquals(3, ops.size());
        assertEquals("add", ops.get(0).op);
        assertEquals("p1", ops.get(0).id);
        assertEquals("Gula", ops.get(0).name);
        assertEquals(15000, ops.get(0).price, 0.001);
        assertEquals("update", ops.get(1).op);
        assertEquals("delete", ops.get(2).op);
        assertEquals("p3", ops.get(2).id);
    }

    @Test
    public void replaceProductOpsKeepsOnlyFailedOps() {
        enqueueProductAdd("p1", "A", 1, 1, 1);
        enqueueProductAdd("p2", "B", 2, 2, 2);
        enqueueProductAdd("p3", "C", 3, 3, 3);

        List<OutboxStore.ProductSyncOp> remaining = new ArrayList<>();
        for (OutboxStore.ProductSyncOp op : OutboxStore.getPendingProductOps(context)) {
            if (!"p2".equals(op.id)) remaining.add(op);
        }
        OutboxStore.replaceProductOps(context, remaining);

        List<OutboxStore.ProductSyncOp> ops = OutboxStore.getPendingProductOps(context);
        assertEquals(2, ops.size());
        assertEquals("p1", ops.get(0).id);
        assertEquals("p3", ops.get(1).id);

        OutboxStore.replaceProductOps(context, new ArrayList<>());
        assertTrue(OutboxStore.getPendingProductOps(context).isEmpty());
    }

    @Test
    public void legacyPriceEcerFieldIsTolerated() {
        // Ops written by older app versions may carry price_ecer instead of price.
        // rw's ensureLegacyMigrated copies the raw JSON payload into
        // sync_outbox, and fromOutboxEntity falls back to price_ecer.
        android.content.SharedPreferences prefs =
                context.getSharedPreferences("pending_sync", Context.MODE_PRIVATE);
        prefs.edit().putString("pending_product_ops",
                "[{\"op\":\"add\",\"id\":\"old1\",\"name\":\"Legacy\",\"price_ecer\":7777,\"stock\":5}]")
                .apply();

        List<OutboxStore.ProductSyncOp> ops = OutboxStore.getPendingProductOps(context);
        assertEquals(1, ops.size());
        assertEquals("old1", ops.get(0).id);
        assertEquals(7777, ops.get(0).price, 0.001);
        assertTrue(ops.get(0).trackStock);
        assertFalse(ops.get(0).name.isEmpty());
    }

    // rw's enqueueProductAdd/Update carry the full retail field set
    // (barcode, unit, pricing tiers, conversion factors); tests only vary
    // the core fields, so defaults mirror PendingSyncStore-era payloads.
    private void enqueueProductAdd(String id, String name, double price, double costPrice, int stock) {
        OutboxStore.enqueueProductAdd(
                context, id, name, "", price, costPrice, stock, "pcs", true,
                price, 0, 0, 0, 10, 20, 100);
    }

    private void enqueueProductUpdate(String id, String name, double price, double costPrice, int stock) {
        OutboxStore.enqueueProductUpdate(
                context, id, name, "", price, costPrice, stock, "pcs", true,
                price, 0, 0, 0, 10, 20, 100);
    }
}
