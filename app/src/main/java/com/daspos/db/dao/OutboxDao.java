package com.daspos.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.daspos.db.entity.OutboxEntity;

import java.util.List;

@Dao
public interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(OutboxEntity entity);

    @Query("SELECT * FROM sync_outbox WHERE entityType = :entityType ORDER BY createdAt ASC, id ASC")
    List<OutboxEntity> getByEntityType(String entityType);

    @Query("SELECT * FROM sync_outbox WHERE entityType = :entityType AND nextRetryAt <= :nowMillis ORDER BY createdAt ASC, id ASC LIMIT :limit")
    List<OutboxEntity> getDueByEntityType(String entityType, long nowMillis, int limit);

    @Query("SELECT * FROM sync_outbox WHERE entityType = :entityType AND entityId = :entityId LIMIT 1")
    OutboxEntity findByEntity(String entityType, String entityId);

    @Query("SELECT COUNT(*) FROM sync_outbox")
    int countAll();

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE entityType = :entityType")
    int countByEntityType(String entityType);

    @Query("SELECT MIN(nextRetryAt) FROM sync_outbox")
    Long getEarliestNextRetryAt();

    @Query("UPDATE sync_outbox SET attemptCount = attemptCount + 1, nextRetryAt = :nextRetryAt, lastError = :lastError WHERE id = :id")
    void recordFailure(long id, long nextRetryAt, String lastError);

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM sync_outbox WHERE entityType = :entityType AND entityId = :entityId")
    void deleteByEntity(String entityType, String entityId);

    @Query("DELETE FROM sync_outbox WHERE entityType = :entityType")
    void deleteByEntityType(String entityType);
}
