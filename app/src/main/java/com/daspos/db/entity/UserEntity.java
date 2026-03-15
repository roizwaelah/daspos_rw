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
    public String passwordHash;

    public UserEntity(@NonNull String username, String role, String passwordHash) {
        this.username = username;
        this.role = role;
        this.passwordHash = passwordHash;
    }
}
