package com.daspos.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Transaction;

import com.daspos.db.entity.TransactionEntity;
import com.daspos.db.entity.TransactionItemEntity;
import com.daspos.db.model.TransactionWithItems;
import com.daspos.model.BestSellerItem;
import com.daspos.model.ReportItem;

import java.util.List;

@Dao
public interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    List<TransactionEntity> getAllTransactions();

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    List<TransactionWithItems> getAllTransactionsWithItems();

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

    @Query("SELECT * FROM transaction_items")
    List<TransactionItemEntity> getAllTransactionItems();

    @Query("SELECT COUNT(*) FROM transactions")
    int count();

    @Query("SELECT MAX(timestamp) FROM transactions")
    Long getMaxTimestamp();

    @Query("SELECT COUNT(*) FROM transactions WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp")
    int countByTimestampRange(long startTimestamp, long endTimestamp);

    @Query("SELECT SUM(total) FROM transactions WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp")
    Double sumTotalByTimestampRange(long startTimestamp, long endTimestamp);

    // Optimized queries for dashboard & reports
    @Query("SELECT ti.productId as productId, ti.productName as productName, SUM(ti.qty) as totalQty, SUM(ti.price * ti.qty) as totalRevenue FROM transaction_items ti JOIN transactions t ON ti.transactionId = t.id WHERE t.timestamp >= :startTimestamp AND t.timestamp < :endTimestamp GROUP BY ti.productId, ti.productName ORDER BY totalQty DESC")
    List<BestSellerItem> getBestSellerItems(long startTimestamp, long endTimestamp);

    @Query("SELECT id, date, time, total FROM transactions WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp ORDER BY timestamp DESC")
    List<ReportItem> getTransactionsByTimestampRange(long startTimestamp, long endTimestamp);

    @Query("SELECT id, date, time, total FROM transactions WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    List<ReportItem> getTransactionsByTimestampRangePaged(long startTimestamp, long endTimestamp, int limit, int offset);

    @Query("SELECT date as id, date, CAST(COUNT(*) AS TEXT) as time, SUM(total) as total FROM transactions WHERE timestamp >= :startTimestamp AND timestamp < :endTimestamp GROUP BY date ORDER BY MAX(timestamp) DESC")
    List<ReportItem> getDailySummariesByTimestampRange(long startTimestamp, long endTimestamp);

    @Query("DELETE FROM transaction_items WHERE transactionId = :transactionId")
    void deleteItemsByTransactionId(String transactionId);

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    void deleteTransactionById(String transactionId);

    @Query("DELETE FROM transaction_items")
    void deleteAllItems();

    @Query("DELETE FROM transactions")
    void deleteAllTransactions();
}
