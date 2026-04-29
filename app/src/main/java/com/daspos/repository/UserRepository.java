package com.daspos.repository;

import android.content.Context;

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
import java.util.List;

public class UserRepository {
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
            UserEntity e = AppDatabase.getInstance(context).userDao().getByUsername(username);
            if (e == null) return null;
            return new User(e.username, e.role);
        });
    }

    public static boolean usernameExists(final Context context, final String username) {
        return DbExecutor.runBlocking(() -> AppDatabase.getInstance(context).userDao().getByUsername(username) != null);
    }

    public static void add(
            final Context context,
            final String username,
            final String password,
            final String role,
            final List<String> allowedMenus
    ) {
        DbExecutor.runBlocking(() -> {
            if (shouldCreateSupabaseCashier(context, role)) {
                if (!NetworkUtils.isOnline(context)) {
                    throw new IllegalStateException("Tambah kasir butuh koneksi internet");
                }
                RemoteDataProvider.CreateCashierResult result;
                try {
                    result = getRemoteProvider(context).createCashierByAdmin(
                            safe(AuthSessionStore.getAccessToken(context)),
                            safe(AuthSessionStore.getOutletId(context)),
                            username,
                            password,
                            defaultDisplayName(username),
                            allowedMenus
                    );
                } catch (Exception e) {
                    throw new IllegalStateException("Gagal menambah kasir ke remote provider", e);
                }
                if (!result.success) {
                    String msg = result.message == null || result.message.trim().isEmpty()
                            ? "Gagal menambah kasir ke Supabase"
                            : result.message;
                    throw new IllegalStateException(msg);
                }
            }

            String passwordHash = PasswordHasher.hash(password);
            AppDatabase.getInstance(context).userDao().insert(new UserEntity(username, role, passwordHash));
        });
    }

    public static void addWithPasswordHash(final Context context, final String username, final String role, final String passwordHash) {
        DbExecutor.runBlocking(() -> AppDatabase.getInstance(context).userDao().insert(new UserEntity(username, role, passwordHash)));
    }

    public static boolean authenticate(final Context context, final String username, final String password) {
        return DbExecutor.runBlocking(() -> {
            UserEntity user = AppDatabase.getInstance(context).userDao().getByUsername(username);
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
            UserEntity user = AppDatabase.getInstance(context).userDao().getByUsername(username);
            return user == null ? "" : user.passwordHash;
        });
    }

    public static void updateRole(final Context context, final String username, final String role) {
        DbExecutor.runBlocking(() -> {
            UserEntity existing = AppDatabase.getInstance(context).userDao().getByUsername(username);
            String passwordHash = existing == null ? "" : existing.passwordHash;
            AppDatabase.getInstance(context).userDao().insert(new UserEntity(username, role, passwordHash));
        });
    }

    public static void updatePassword(final Context context, final String username, final String newPassword) {
        DbExecutor.runBlocking(() -> {
            UserEntity existing = AppDatabase.getInstance(context).userDao().getByUsername(username);
            if (existing == null) return;
            String passwordHash = PasswordHasher.hash(newPassword);
            AppDatabase.getInstance(context).userDao().insert(new UserEntity(username, existing.role, passwordHash));
        });
    }

    public static void delete(final Context context, final String username) {
        DbExecutor.runBlocking(() -> AppDatabase.getInstance(context).userDao().deleteByUsername(username));
    }

    private static boolean shouldCreateSupabaseCashier(Context context, String role) {
        if (AuthSessionStore.isLocalDatabaseMode(context)) return false;
        return getRemoteProvider(context).isConfigured(context)
                && "supabase".equalsIgnoreCase(safe(AuthSessionStore.getSource(context)))
                && !safe(AuthSessionStore.getAccessToken(context)).isEmpty()
                && !safe(AuthSessionStore.getOutletId(context)).isEmpty()
                && "kasir".equalsIgnoreCase(safe(role));
    }

    private static void syncRemoteCashiersIfNeeded(Context context) {
        if (AuthSessionStore.isLocalDatabaseMode(context)) return;
        if (!getRemoteProvider(context).isConfigured(context)) return;
        if (!NetworkUtils.isOnline(context)) return;
        if (!"supabase".equalsIgnoreCase(safe(AuthSessionStore.getSource(context)))) return;
        String role = safe(AuthSessionStore.getRole(context));
        if (!"admin".equalsIgnoreCase(role) && !"owner".equalsIgnoreCase(role)) return;

        String accessToken = safe(AuthSessionStore.getAccessToken(context));
        if (accessToken.isEmpty()) return;

        RemoteDataProvider.ListCashiersResult result;
        try {
            result = getRemoteProvider(context).listCashiersByAdmin(accessToken);
        } catch (Exception ignored) {
            return;
        }
        if (!result.success) return;

        for (RemoteDataProvider.RemoteCashier cashier : result.users) {
            String email = safe(cashier.email);
            if (email.isEmpty()) continue;
            UserEntity existing = AppDatabase.getInstance(context).userDao().getByUsername(email);
            String passwordHash = existing == null ? "" : safe(existing.passwordHash);
            String cashierRole = safe(cashier.role).isEmpty() ? "Kasir" : cashier.role;
            AppDatabase.getInstance(context).userDao().insert(new UserEntity(email, cashierRole, passwordHash));
            MenuAccessStore.saveForUser(context, email, cashier.allowedMenus);
        }
    }

    private static String defaultDisplayName(String email) {
        String safeEmail = safe(email);
        int at = safeEmail.indexOf('@');
        String base = at > 0 ? safeEmail.substring(0, at) : safeEmail;
        if (base.trim().isEmpty()) return "Kasir";
        return base.trim();
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
