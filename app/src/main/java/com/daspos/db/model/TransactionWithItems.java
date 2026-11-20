package com.daspos.db.model;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.daspos.db.entity.TransactionEntity;
import com.daspos.db.entity.TransactionItemEntity;

import java.util.List;

public class TransactionWithItems {
    @Embedded
    public TransactionEntity transaction;

    @Relation(
            parentColumn = "id",
            entityColumn = "transactionId"
    )
    public List<TransactionItemEntity> items;
}
