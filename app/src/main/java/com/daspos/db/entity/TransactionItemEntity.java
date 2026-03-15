package com.daspos.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transaction_items")
public class TransactionItemEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String transactionId;
    public String productId;
    public String productName;
    public double price;
    public int qty;

    public TransactionItemEntity(@NonNull String id, String transactionId, String productId, String productName, double price, int qty) {
        this.id = id;
        this.transactionId = transactionId;
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.qty = qty;
    }
}
