package com.daspos.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.daspos.db.entity.ProductEntity;

import java.util.List;

@Dao
public interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    List<ProductEntity> getAll();

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    ProductEntity getById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ProductEntity> items);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ProductEntity item);

    @Update
    void update(ProductEntity item);

    @Query("DELETE FROM products WHERE id = :id")
    void deleteById(String id);

    @Query("SELECT COUNT(*) FROM products")
    int count();
}
