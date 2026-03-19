package com.daspos.db.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.daspos.db.entity.TransactionEntity;
import com.daspos.db.entity.TransactionItemEntity;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TransactionDao_Impl implements TransactionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TransactionEntity> __insertionAdapterOfTransactionEntity;

  private final EntityInsertionAdapter<TransactionItemEntity> __insertionAdapterOfTransactionItemEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteItemsByTransactionId;

  private final SharedSQLiteStatement __preparedStmtOfDeleteTransactionById;

  public TransactionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTransactionEntity = new EntityInsertionAdapter<TransactionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `transactions` (`id`,`date`,`time`,`timestamp`,`total`,`pay`,`changeAmount`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final TransactionEntity entity) {
        if (entity.id == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.id);
        }
        if (entity.date == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.date);
        }
        if (entity.time == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.time);
        }
        statement.bindLong(4, entity.timestamp);
        statement.bindDouble(5, entity.total);
        statement.bindDouble(6, entity.pay);
        statement.bindDouble(7, entity.changeAmount);
      }
    };
    this.__insertionAdapterOfTransactionItemEntity = new EntityInsertionAdapter<TransactionItemEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `transaction_items` (`id`,`transactionId`,`productId`,`productName`,`price`,`qty`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final TransactionItemEntity entity) {
        if (entity.id == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.id);
        }
        if (entity.transactionId == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.transactionId);
        }
        if (entity.productId == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.productId);
        }
        if (entity.productName == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.productName);
        }
        statement.bindDouble(5, entity.price);
        statement.bindLong(6, entity.qty);
      }
    };
    this.__preparedStmtOfDeleteItemsByTransactionId = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM transaction_items WHERE transactionId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteTransactionById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM transactions WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public void insertTransaction(final TransactionEntity item) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfTransactionEntity.insert(item);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void insertTransactionItems(final List<TransactionItemEntity> items) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfTransactionItemEntity.insert(items);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteItemsByTransactionId(final String transactionId) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteItemsByTransactionId.acquire();
    int _argIndex = 1;
    if (transactionId == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, transactionId);
    }
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteItemsByTransactionId.release(_stmt);
    }
  }

  @Override
  public void deleteTransactionById(final String transactionId) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteTransactionById.acquire();
    int _argIndex = 1;
    if (transactionId == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, transactionId);
    }
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteTransactionById.release(_stmt);
    }
  }

  @Override
  public List<TransactionEntity> getAllTransactions() {
    final String _sql = "SELECT * FROM transactions ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
      final int _cursorIndexOfTime = CursorUtil.getColumnIndexOrThrow(_cursor, "time");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfTotal = CursorUtil.getColumnIndexOrThrow(_cursor, "total");
      final int _cursorIndexOfPay = CursorUtil.getColumnIndexOrThrow(_cursor, "pay");
      final int _cursorIndexOfChangeAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "changeAmount");
      final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final TransactionEntity _item;
        final String _tmpId;
        if (_cursor.isNull(_cursorIndexOfId)) {
          _tmpId = null;
        } else {
          _tmpId = _cursor.getString(_cursorIndexOfId);
        }
        final String _tmpDate;
        if (_cursor.isNull(_cursorIndexOfDate)) {
          _tmpDate = null;
        } else {
          _tmpDate = _cursor.getString(_cursorIndexOfDate);
        }
        final String _tmpTime;
        if (_cursor.isNull(_cursorIndexOfTime)) {
          _tmpTime = null;
        } else {
          _tmpTime = _cursor.getString(_cursorIndexOfTime);
        }
        final long _tmpTimestamp;
        _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        final double _tmpTotal;
        _tmpTotal = _cursor.getDouble(_cursorIndexOfTotal);
        final double _tmpPay;
        _tmpPay = _cursor.getDouble(_cursorIndexOfPay);
        final double _tmpChangeAmount;
        _tmpChangeAmount = _cursor.getDouble(_cursorIndexOfChangeAmount);
        _item = new TransactionEntity(_tmpId,_tmpDate,_tmpTime,_tmpTimestamp,_tmpTotal,_tmpPay,_tmpChangeAmount);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public TransactionEntity getLastTransaction() {
    final String _sql = "SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
      final int _cursorIndexOfTime = CursorUtil.getColumnIndexOrThrow(_cursor, "time");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfTotal = CursorUtil.getColumnIndexOrThrow(_cursor, "total");
      final int _cursorIndexOfPay = CursorUtil.getColumnIndexOrThrow(_cursor, "pay");
      final int _cursorIndexOfChangeAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "changeAmount");
      final TransactionEntity _result;
      if (_cursor.moveToFirst()) {
        final String _tmpId;
        if (_cursor.isNull(_cursorIndexOfId)) {
          _tmpId = null;
        } else {
          _tmpId = _cursor.getString(_cursorIndexOfId);
        }
        final String _tmpDate;
        if (_cursor.isNull(_cursorIndexOfDate)) {
          _tmpDate = null;
        } else {
          _tmpDate = _cursor.getString(_cursorIndexOfDate);
        }
        final String _tmpTime;
        if (_cursor.isNull(_cursorIndexOfTime)) {
          _tmpTime = null;
        } else {
          _tmpTime = _cursor.getString(_cursorIndexOfTime);
        }
        final long _tmpTimestamp;
        _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        final double _tmpTotal;
        _tmpTotal = _cursor.getDouble(_cursorIndexOfTotal);
        final double _tmpPay;
        _tmpPay = _cursor.getDouble(_cursorIndexOfPay);
        final double _tmpChangeAmount;
        _tmpChangeAmount = _cursor.getDouble(_cursorIndexOfChangeAmount);
        _result = new TransactionEntity(_tmpId,_tmpDate,_tmpTime,_tmpTimestamp,_tmpTotal,_tmpPay,_tmpChangeAmount);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public TransactionEntity getTransactionById(final String transactionId) {
    final String _sql = "SELECT * FROM transactions WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (transactionId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, transactionId);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
      final int _cursorIndexOfTime = CursorUtil.getColumnIndexOrThrow(_cursor, "time");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfTotal = CursorUtil.getColumnIndexOrThrow(_cursor, "total");
      final int _cursorIndexOfPay = CursorUtil.getColumnIndexOrThrow(_cursor, "pay");
      final int _cursorIndexOfChangeAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "changeAmount");
      final TransactionEntity _result;
      if (_cursor.moveToFirst()) {
        final String _tmpId;
        if (_cursor.isNull(_cursorIndexOfId)) {
          _tmpId = null;
        } else {
          _tmpId = _cursor.getString(_cursorIndexOfId);
        }
        final String _tmpDate;
        if (_cursor.isNull(_cursorIndexOfDate)) {
          _tmpDate = null;
        } else {
          _tmpDate = _cursor.getString(_cursorIndexOfDate);
        }
        final String _tmpTime;
        if (_cursor.isNull(_cursorIndexOfTime)) {
          _tmpTime = null;
        } else {
          _tmpTime = _cursor.getString(_cursorIndexOfTime);
        }
        final long _tmpTimestamp;
        _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        final double _tmpTotal;
        _tmpTotal = _cursor.getDouble(_cursorIndexOfTotal);
        final double _tmpPay;
        _tmpPay = _cursor.getDouble(_cursorIndexOfPay);
        final double _tmpChangeAmount;
        _tmpChangeAmount = _cursor.getDouble(_cursorIndexOfChangeAmount);
        _result = new TransactionEntity(_tmpId,_tmpDate,_tmpTime,_tmpTimestamp,_tmpTotal,_tmpPay,_tmpChangeAmount);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<TransactionItemEntity> getItemsByTransactionId(final String transactionId) {
    final String _sql = "SELECT * FROM transaction_items WHERE transactionId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (transactionId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, transactionId);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfTransactionId = CursorUtil.getColumnIndexOrThrow(_cursor, "transactionId");
      final int _cursorIndexOfProductId = CursorUtil.getColumnIndexOrThrow(_cursor, "productId");
      final int _cursorIndexOfProductName = CursorUtil.getColumnIndexOrThrow(_cursor, "productName");
      final int _cursorIndexOfPrice = CursorUtil.getColumnIndexOrThrow(_cursor, "price");
      final int _cursorIndexOfQty = CursorUtil.getColumnIndexOrThrow(_cursor, "qty");
      final List<TransactionItemEntity> _result = new ArrayList<TransactionItemEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final TransactionItemEntity _item;
        final String _tmpId;
        if (_cursor.isNull(_cursorIndexOfId)) {
          _tmpId = null;
        } else {
          _tmpId = _cursor.getString(_cursorIndexOfId);
        }
        final String _tmpTransactionId;
        if (_cursor.isNull(_cursorIndexOfTransactionId)) {
          _tmpTransactionId = null;
        } else {
          _tmpTransactionId = _cursor.getString(_cursorIndexOfTransactionId);
        }
        final String _tmpProductId;
        if (_cursor.isNull(_cursorIndexOfProductId)) {
          _tmpProductId = null;
        } else {
          _tmpProductId = _cursor.getString(_cursorIndexOfProductId);
        }
        final String _tmpProductName;
        if (_cursor.isNull(_cursorIndexOfProductName)) {
          _tmpProductName = null;
        } else {
          _tmpProductName = _cursor.getString(_cursorIndexOfProductName);
        }
        final double _tmpPrice;
        _tmpPrice = _cursor.getDouble(_cursorIndexOfPrice);
        final int _tmpQty;
        _tmpQty = _cursor.getInt(_cursorIndexOfQty);
        _item = new TransactionItemEntity(_tmpId,_tmpTransactionId,_tmpProductId,_tmpProductName,_tmpPrice,_tmpQty);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int count() {
    final String _sql = "SELECT COUNT(*) FROM transactions";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public int countByTimestampRange(final long startTimestamp, final long endTimestamp) {
    final String _sql = "SELECT COUNT(*) FROM transactions WHERE timestamp >= ? AND timestamp < ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startTimestamp);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endTimestamp);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _result;
      if (_cursor.moveToFirst()) {
        _result = _cursor.getInt(0);
      } else {
        _result = 0;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
