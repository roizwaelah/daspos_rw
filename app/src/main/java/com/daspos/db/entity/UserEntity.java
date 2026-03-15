package com.daspos.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class UserEntity {
    @PrimaryKey
    @NonNull
    public String username;
    public String role;

    public UserEntity(@NonNull String username, String role) {
        this.username = username;
        this.role = role;
    }
}
