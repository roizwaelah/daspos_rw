package com.daspos.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions", indices = {@Index(value = {"timestamp"})})
public class TransactionEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String date;
    public String time;
    public long timestamp;
    public double total;
    public double discount;
    public double pay;
    public double changeAmount;

    @Ignore
    public TransactionEntity(@NonNull String id, String date, String time, long timestamp, double total, double pay, double changeAmount) {
        this(id, date, time, timestamp, total, 0, pay, changeAmount);
    }

    public TransactionEntity(@NonNull String id, String date, String time, long timestamp, double total, double discount, double pay, double changeAmount) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.timestamp = timestamp;
        this.total = total;
        this.discount = Math.max(0, discount);
        this.pay = pay;
        this.changeAmount = changeAmount;
    }
}
