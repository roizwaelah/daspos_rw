package com.daspos.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "sync_outbox",
        indices = {
                @Index(value = {"entityType", "entityId"}),
                @Index(value = {"createdAt"}),
                @Index(value = {"entityType", "nextRetryAt"})
        }
)
public class OutboxEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String entityType; // "transaction" | "product"

    @NonNull
    public String operation;  // "create" | "add" | "update" | "delete"

    @NonNull
    public String entityId;

    @NonNull
    public String payloadJson;

    public long createdAt;
    public int attemptCount;
    public long nextRetryAt;
    public String lastError;

    @Ignore
    public OutboxEntity(
            @NonNull String entityType,
            @NonNull String operation,
            @NonNull String entityId,
            @NonNull String payloadJson,
            long createdAt,
            int attemptCount,
            String lastError
    ) {
        this(entityType, operation, entityId, payloadJson, createdAt, attemptCount, 0L, lastError);
    }

    public OutboxEntity(
            @NonNull String entityType,
            @NonNull String operation,
            @NonNull String entityId,
            @NonNull String payloadJson,
            long createdAt,
            int attemptCount,
            long nextRetryAt,
            String lastError
    ) {
        this.entityType = entityType;
        this.operation = operation;
        this.entityId = entityId;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
        this.attemptCount = attemptCount;
        this.nextRetryAt = nextRetryAt;
        this.lastError = lastError;
    }
}
