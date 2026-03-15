package com.daspos.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.daspos.db.entity.UserEntity;

import java.util.List;

@Dao
public interface UserDao {
    @Query("SELECT * FROM users ORDER BY username ASC")
    List<UserEntity> getAll();

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    UserEntity getByUsername(String username);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<UserEntity> items);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(UserEntity item);

    @Query("DELETE FROM users WHERE username = :username")
    void deleteByUsername(String username);

    @Query("SELECT COUNT(*) FROM users")
    int count();
}
