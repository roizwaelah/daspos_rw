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
import com.daspos.db.entity.UserEntity;
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
public final class UserDao_Impl implements UserDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<UserEntity> __insertionAdapterOfUserEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByUsername;

  public UserDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfUserEntity = new EntityInsertionAdapter<UserEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `users` (`username`,`role`,`passwordHash`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final UserEntity entity) {
        if (entity.username == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.username);
        }
        if (entity.role == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.role);
        }
        if (entity.passwordHash == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.passwordHash);
        }
      }
    };
    this.__preparedStmtOfDeleteByUsername = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM users WHERE username = ?";
        return _query;
      }
    };
  }

  @Override
  public void insertAll(final List<UserEntity> items) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfUserEntity.insert(items);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void insert(final UserEntity item) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfUserEntity.insert(item);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteByUsername(final String username) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByUsername.acquire();
    int _argIndex = 1;
    if (username == null) {
      _stmt.bindNull(_argIndex);
    } else {
      _stmt.bindString(_argIndex, username);
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
      __preparedStmtOfDeleteByUsername.release(_stmt);
    }
  }

  @Override
  public List<UserEntity> getAll() {
    final String _sql = "SELECT * FROM users ORDER BY username ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "username");
      final int _cursorIndexOfRole = CursorUtil.getColumnIndexOrThrow(_cursor, "role");
      final int _cursorIndexOfPasswordHash = CursorUtil.getColumnIndexOrThrow(_cursor, "passwordHash");
      final List<UserEntity> _result = new ArrayList<UserEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final UserEntity _item;
        final String _tmpUsername;
        if (_cursor.isNull(_cursorIndexOfUsername)) {
          _tmpUsername = null;
        } else {
          _tmpUsername = _cursor.getString(_cursorIndexOfUsername);
        }
        final String _tmpRole;
        if (_cursor.isNull(_cursorIndexOfRole)) {
          _tmpRole = null;
        } else {
          _tmpRole = _cursor.getString(_cursorIndexOfRole);
        }
        final String _tmpPasswordHash;
        if (_cursor.isNull(_cursorIndexOfPasswordHash)) {
          _tmpPasswordHash = null;
        } else {
          _tmpPasswordHash = _cursor.getString(_cursorIndexOfPasswordHash);
        }
        _item = new UserEntity(_tmpUsername,_tmpRole,_tmpPasswordHash);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public UserEntity getByUsername(final String username) {
    final String _sql = "SELECT * FROM users WHERE username = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (username == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, username);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "username");
      final int _cursorIndexOfRole = CursorUtil.getColumnIndexOrThrow(_cursor, "role");
      final int _cursorIndexOfPasswordHash = CursorUtil.getColumnIndexOrThrow(_cursor, "passwordHash");
      final UserEntity _result;
      if (_cursor.moveToFirst()) {
        final String _tmpUsername;
        if (_cursor.isNull(_cursorIndexOfUsername)) {
          _tmpUsername = null;
        } else {
          _tmpUsername = _cursor.getString(_cursorIndexOfUsername);
        }
        final String _tmpRole;
        if (_cursor.isNull(_cursorIndexOfRole)) {
          _tmpRole = null;
        } else {
          _tmpRole = _cursor.getString(_cursorIndexOfRole);
        }
        final String _tmpPasswordHash;
        if (_cursor.isNull(_cursorIndexOfPasswordHash)) {
          _tmpPasswordHash = null;
        } else {
          _tmpPasswordHash = _cursor.getString(_cursorIndexOfPasswordHash);
        }
        _result = new UserEntity(_tmpUsername,_tmpRole,_tmpPasswordHash);
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
  public int count() {
    final String _sql = "SELECT COUNT(*) FROM users";
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
