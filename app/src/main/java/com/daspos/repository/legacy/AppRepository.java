package com.daspos.repository.legacy;

import android.content.Context;

import com.daspos.model.Product;
import com.daspos.model.TransactionRecord;
import com.daspos.model.User;

import java.util.List;

/**
 * Deprecated legacy shim.
 * Kept only to reduce migration risk for older code paths.
 * Prefer ProductRepository, UserRepository, TransactionRepository,
 * LegacySeedRepository directly.
 */
@Deprecated
public class AppRepository {
    public static List<Product> getProducts(Context context) {
        return LegacySeedRepository.getProducts(context);
    }

    public static List<User> getUsers(Context context) {
        return LegacySeedRepository.getUsers(context);
    }

    public static List<TransactionRecord> getTransactions(Context context) {
        return LegacySeedRepository.getTransactions(context);
    }
}
