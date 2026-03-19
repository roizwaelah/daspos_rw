package com.daspos.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.daspos.db.entity.TransactionEntity;
import com.daspos.db.entity.TransactionItemEntity;

import java.util.List;

@Dao
public interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    List<TransactionEntity> getAllTransactions();

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 1")
    TransactionEntity getLastTransaction();

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    TransactionEntity getTransactionById(String transactionId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTransaction(TransactionEntity item);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTransactionItems(List<TransactionItemEntity> items);

    @Query("SELECT * FROM transaction_items WHERE transactionId = :transactionId")
    List<TransactionItemEntity> getItemsByTransactionId(String transactionId);

    @Query("SELECT COUNT(*) FROM transactions")
    int count();

    @Query("SELECT COUNT(*) FROM transactions WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp")
    int countByTimestampRange(long startTimestamp, long endTimestamp);

    @Query("DELETE FROM transaction_items WHERE transactionId = :transactionId")
    void deleteItemsByTransactionId(String transactionId);

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    void deleteTransactionById(String transactionId);
}
