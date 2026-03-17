package com.daspos.repository;

import android.content.Context;

import com.daspos.db.AppDatabase;
import com.daspos.db.entity.UserEntity;
import com.daspos.model.User;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.PasswordHasher;

import java.util.ArrayList;
import java.util.List;

public class UserRepository {
    public static List<User> getAll(final Context context) {
        return DbExecutor.runBlocking(() -> {
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

    public static void add(final Context context, final String username, final String password, final String role) {
        DbExecutor.runBlocking(() -> {
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
            add(context, username, password, role);
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
