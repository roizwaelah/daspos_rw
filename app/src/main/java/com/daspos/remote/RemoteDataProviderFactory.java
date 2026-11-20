package com.daspos.remote;

import android.content.Context;

import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.TransactionRecord;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public final class RemoteDataProviderFactory {
    private static volatile RemoteDataProvider testProviderOverride;

    private RemoteDataProviderFactory() {
    }

    // Test seam: lets unit tests inject a fake provider without HTTP.
    public static void setProviderOverrideForTesting(RemoteDataProvider provider) {
        testProviderOverride = provider;
    }

    public static RemoteDataProvider getProvider(Context context) {
        RemoteDataProvider override = testProviderOverride;
        if (override != null) return override;
        String providerType = AuthSessionStore.getRemoteProviderType(context);
        if (AuthSessionStore.PROVIDER_SUPABASE.equalsIgnoreCase(providerType)) {
            return new SupabaseRemoteDataProvider();
        }
        if (AuthSessionStore.PROVIDER_FIREBASE.equalsIgnoreCase(providerType)
                || AuthSessionStore.PROVIDER_APPWRITE.equalsIgnoreCase(providerType)
                || AuthSessionStore.PROVIDER_POCKETBASE.equalsIgnoreCase(providerType)
                || AuthSessionStore.PROVIDER_CUSTOM_API.equalsIgnoreCase(providerType)) {
            return new GenericRemoteDataProvider(context, providerType);
        }
        return new UnsupportedRemoteDataProvider(providerType);
    }

    private static final class UnsupportedRemoteDataProvider implements RemoteDataProvider {
        private final String type;

        private UnsupportedRemoteDataProvider(String type) {
            this.type = type == null || type.trim().isEmpty() ? "unsupported" : type.trim().toLowerCase();
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public boolean isConfigured(Context context) {
            return false;
        }

        @Override
        public boolean supportsCashierManagement() {
            return false;
        }

        @Override
        public void addProduct(String outletId, String accessToken, Product product) throws Exception {
            throw unsupported();
        }

        @Override
        public void updateProduct(String outletId, String accessToken, Product product) throws Exception {
            throw unsupported();
        }

        @Override
        public void deleteProduct(String outletId, String accessToken, String productId) throws Exception {
            throw unsupported();
        }

        @Override
        public boolean hasEnoughStock(String outletId, String accessToken, List<CartItem> items) throws Exception {
            throw unsupported();
        }

        @Override
        public void reduceStock(String outletId, String accessToken, List<CartItem> items) throws Exception {
            throw unsupported();
        }

        @Override
        public void increaseStock(String outletId, String accessToken, List<CartItem> items) throws Exception {
            throw unsupported();
        }

        @Override
        public List<Product> getAllProducts(String outletId, String accessToken) throws Exception {
            throw unsupported();
        }

        @Override
        public Product getProductById(String outletId, String accessToken, String productId) throws Exception {
            throw unsupported();
        }

        @Override
        public String saveTransaction(String outletId, String userId, String accessToken, List<CartItem> items, double total, double discount, double pay, double change, String clientTransactionId) throws Exception {
            throw unsupported();
        }

        @Override
        public List<TransactionRecord> getAllTransactions(String outletId, String accessToken) throws Exception {
            throw unsupported();
        }

        @Override
        public TransactionRecord getTransactionById(String outletId, String accessToken, String transactionId) throws Exception {
            throw unsupported();
        }

        @Override
        public boolean deleteTransactionById(String outletId, String accessToken, String transactionId) throws Exception {
            throw unsupported();
        }

        @Override
        public List<TransactionRecord> getTransactionsForReportPeriod(String outletId, String accessToken, Calendar selectedCalendar, boolean monthly) throws Exception {
            throw unsupported();
        }

        @Override
        public CreateCashierResult createCashierByAdmin(
                String accessToken,
                String outletId,
                String username,
                String password,
                String displayName,
                List<String> allowedMenus
        ) throws Exception {
            throw unsupported();
        }

        @Override
        public ListCashiersResult listCashiersByAdmin(String accessToken) throws Exception {
            throw unsupported();
        }

        private Exception unsupported() {
            return new UnsupportedOperationException("Provider '" + type + "' belum didukung pada versi ini.");
        }
    }
}
