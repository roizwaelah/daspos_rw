package com.daspos.repository;

import android.content.Context;
import android.util.Log;

import com.daspos.db.AppDatabase;
import com.daspos.db.entity.UserEntity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.feature.auth.MenuAccessStore;
import com.daspos.model.User;
import com.daspos.remote.RemoteDataProvider;
import com.daspos.remote.RemoteDataProviderFactory;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.NetworkUtils;
import com.daspos.shared.util.PasswordHasher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserRepository {
    private static final String TAG = "UserRepository";
    public static List<User> getAll(final Context context) {
        return DbExecutor.runBlocking(() -> {
            syncRemoteCashiersIfNeeded(context);
            List<User> models = new ArrayList<>();
            for (UserEntity e : AppDatabase.getInstance(context).userDao().getAll()) {
                models.add(new User(e.username, e.role));
            }
            return models;
        });
    }

    public static User getByUsername(final Context context, final String username) {
        return DbExecutor.runBlocking(() -> {
            UserEntity e = AppDatabase.getInstance(context).userDao().getByUsername(normalizeUsername(username));
            if (e == null) return null;
            return new User(e.username, e.role);
        });
    }

    public static boolean usernameExists(final Context context, final String username) {
        return DbExecutor.runBlocking(() -> AppDatabase.getInstance(context).userDao().getByUsername(normalizeUsername(username)) != null);
    }

    public static void add(
            final Context context,
            final String username,
            final String password,
            final String role,
            final List<String> allowedMenus
    ) {
        DbExecutor.runBlocking(() -> {
            String normalizedUsername = normalizeUsername(username);
            if (shouldCreateRemoteCashier(context, role)) {
                if (!NetworkUtils.isOnline(context)) {
                    throw new IllegalStateException("Tambah kasir butuh koneksi internet");
                }
                RemoteDataProvider.CreateCashierResult result;
                try {
                    result = getRemoteProvider(context).createCashierByAdmin(
                            safe(AuthSessionStore.getAccessToken(context)),
                            safe(AuthSessionStore.getOutletId(context)),
                            normalizedUsername,
                            password,
                            defaultDisplayName(normalizedUsername),
                            allowedMenus
                    );
                } catch (Exception e) {
                    throw new IllegalStateException("Gagal menambah kasir ke remote provider", e);
                }
                if (!result.success) {
                    String msg = result.message == null || result.message.trim().isEmpty()
                            ? "Gagal menambah kasir ke remote provider"
                            : result.message;
                    throw new IllegalStateException(msg);
                }
            }

            String passwordHash = PasswordHasher.hash(password);
            AppDatabase.getInstance(context).userDao().insert(new UserEntity(normalizedUsername, role, passwordHash));
        });
    }

    public static void addWithPasswordHash(final Context context, final String username, final String role, final String passwordHash) {
        DbExecutor.runBlocking(() -> AppDatabase.getInstance(context).userDao().insert(new UserEntity(normalizeUsername(username), role, passwordHash)));
    }

    public static boolean authenticate(final Context context, final String username, final String password) {
        return DbExecutor.runBlocking(() -> {
            UserEntity user = AppDatabase.getInstance(context).userDao().getByUsername(normalizeUsername(username));
            if (user == null) return false;
            boolean authenticated = PasswordHasher.verify(password, user.passwordHash);
            if (!authenticated) return false;

            if (PasswordHasher.needsRehash(user.passwordHash)) {
                String upgradedHash = PasswordHasher.hash(password);
                AppDatabase.getInstance(context).userDao().insert(new UserEntity(user.username, user.role, upgradedHash));
            }
            return true;
        });
    }


    public static String getPasswordHash(final Context context, final String username) {
        return DbExecutor.runBlocking(() -> {
            UserEntity user = AppDatabase.getInstance(context).userDao().getByUsername(normalizeUsername(username));
            return user == null ? "" : user.passwordHash;
        });
    }

    public static void updateRole(final Context context, final String username, final String role) {
        DbExecutor.runBlocking(() -> {
            String normalizedUsername = normalizeUsername(username);
            UserEntity existing = AppDatabase.getInstance(context).userDao().getByUsername(normalizedUsername);
            String passwordHash = existing == null ? "" : existing.passwordHash;
            AppDatabase.getInstance(context).userDao().insert(new UserEntity(normalizedUsername, role, passwordHash));
        });
    }

    public static void updatePassword(final Context context, final String username, final String newPassword) {
        DbExecutor.runBlocking(() -> {
            String normalizedUsername = normalizeUsername(username);
            UserEntity existing = AppDatabase.getInstance(context).userDao().getByUsername(normalizedUsername);
            if (existing == null) return;
            String passwordHash = PasswordHasher.hash(newPassword);
            AppDatabase.getInstance(context).userDao().insert(new UserEntity(normalizedUsername, existing.role, passwordHash));
        });
    }

    public static void delete(final Context context, final String username) {
        DbExecutor.runBlocking(() -> {
            String normalizedUsername = normalizeUsername(username);
            deleteRemoteCashierIfNeeded(context, normalizedUsername);
            AppDatabase.getInstance(context).userDao().deleteByUsername(normalizedUsername);
        });
    }

    // Remote-managed accounts are keyed by email; deleting them locally only
    // would let the next cashier sync resurrect them.
    private static void deleteRemoteCashierIfNeeded(Context context, String username) {
        if (AuthSessionStore.isLocalDatabaseMode(context)) return;
        if (username.indexOf('@') < 0) return;
        RemoteDataProvider provider = getRemoteProvider(context);
        if (provider == null || !provider.isConfigured(context)) return;
        if (!provider.supportsCashierManagement()) return;
        String accessToken = safe(AuthSessionStore.getAccessToken(context));
        if (provider.requiresAccessToken() && accessToken.isEmpty()) {
            throw new IllegalStateException("Sesi login tidak valid untuk menghapus kasir");
        }
        if (!NetworkUtils.isOnline(context)) {
            throw new IllegalStateException("Hapus kasir butuh koneksi internet");
        }
        RemoteDataProvider.CreateCashierResult result;
        try {
            result = provider.deleteCashierByAdmin(accessToken, username);
        } catch (Exception e) {
            throw new IllegalStateException("Gagal menghapus kasir di remote provider", e);
        }
        if (result == null || !result.success) {
            String msg = result == null || result.message == null || result.message.trim().isEmpty()
                    ? "Gagal menghapus kasir di remote provider"
                    : result.message;
            throw new IllegalStateException(msg);
        }
    }

    private static boolean shouldCreateRemoteCashier(Context context, String role) {
        if (AuthSessionStore.isLocalDatabaseMode(context)) return false;
        RemoteDataProvider provider = getRemoteProvider(context);
        if (provider == null || !provider.isConfigured(context)) return false;
        if (!provider.supportsCashierManagement()) return false;
        String accessToken = safe(AuthSessionStore.getAccessToken(context));
        String outletId = safe(AuthSessionStore.getOutletId(context));
        if (provider.requiresAccessToken() && accessToken.isEmpty()) return false;
        if (provider.requiresOutletId() && outletId.isEmpty()) return false;
        return "kasir".equalsIgnoreCase(safe(role));
    }

    private static void syncRemoteCashiersIfNeeded(Context context) {
        if (AuthSessionStore.isLocalDatabaseMode(context)) return;
        RemoteDataProvider provider = getRemoteProvider(context);
        if (provider == null || !provider.isConfigured(context)) return;
        if (!provider.supportsCashierManagement()) return;
        if (!NetworkUtils.isOnline(context)) return;
        String role = safe(AuthSessionStore.getRole(context));
        if (!"admin".equalsIgnoreCase(role) && !"owner".equalsIgnoreCase(role)) return;

        String accessToken = safe(AuthSessionStore.getAccessToken(context));
        if (provider.requiresAccessToken() && accessToken.isEmpty()) return;

        RemoteDataProvider.ListCashiersResult result;
        try {
            result = provider.listCashiersByAdmin(accessToken);
        } catch (Exception e) {
            Log.w(TAG, "Gagal sinkronisasi kasir remote", e);
            return;
        }
        if (!result.success) return;

        Set<String> remoteEmails = new HashSet<>();
        for (RemoteDataProvider.RemoteCashier cashier : result.users) {
            String email = safe(cashier.email);
            if (email.isEmpty()) continue;
            remoteEmails.add(normalizeUsername(email));
            UserEntity existing = AppDatabase.getInstance(context).userDao().getByUsername(email);
            String passwordHash = existing == null ? "" : safe(existing.passwordHash);
            String cashierRole = safe(cashier.role).isEmpty() ? "Kasir" : cashier.role;
            AppDatabase.getInstance(context).userDao().insert(new UserEntity(email, cashierRole, passwordHash));
            MenuAccessStore.saveForUser(context, email, cashier.allowedMenus);
        }

        // The remote list is authoritative for cashiers: drop stale local rows
        // for accounts that no longer exist remotely (e.g. deleted in Supabase).
        String sessionUsername = normalizeUsername(AuthSessionStore.getUsername(context));
        for (UserEntity local : AppDatabase.getInstance(context).userDao().getAll()) {
            String name = normalizeUsername(local.username);
            if (name.indexOf('@') < 0) continue;
            if (!"kasir".equalsIgnoreCase(safe(local.role))) continue;
            if (name.equals(sessionUsername)) continue;
            if (remoteEmails.contains(name)) continue;
            AppDatabase.getInstance(context).userDao().deleteByUsername(local.username);
            MenuAccessStore.clearForUser(context, local.username);
        }
    }

    private static String defaultDisplayName(String email) {
        String safeEmail = normalizeUsername(email);
        int at = safeEmail.indexOf('@');
        String base = at > 0 ? safeEmail.substring(0, at) : safeEmail;
        if (base.trim().isEmpty()) return "Kasir";
        return base.trim();
    }

    private static String normalizeUsername(String value) {
        return safe(value).toLowerCase();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static RemoteDataProvider getRemoteProvider(Context context) {
        return RemoteDataProviderFactory.getProvider(context);
    }


    public static void getAllAsync(final Context context, final DbExecutor.SuccessCallback<List<User>> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getAll(context), onSuccess, onError);
    }

    public static void getByUsernameAsync(final Context context, final String username, final DbExecutor.SuccessCallback<User> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> getByUsername(context, username), onSuccess, onError);
    }

    public static void usernameExistsAsync(final Context context, final String username, final DbExecutor.SuccessCallback<Boolean> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> usernameExists(context, username), onSuccess, onError);
    }

    public static void authenticateAsync(final Context context, final String username, final String password, final DbExecutor.SuccessCallback<Boolean> onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> authenticate(context, username, password), onSuccess, onError);
    }

    public static void addAsync(final Context context, final String username, final String password, final String role, final Runnable onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> {
            add(context, username, password, role, null);
            return null;
        }, ignored -> {
            if (onSuccess != null) onSuccess.run();
        }, onError);
    }

    public static void addAsync(
            final Context context,
            final String username,
            final String password,
            final String role,
            final List<String> allowedMenus,
            final Runnable onSuccess,
            final DbExecutor.ErrorCallback onError
    ) {
        DbExecutor.runAsync(() -> {
            add(context, username, password, role, allowedMenus);
            return null;
        }, ignored -> {
            if (onSuccess != null) onSuccess.run();
        }, onError);
    }

    public static void updateRoleAsync(final Context context, final String username, final String role, final Runnable onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> {
            updateRole(context, username, role);
            return null;
        }, ignored -> {
            if (onSuccess != null) onSuccess.run();
        }, onError);
    }

    public static void updatePasswordAsync(final Context context, final String username, final String newPassword, final Runnable onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> {
            updatePassword(context, username, newPassword);
            return null;
        }, ignored -> {
            if (onSuccess != null) onSuccess.run();
        }, onError);
    }

    public static void deleteAsync(final Context context, final String username, final Runnable onSuccess, final DbExecutor.ErrorCallback onError) {
        DbExecutor.runAsync(() -> {
            delete(context, username);
            return null;
        }, ignored -> {
            if (onSuccess != null) onSuccess.run();
        }, onError);
    }
}
