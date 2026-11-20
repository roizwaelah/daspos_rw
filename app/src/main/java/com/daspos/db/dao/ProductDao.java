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

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' ORDER BY name ASC")
    List<ProductEntity> searchProducts(String query);

    @Query("SELECT * FROM products ORDER BY name ASC")
    List<ProductEntity> getAllSortedByNameAsc();

    @Query("SELECT * FROM products ORDER BY name DESC")
    List<ProductEntity> getAllSortedByNameDesc();

    @Query("SELECT * FROM products ORDER BY price ASC")
    List<ProductEntity> getAllSortedByPriceAsc();

    @Query("SELECT * FROM products ORDER BY price DESC")
    List<ProductEntity> getAllSortedByPriceDesc();

    @Query("SELECT * FROM products ORDER BY stock DESC")
    List<ProductEntity> getAllSortedByStockDesc();

    @Query("SELECT * FROM products WHERE LOWER(name) = LOWER(:name)")
    List<ProductEntity> findByNameIgnoreCase(String name);

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' ORDER BY name DESC")
    List<ProductEntity> searchProductsByNameDesc(String query);

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' ORDER BY price ASC")
    List<ProductEntity> searchProductsByPriceAsc(String query);

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' ORDER BY price DESC")
    List<ProductEntity> searchProductsByPriceDesc(String query);

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' ORDER BY stock DESC")
    List<ProductEntity> searchProductsByStockDesc(String query);

    @Query("SELECT * FROM products ORDER BY name ASC LIMIT :limit OFFSET :offset")
    List<ProductEntity> getAllSortedByNameAscPaged(int limit, int offset);

    @Query("SELECT * FROM products ORDER BY name DESC LIMIT :limit OFFSET :offset")
    List<ProductEntity> getAllSortedByNameDescPaged(int limit, int offset);

    @Query("SELECT * FROM products ORDER BY price ASC LIMIT :limit OFFSET :offset")
    List<ProductEntity> getAllSortedByPriceAscPaged(int limit, int offset);

    @Query("SELECT * FROM products ORDER BY price DESC LIMIT :limit OFFSET :offset")
    List<ProductEntity> getAllSortedByPriceDescPaged(int limit, int offset);

    @Query("SELECT * FROM products ORDER BY stock DESC LIMIT :limit OFFSET :offset")
    List<ProductEntity> getAllSortedByStockDescPaged(int limit, int offset);

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' ORDER BY name ASC LIMIT :limit OFFSET :offset")
    List<ProductEntity> searchProductsByNameAscPaged(String query, int limit, int offset);

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' ORDER BY name DESC LIMIT :limit OFFSET :offset")
    List<ProductEntity> searchProductsByNameDescPaged(String query, int limit, int offset);

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' ORDER BY price ASC LIMIT :limit OFFSET :offset")
    List<ProductEntity> searchProductsByPriceAscPaged(String query, int limit, int offset);

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' ORDER BY price DESC LIMIT :limit OFFSET :offset")
    List<ProductEntity> searchProductsByPriceDescPaged(String query, int limit, int offset);

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' ORDER BY stock DESC LIMIT :limit OFFSET :offset")
    List<ProductEntity> searchProductsByStockDescPaged(String query, int limit, int offset);

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

    @Query("DELETE FROM products")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM products")
    int count();
}
