package com.daspos.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.ConnectivityManager;

import androidx.test.core.app.ApplicationProvider;

import com.daspos.db.AppDatabase;
import com.daspos.db.entity.ProductEntity;
import com.daspos.db.entity.TransactionEntity;
import com.daspos.db.entity.TransactionItemEntity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.SalesUnit;
import com.daspos.model.TransactionRecord;
import com.daspos.remote.RemoteDataProvider;
import com.daspos.remote.RemoteDataProviderFactory;
import com.daspos.shared.util.DbExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowConnectivityManager;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;

@RunWith(RobolectricTestRunner.class)
public class TransactionSyncEngineTest {
    private Context context;
    private AppDatabase db;
    private FakeRemoteProvider provider;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AppDatabase.resetInstanceForTesting();
        db = AppDatabase.getInstance(context);
        clearDatabase();
        OutboxStore.clearPendingTransactions(context);
        OutboxStore.clearPendingProductOps(context);

        AuthSessionStore.setDatabaseMode(context, AuthSessionStore.DATABASE_MODE_REMOTE);
        AuthSessionStore.setRemoteProviderType(context, AuthSessionStore.PROVIDER_SUPABASE);
        AuthSessionStore.saveSession(
                context,
                "kasir@test.dev",
                "kasir",
                "user-1",
                "access-token",
                "refresh-token",
                "supabase",
                "outlet-1"
        );

