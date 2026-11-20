package com.daspos.feature.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.daspos.db.AppDatabase;
import com.daspos.db.entity.ProductEntity;
import com.daspos.db.entity.TransactionEntity;
import com.daspos.db.entity.TransactionItemEntity;
import com.daspos.db.entity.UserEntity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.model.TransactionRecord;
import com.daspos.repository.TransactionRepository;
import com.daspos.shared.util.DbExecutor;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

@RunWith(RobolectricTestRunner.class)
public class BackupRestoreHelperTest {
    private Context context;
    private AppDatabase db;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        AppDatabase.resetInstanceForTesting();
        db = AppDatabase.getInstance(context);
        // BuildConfig may carry live Supabase credentials; force local mode so
        // repository reads/writes stay offline and hermetic.
        AuthSessionStore.setDatabaseMode(context, AuthSessionStore.DATABASE_MODE_LOCAL);
        clearDatabase();
    }

    @Test
    public void backupRestoreRoundTripPreservesCoreData() throws Exception {
        seedDatabase();
        File file = File.createTempFile("backup", ".json");
        Uri uri = Uri.fromFile(file);

        assertTrue(io(() -> BackupRestoreHelper.backup(context, uri)));

        clearDatabase();
        assertEquals(BackupRestoreHelper.RestoreStatus.SUCCESS, io(() -> BackupRestoreHelper.restore(context, uri)));

        assertEquals(1, io(() -> db.productDao().getAll()).size());
        ProductEntity product = io(() -> db.productDao().getById("prod-1"));
        assertEquals("Gula", product.name);
        assertEquals(15000, product.price, 0.001);
        assertEquals(12000, product.costPrice, 0.001);
        assertEquals(42, product.stock);
        assertTrue(product.trackStock);
        // rw keeps barcode/unitName as first-class product fields.
        assertEquals("8990001001", product.barcode);
        assertEquals("pcs", product.unitName);

        assertEquals(1, io(() -> db.userDao().getAll()).size());
        assertEquals("admin@test.dev", io(() -> db.userDao().getByUsername("admin@test.dev")).username);

        assertEquals(1, io(() -> db.transactionDao().getAllTransactions()).size());
        TransactionEntity tx = io(() -> db.transactionDao().getTransactionById("2606010001"));
        assertNotNull(tx);
        assertEquals(25000, tx.total, 0.001);
        assertEquals(2000, tx.discount, 0.001);
        List<TransactionItemEntity> items = io(() -> db.transactionDao().getItemsByTransactionId("2606010001"));
        assertEquals(1, items.size());
        // rw re-encodes unit metadata into productName as "name~~~unit~~~...".
        assertTrue(items.get(0).productName.startsWith("Kopi"));
        assertEquals(2, items.get(0).qty);
    }

    @Test
    public void restoreToleratesLegacyAndUnknownFields() throws Exception {
        // Unlike min, rw still supports barcode/unitName/discount — those
        // keys must round-trip — while unknown tier fields are ignored.
        JSONObject root = new JSONObject();
        root.put("meta", new JSONObject().put("formatVersion", 4));

        JSONArray products = new JSONArray();
        JSONObject p = new JSONObject();
        p.put("id", "prod-old");
        p.put("name", "Legacy");
        p.put("price", 9000);
        p.put("barcode", "8990001");
        p.put("unitName", "karton");
        p.put("priceEcer", 9000);
        p.put("priceRenteng", 80000);
        p.put("factorRenteng", 10);
        p.put("stock", 5);
        p.put("trackStock", true);
        products.put(p);
        root.put("products", products);

        JSONArray users = new JSONArray();
        users.put(new JSONObject().put("username", "u@x.dev").put("role", "admin").put("passwordHash", "h"));
        root.put("users", users);

        JSONArray transactions = new JSONArray();
        JSONObject t = new JSONObject();
        t.put("id", "trx-legacy");
        t.put("date", "16 Nov 2026");
        t.put("time", "09:30");
        t.put("timestamp", System.currentTimeMillis());
        t.put("total", 18000);
        t.put("pay", 20000);
        t.put("change", 2000);
        t.put("discount", 2000);
        JSONArray items = new JSONArray();
        JSONObject item = new JSONObject();
        item.put("productId", "prod-old");
        item.put("productName", "Legacy~~~RENTENG~~~10~~~1~~~pcs"); // legacy ~~~ encoding
        item.put("price", 9000);
        item.put("qty", 2);
        item.put("unitCode", "RENTENG");
        item.put("unitFactorToBase", 10);
        item.put("tierPricingEnabled", true);
        item.put("unitName", "karton");
        items.put(item);
        t.put("items", items);
        transactions.put(t);
        root.put("transactions", transactions);

        File file = writeJson(root);
        assertEquals(BackupRestoreHelper.RestoreStatus.SUCCESS, io(() -> BackupRestoreHelper.restore(context, Uri.fromFile(file))));

        ProductEntity restored = io(() -> db.productDao().getById("prod-old"));
        assertNotNull(restored);
        assertEquals("Legacy", restored.name);
        // rw keeps these fields; they must survive the restore.
        assertEquals("8990001", restored.barcode);
        assertEquals("karton", restored.unitName);

        TransactionRecord record = io(() -> TransactionRepository.getById(context, "trx-legacy"));
        assertNotNull(record);
        // rw's discount column is preserved through restore.
        assertEquals(2000, record.getDiscount(), 0.001);
        // The ~~~ metadata must not leak into the displayed product name.
        assertEquals("Legacy", record.getItems().get(0).getProduct().getName());
    }

    @Test
    public void restoreRejectsMissingRequiredKeys() throws Exception {
        File file = writeJson(new JSONObject().put("meta", new JSONObject().put("formatVersion", 4)));
        assertEquals(BackupRestoreHelper.RestoreStatus.INVALID,
                io(() -> BackupRestoreHelper.restore(context, Uri.fromFile(file))));
    }

    @Test
    public void restoreRejectsNewerFormatVersion() throws Exception {
        JSONObject root = new JSONObject();
        root.put("meta", new JSONObject().put("formatVersion", 99));
        root.put("products", new JSONArray());
        root.put("users", new JSONArray());
        root.put("transactions", new JSONArray());
        File file = writeJson(root);
        assertEquals(BackupRestoreHelper.RestoreStatus.INCOMPATIBLE_VERSION,
                io(() -> BackupRestoreHelper.restore(context, Uri.fromFile(file))));
    }

    private void seedDatabase() {
        io(() -> {
            db.productDao().insertAll(Collections.singletonList(
                    new ProductEntity("prod-1", "Gula", "8990001001", 15000, 12000, 42, "pcs", true)));
            db.userDao().insertAll(Collections.singletonList(
                    new UserEntity("admin@test.dev", "admin", "hash")));
            db.transactionDao().insertTransaction(new TransactionEntity(
                    "2606010001", "16 Nov 2026", "08:00", System.currentTimeMillis(), 25000, 2000, 30000, 5000));
            db.transactionDao().insertTransactionItems(Collections.singletonList(
                    new TransactionItemEntity(UUID.randomUUID().toString(), "2606010001", "prod-1", "Kopi", 5000, 2)));
            return null;
        });
    }

    private File writeJson(JSONObject root) throws Exception {
        File file = File.createTempFile("restore", ".json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
        }
        return file;
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
}
