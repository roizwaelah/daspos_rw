package com.daspos.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions")
public class TransactionEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String date;
    public String time;
    public long timestamp;
    public double total;
    public double pay;
    public double changeAmount;

    public TransactionEntity(@NonNull String id, String date, String time, long timestamp, double total, double pay, double changeAmount) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.timestamp = timestamp;
        this.total = total;
        this.pay = pay;
        this.changeAmount = changeAmount;
    }
}
