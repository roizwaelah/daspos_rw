package com.daspos.remote;

import android.content.Context;

import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.TransactionRecord;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public interface RemoteDataProvider {
    String getType();

    boolean isConfigured(Context context);

    default boolean requiresAccessToken() {
        return true;
    }

    default boolean requiresOutletId() {
        return true;
    }

    default boolean requiresUserId() {
        return true;
    }

    default boolean supportsCashierManagement() {
        return false;
    }

    // Product operations
    void addProduct(String outletId, String accessToken, Product product) throws Exception;

    default void addProductsBatch(String outletId, String accessToken, List<Product> products) throws Exception {
        if (products == null) return;
        for (Product product : products) {
            if (product != null) addProduct(outletId, accessToken, product);
        }
    }
    void updateProduct(String outletId, String accessToken, Product product) throws Exception;
    void deleteProduct(String outletId, String accessToken, String productId) throws Exception;
    boolean hasEnoughStock(String outletId, String accessToken, List<CartItem> items) throws Exception;
    void reduceStock(String outletId, String accessToken, List<CartItem> items) throws Exception;
    void increaseStock(String outletId, String accessToken, List<CartItem> items) throws Exception;
    List<Product> getAllProducts(String outletId, String accessToken) throws Exception;
    Product getProductById(String outletId, String accessToken, String productId) throws Exception;

    // Transaction operations
    String saveTransaction(
            String outletId,
            String userId,
            String accessToken,
            List<CartItem> items,
            double total,
            double discount,
            double pay,
            double change,
            String clientTransactionId
    ) throws Exception;
    List<TransactionRecord> getAllTransactions(String outletId, String accessToken) throws Exception;

    /**
     * Returns transactions created after {@code sinceEpochMillis}, or {@code null}
     * when the provider does not support incremental fetch (caller falls back
     * to {@link #getAllTransactions}).
     */
    default List<TransactionRecord> getTransactionsSince(String outletId, String accessToken, long sinceEpochMillis) throws Exception {
        return null;
    }
    TransactionRecord getTransactionById(String outletId, String accessToken, String transactionId) throws Exception;
    boolean deleteTransactionById(String outletId, String accessToken, String transactionId) throws Exception;
    List<TransactionRecord> getTransactionsForReportPeriod(
            String outletId,
            String accessToken,
            Calendar selectedCalendar,
            boolean monthly
    ) throws Exception;

    // User operations
    CreateCashierResult createCashierByAdmin(
            String accessToken,
            String outletId,
            String username,
            String password,
            String displayName,
            List<String> allowedMenus
    ) throws Exception;
    ListCashiersResult listCashiersByAdmin(String accessToken) throws Exception;

    default CreateCashierResult deleteCashierByAdmin(String accessToken, String email) throws Exception {
        return new CreateCashierResult(false, "Provider tidak mendukung hapus kasir");
    }

    final class CreateCashierResult {
        public final boolean success;
        public final String message;

        public CreateCashierResult(boolean success, String message) {
            this.success = success;
            this.message = message == null ? "" : message;
        }
    }

    final class RemoteCashier {
        public final String email;
        public final String role;
        public final List<String> allowedMenus;

        public RemoteCashier(String email, String role, List<String> allowedMenus) {
            this.email = email == null ? "" : email;
            this.role = role == null ? "" : role;
            this.allowedMenus = allowedMenus == null ? Collections.emptyList() : allowedMenus;
        }
    }

    final class ListCashiersResult {
        public final boolean success;
        public final List<RemoteCashier> users;

        public ListCashiersResult(boolean success, List<RemoteCashier> users) {
            this.success = success;
            this.users = users == null ? Collections.emptyList() : users;
        }
    }
}