        provider = new FakeRemoteProvider();
        RemoteDataProviderFactory.setProviderOverrideForTesting(provider);
    }

    @After
    public void tearDown() {
        RemoteDataProviderFactory.setProviderOverrideForTesting(null);
        clearDatabase();
        OutboxStore.clearPendingTransactions(context);
    }

    @Test
    public void pendingTransactionIsUploadedThenRemoved() throws Exception {
        insertLocalTransaction("2606010001");
        OutboxStore.enqueueTransaction(context, "2606010001");

        invokeSyncPending();

        assertEquals(1, provider.savedTransactions.size());
        assertTrue(provider.savedTransactions.get(0).clientTransactionId.contains(":2606010001"));
        assertTrue(OutboxStore.getPendingTransactions(context).isEmpty());
        // Local row is re-keyed to the remote trx_no so incremental refresh
        // cannot surface a duplicate of the same sale.
        assertNull(io(() -> db.transactionDao().getTransactionById("2606010001")));
        assertNotNull(io(() -> db.transactionDao().getTransactionById("REMOTE-1")));
    }

    @Test
    public void failedUploadKeepsPendingEntry() throws Exception {
        insertLocalTransaction("2606010002");
        OutboxStore.enqueueTransaction(context, "2606010002");
        provider.failSaves = true;

        invokeSyncPending();

        assertEquals(1, provider.saveAttempts);
        assertTrue(provider.savedTransactions.isEmpty());
        List<String> pending = OutboxStore.getPendingTransactions(context);
        assertEquals(1, pending.size());
        assertEquals("2606010002", pending.get(0));
    }

    @Test
    public void missingLocalTransactionIsDroppedFromQueue() throws Exception {
        OutboxStore.enqueueTransaction(context, "ghost-id");

        invokeSyncPending();

        assertTrue(provider.savedTransactions.isEmpty());
        assertTrue(OutboxStore.getPendingTransactions(context).isEmpty());
    }

    @Test
    public void cacheRebuildPreservesPendingTransactionsAndItems() throws Exception {
        insertLocalTransaction("2606010003");
        OutboxStore.enqueueTransaction(context, "2606010003");
        insertLocalTransaction("2606010004"); // synced row, not pending

        List<TransactionRecord> remote = new ArrayList<>();
        remote.add(remoteRecord("REMOTE-1"));
        invokeReplaceCache(remote);

        // Pending transaction survives the wipe.
        assertNotNull(io(() -> db.transactionDao().getTransactionById("2606010003")));
        assertEquals(1, io(() -> db.transactionDao().getItemsByTransactionId("2606010003")).size());
        // Non-pending local row is replaced by remote data.
        assertNull(io(() -> db.transactionDao().getTransactionById("2606010004")));
        assertNotNull(io(() -> db.transactionDao().getTransactionById("REMOTE-1")));
        assertEquals(1, io(() -> db.transactionDao().getItemsByTransactionId("REMOTE-1")).size());
    }

    @Test
    public void cacheRebuildPrefersRemoteRowWhenIdCollides() throws Exception {
        insertLocalTransaction("2606010005");
        OutboxStore.enqueueTransaction(context, "2606010005");

        List<TransactionRecord> remote = new ArrayList<>();
        remote.add(remoteRecord("2606010005"));
        invokeReplaceCache(remote);

        // Exactly one row for the colliding id, sourced from remote.
        assertEquals(1, io(() -> db.transactionDao().getAllTransactions()).size());
        assertEquals(99999, io(() -> db.transactionDao().getTransactionById("2606010005")).total, 0.001);
    }

    @Test
    public void offlineCheckoutIsSavedLocallyAndQueued() {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        ShadowConnectivityManager shadowCm = Shadows.shadowOf(cm);
        shadowCm.setActiveNetworkInfo(null);
        // First remote-mode call binds the product cache to the outlet and
        // wipes it once; do it before seeding so prod-1 survives.
        ProductRepository.getById(context, "warmup");
        io(() -> {
            db.productDao().insertAll(Collections.singletonList(
                    new ProductEntity("prod-1", "Gula", "", 5000, 4000, 10, "pcs", true)));
            return null;
        });
        List<CartItem> items = Collections.singletonList(
                new CartItem(product("prod-1", "Gula", 5000, 4000, 10, true), 2));

        String id = TransactionRepository.save(context, items, 10000, 20000, 10000);

        assertNotNull(id);
        assertTrue(OutboxStore.getPendingTransactions(context).contains(id));
        ProductEntity product = io(() -> db.productDao().getById("prod-1"));
        assertEquals(8, product.stock);
    }

    @Test
    public void localTransactionIdsNeverCollideAfterRestore() {
        AuthSessionStore.setDatabaseMode(context, AuthSessionStore.DATABASE_MODE_LOCAL);
        List<CartItem> items = Collections.singletonList(
                new CartItem(product("prod-x", "Item", 1000, 0, 0, false), 1));

        // Simulate the collision the audit described: exactly one transaction
        // exists this month but its id is "+0002", so the naive count+1
        // sequence regenerates an existing primary key.
        String today = new SimpleDateFormat("yyMMdd", Locale.getDefault()).format(new Date());
        io(() -> {
            db.transactionDao().insertTransaction(new TransactionEntity(
                    today + "0002", "01 Jan 2026", "00:00", System.currentTimeMillis(), 777, 777, 0));
            return null;
        });

        String first = TransactionRepository.save(context, items, 1000, 1000, 0);
        String second = TransactionRepository.save(context, items, 1000, 1000, 0);

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(!first.equals(second));
        // The pre-existing row must not be overwritten.
        assertEquals(777, io(() -> db.transactionDao().getTransactionById(today + "0002")).total, 0.001);
        assertNotNull(io(() -> db.transactionDao().getTransactionById(first)));
        assertNotNull(io(() -> db.transactionDao().getTransactionById(second)));
    }

    private void insertLocalTransaction(String id) {
        io(() -> {
            db.transactionDao().insertTransaction(new TransactionEntity(
                    id, "16 Nov 2026", "10:00", System.currentTimeMillis(), 25000, 30000, 5000));
            db.transactionDao().insertTransactionItems(Collections.singletonList(
                    new TransactionItemEntity(UUID.randomUUID().toString(), id, "prod-1", "Gula", 5000, 5)));
            return null;
        });
    }

    private TransactionRecord remoteRecord(String id) {
        Product p = product("prod-1", "Gula", 5000, 4000, 10, true);
        return new TransactionRecord(
                id, "16 Nov 2026", "11:00", 99999, 100000, 1,
                Collections.singletonList(new CartItem(p, 3, SalesUnit.ECER, 1, 5000)));
    }

    // rw's Product has no (id,name,price,costPrice,stock,trackStock) ctor;
    // build via the 4-arg ctor plus setters to mirror min's semantics.
    private static Product product(String id, String name, double price, double costPrice, int stock, boolean trackStock) {
        Product p = new Product(id, name, price, stock);
        p.setCostPrice(costPrice);
        p.setTrackStock(trackStock);
        return p;
    }

    private void invokeSyncPending() throws Exception {
        io(() -> {
            try {
                Method m = TransactionRepository.class.getDeclaredMethod("syncPendingTransactionsNow", Context.class);
                m.setAccessible(true);
                m.invoke(null, context);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private void invokeReplaceCache(List<TransactionRecord> records) throws Exception {
        io(() -> {
            try {
                Method m = TransactionRepository.class.getDeclaredMethod(
                        "replaceLocalTransactionCache", Context.class, List.class);
                m.setAccessible(true);
                m.invoke(null, context, records);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private <T> T io(Callable<T> task) {
        return DbExecutor.runBlocking(task);
    }

    private void clearDatabase() {
        io(() -> {
            db.transactionDao().deleteAllItems();
            db.transactionDao().deleteAllTransactions();
            db.productDao().deleteAll();
            db.userDao().deleteAll();
            return null;
        });
    }

    private static final class FakeRemoteProvider implements RemoteDataProvider {
        final List<SavedTransaction> savedTransactions = new ArrayList<>();
        int saveAttempts;
        boolean failSaves;

        @Override public String getType() { return "fake"; }
        @Override public boolean isConfigured(Context context) { return true; }

        @Override public void addProduct(String outletId, String accessToken, Product product) { }
        @Override public void updateProduct(String outletId, String accessToken, Product product) { }
        @Override public void deleteProduct(String outletId, String accessToken, String productId) { }
        @Override public boolean hasEnoughStock(String outletId, String accessToken, List<CartItem> items) { return true; }
        @Override public void reduceStock(String outletId, String accessToken, List<CartItem> items) { }
        @Override public void increaseStock(String outletId, String accessToken, List<CartItem> items) { }
        @Override public List<Product> getAllProducts(String outletId, String accessToken) { return Collections.emptyList(); }
        @Override public Product getProductById(String outletId, String accessToken, String productId) { return null; }

        @Override
        public String saveTransaction(String outletId, String userId, String accessToken,
                                      List<CartItem> items, double total, double discount,
                                      double pay, double change,
                                      String clientTransactionId) {
            saveAttempts++;
            if (failSaves) throw new RuntimeException("simulated upload failure");
            savedTransactions.add(new SavedTransaction(clientTransactionId, items.size()));
            return "REMOTE-" + savedTransactions.size();
        }

        @Override public List<TransactionRecord> getAllTransactions(String outletId, String accessToken) { return Collections.emptyList(); }
        @Override public TransactionRecord getTransactionById(String outletId, String accessToken, String transactionId) { return null; }
        @Override public boolean deleteTransactionById(String outletId, String accessToken, String transactionId) { return false; }
        @Override public List<TransactionRecord> getTransactionsForReportPeriod(String outletId, String accessToken, Calendar selectedCalendar, boolean monthly) { return Collections.emptyList(); }
        @Override public CreateCashierResult createCashierByAdmin(String accessToken, String outletId, String username, String password, String displayName, List<String> allowedMenus) { return new CreateCashierResult(false, "n/a"); }
        @Override public ListCashiersResult listCashiersByAdmin(String accessToken) { return new ListCashiersResult(false, Collections.emptyList()); }
    }

    private static final class SavedTransaction {
        final String clientTransactionId;
        final int itemCount;
        SavedTransaction(String clientTransactionId, int itemCount) {
            this.clientTransactionId = clientTransactionId;
            this.itemCount = itemCount;
        }
    }
}
