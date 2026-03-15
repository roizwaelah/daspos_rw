package com.daspos.repository;

import android.content.Context;

import com.daspos.db.AppDatabase;
import com.daspos.db.entity.UserEntity;
import com.daspos.model.User;

import java.util.ArrayList;
import java.util.List;

public class UserRepository {
    public static List<User> getAll(Context context) {
        List<User> models = new ArrayList<>();
        for (UserEntity e : AppDatabase.getInstance(context).userDao().getAll()) {
            models.add(new User(e.username, e.role));
        }
        return models;
    }

    public static User getByUsername(Context context, String username) {
        UserEntity e = AppDatabase.getInstance(context).userDao().getByUsername(username);
        if (e == null) return null;
        return new User(e.username, e.role);
    }

    public static void add(Context context, String username, String role) {
        AppDatabase.getInstance(context).userDao().insert(new UserEntity(username, role));
    }

    public static void updateRole(Context context, String username, String role) {
        AppDatabase.getInstance(context).userDao().insert(new UserEntity(username, role));
    }

    public static void delete(Context context, String username) {
        AppDatabase.getInstance(context).userDao().deleteByUsername(username);
    }
}
