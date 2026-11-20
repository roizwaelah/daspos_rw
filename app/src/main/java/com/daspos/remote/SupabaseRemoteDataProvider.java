package com.daspos.remote;

import android.content.Context;

import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.TransactionRecord;
import com.daspos.supabase.SupabaseAuthService;
import com.daspos.supabase.SupabaseConfig;
import com.daspos.supabase.SupabaseProductService;
import com.daspos.supabase.SupabaseTransactionService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class SupabaseRemoteDataProvider implements RemoteDataProvider {
    @Override
    public String getType() {
        return AuthSessionStore.PROVIDER_SUPABASE;
    }

    @Override
    public boolean isConfigured(Context context) {
        return SupabaseConfig.isConfigured();
    }

    @Override
    public boolean supportsCashierManagement() {
        return true;
    }

    @Override
    public void addProduct(String outletId, String accessToken, Product product) throws Exception {
        SupabaseProductService.addWithId(outletId, accessToken, product);
    }

    @Override
    public void addProductsBatch(String outletId, String accessToken, List<Product> products) throws Exception {
        SupabaseProductService.addBatch(outletId, accessToken, products);
    }

    @Override
    public void updateProduct(String outletId, String accessToken, Product product) throws Exception {
        SupabaseProductService.update(outletId, accessToken, product);
    }

    @Override
    public void deleteProduct(String outletId, String accessToken, String productId) throws Exception {
        SupabaseProductService.delete(outletId, accessToken, productId);
    }

    @Override
    public boolean hasEnoughStock(String outletId, String accessToken, List<CartItem> items) throws Exception {
        return SupabaseProductService.hasEnoughStock(outletId, accessToken, items);
    }

    @Override
    public void reduceStock(String outletId, String accessToken, List<CartItem> items) throws Exception {
        SupabaseProductService.reduceStock(outletId, accessToken, items);
    }

    @Override
    public void increaseStock(String outletId, String accessToken, List<CartItem> items) throws Exception {
        SupabaseProductService.increaseStock(outletId, accessToken, items);
    }

    @Override
    public List<Product> getAllProducts(String outletId, String accessToken) throws Exception {
        return SupabaseProductService.getAll(outletId, accessToken);
    }

    @Override
    public Product getProductById(String outletId, String accessToken, String productId) throws Exception {
        return SupabaseProductService.getById(outletId, accessToken, productId);
    }

    @Override
    public String saveTransaction(
            String outletId,
            String userId,
            String accessToken,
            List<CartItem> items,
            double total,
            double discount,
            double pay,
            double change,
            String clientTransactionId
    ) throws Exception {
        return SupabaseTransactionService.save(outletId, userId, accessToken, items, total, discount, pay, change, clientTransactionId);
    }

    @Override
    public List<TransactionRecord> getAllTransactions(String outletId, String accessToken) throws Exception {
        return SupabaseTransactionService.getAll(outletId, accessToken);
    }

    @Override
    public List<TransactionRecord> getTransactionsSince(String outletId, String accessToken, long sinceEpochMillis) throws Exception {
        return SupabaseTransactionService.getSince(outletId, accessToken, sinceEpochMillis);
    }

    @Override
    public TransactionRecord getTransactionById(String outletId, String accessToken, String transactionId) throws Exception {
        return SupabaseTransactionService.getById(outletId, accessToken, transactionId);
    }

    @Override
    public boolean deleteTransactionById(String outletId, String accessToken, String transactionId) throws Exception {
        return SupabaseTransactionService.deleteById(outletId, accessToken, transactionId);
    }

    @Override
    public List<TransactionRecord> getTransactionsForReportPeriod(
            String outletId,
            String accessToken,
            Calendar selectedCalendar,
            boolean monthly
    ) throws Exception {
        return SupabaseTransactionService.getForReportPeriod(outletId, accessToken, selectedCalendar, monthly);
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
        SupabaseAuthService.SignupResult result = SupabaseAuthService.signupCashierByAdmin(
                accessToken,
                outletId,
                username,
                password,
                displayName,
                allowedMenus
        );
        return new CreateCashierResult(result.success, result.message);
    }

    @Override
    public ListCashiersResult listCashiersByAdmin(String accessToken) throws Exception {
        SupabaseAuthService.ListCashiersResult result = SupabaseAuthService.listCashiersByAdmin(accessToken);
        List<RemoteCashier> users = new ArrayList<>();
        if (result.users != null) {
            for (SupabaseAuthService.CashierUser user : result.users) {
                users.add(new RemoteCashier(user.email, user.role, user.allowedMenus));
            }
        }
        return new ListCashiersResult(result.success, users);
    }

    @Override
    public CreateCashierResult deleteCashierByAdmin(String accessToken, String email) throws Exception {
        SupabaseAuthService.SignupResult result = SupabaseAuthService.deleteCashierByAdmin(accessToken, email);
        return new CreateCashierResult(result.success, result.message);
    }
}
