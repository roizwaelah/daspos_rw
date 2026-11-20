package com.daspos.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.daspos.db.AppDatabase;
import com.daspos.db.entity.UserEntity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.TransactionRecord;
import com.daspos.remote.RemoteDataProvider;
import com.daspos.remote.RemoteDataProviderFactory;
import com.daspos.shared.util.DbExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

@RunWith(RobolectricTestRunner.class)
public class UserRepositoryCashierSyncTest {
    private Context context;
    private AppDatabase db;
    private FakeCashierProvider provider;

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
                "admin@test.dev",
                "admin",
                "admin-1",
                "access-token",
                "refresh-token",
                "supabase",
                "outlet-1"
        );

        provider = new FakeCashierProvider();
        RemoteDataProviderFactory.setProviderOverrideForTesting(provider);
    }

    @After
    public void tearDown() {
        RemoteDataProviderFactory.setProviderOverrideForTesting(null);
        clearDatabase();
    }

    @Test
    public void deleteRemoteCashierRemovesLocalRow() {
        insertUser("kasir1@test.dev", "kasir");

        UserRepository.delete(context, "kasir1@test.dev");

        assertEquals(Collections.singletonList("kasir1@test.dev"), provider.deletedEmails);
        assertNull(io(() -> db.userDao().getByUsername("kasir1@test.dev")));
    }

    @Test
    public void failedRemoteDeleteKeepsLocalRow() {
        insertUser("kasir2@test.dev", "kasir");
        provider.failDeletes = true;

        try {
            UserRepository.delete(context, "kasir2@test.dev");
            fail("expected delete to fail");
        } catch (IllegalStateException expected) {
            // remote failure must propagate
        }

        assertNotNull(io(() -> db.userDao().getByUsername("kasir2@test.dev")));
    }

    @Test
    public void deleteLocalOnlyUsernameSkipsRemoteCall() {
        insertUser("kasir-lokal", "kasir");

        UserRepository.delete(context, "kasir-lokal");

        assertTrue(provider.deletedEmails.isEmpty());
        assertNull(io(() -> db.userDao().getByUsername("kasir-lokal")));
    }

    @Test
    public void cashierSyncPrunesStaleRemoteAccounts() {
        insertUser("old@test.dev", "kasir");       // deleted remotely -> prune
        insertUser("new@test.dev", "kasir");       // still remote -> keep
        insertUser("lain@test.dev", "admin");      // non-kasir -> keep
        insertUser("admin@test.dev", "kasir");     // session user -> keep
        provider.cashiers = Collections.singletonList(
                new RemoteDataProvider.RemoteCashier("new@test.dev", "kasir", Collections.emptyList()));

        List<com.daspos.model.User> users = UserRepository.getAll(context);

        assertTrue(users.stream().anyMatch(u -> "new@test.dev".equals(u.getUsername())));
        assertTrue(users.stream().anyMatch(u -> "lain@test.dev".equals(u.getUsername())));
        assertTrue(users.stream().anyMatch(u -> "admin@test.dev".equals(u.getUsername())));
        assertTrue(users.stream().noneMatch(u -> "old@test.dev".equals(u.getUsername())));
        assertNull(io(() -> db.userDao().getByUsername("old@test.dev")));
    }

    private void insertUser(String username, String role) {
        io(() -> {
            db.userDao().insert(new UserEntity(username, role, "hash"));
            return null;
        });
    }

    private <T> T io(Callable<T> task) {
        return DbExecutor.runBlocking(task);
    }

    private void clearDatabase() {
        io(() -> {
            db.userDao().deleteAll();
            return null;
        });
    }

    private static final class FakeCashierProvider implements RemoteDataProvider {
        final List<String> deletedEmails = new ArrayList<>();
        List<RemoteCashier> cashiers = Collections.emptyList();
        boolean failDeletes;

        @Override public String getType() { return "fake"; }
        @Override public boolean isConfigured(Context context) { return true; }
        @Override public boolean supportsCashierManagement() { return true; }

        @Override public void addProduct(String outletId, String accessToken, Product product) { }
        @Override public void updateProduct(String outletId, String accessToken, Product product) { }
        @Override public void deleteProduct(String outletId, String accessToken, String productId) { }
        @Override public boolean hasEnoughStock(String outletId, String accessToken, List<CartItem> items) { return true; }
        @Override public void reduceStock(String outletId, String accessToken, List<CartItem> items) { }
        @Override public void increaseStock(String outletId, String accessToken, List<CartItem> items) { }
        @Override public List<Product> getAllProducts(String outletId, String accessToken) { return Collections.emptyList(); }
        @Override public Product getProductById(String outletId, String accessToken, String productId) { return null; }
        @Override public String saveTransaction(String outletId, String userId, String accessToken, List<CartItem> items, double total, double discount, double pay, double change, String clientTransactionId) { return "REMOTE-1"; }
        @Override public List<TransactionRecord> getAllTransactions(String outletId, String accessToken) { return Collections.emptyList(); }
        @Override public TransactionRecord getTransactionById(String outletId, String accessToken, String transactionId) { return null; }
        @Override public boolean deleteTransactionById(String outletId, String accessToken, String transactionId) { return false; }
        @Override public List<TransactionRecord> getTransactionsForReportPeriod(String outletId, String accessToken, Calendar selectedCalendar, boolean monthly) { return Collections.emptyList(); }
        @Override public CreateCashierResult createCashierByAdmin(String accessToken, String outletId, String username, String password, String displayName, List<String> allowedMenus) { return new CreateCashierResult(false, "n/a"); }
        @Override public ListCashiersResult listCashiersByAdmin(String accessToken) { return new ListCashiersResult(true, cashiers); }

        @Override
        public CreateCashierResult deleteCashierByAdmin(String accessToken, String email) {
            if (failDeletes) return new CreateCashierResult(false, "remote delete failed");
            deletedEmails.add(email);
            return new CreateCashierResult(true, "");
        }
    }
}
