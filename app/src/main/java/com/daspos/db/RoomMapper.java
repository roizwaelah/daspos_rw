package com.daspos.db;

import com.daspos.db.entity.ProductEntity;
import com.daspos.db.entity.TransactionEntity;
import com.daspos.db.entity.TransactionItemEntity;
import com.daspos.db.entity.UserEntity;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.TransactionRecord;
import com.daspos.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RoomMapper {
    public static ProductEntity toEntity(Product model) {
        return new ProductEntity(model.getId(), model.getName(), model.getPrice(), model.getStock());
    }

    public static Product toModel(ProductEntity entity) {
        return new Product(entity.id, entity.name, entity.price, entity.stock);
    }

    public static UserEntity toEntity(User model) {
        return new UserEntity(model.getUsername(), model.getRole(), "");
    }

    public static User toModel(UserEntity entity) {
        return new User(entity.username, entity.role);
    }

    public static TransactionEntity toTransactionEntity(TransactionRecord record, long timestamp) {
        return new TransactionEntity(record.getId(), record.getDate(), record.getTime(), timestamp, record.getTotal(), record.getPay(), record.getChange());
    }

    public static List<TransactionItemEntity> toTransactionItemEntities(TransactionRecord record) {
        List<TransactionItemEntity> list = new ArrayList<>();
        for (CartItem item : record.getItems()) {
            list.add(new TransactionItemEntity(
                    UUID.randomUUID().toString(),
                    record.getId(),
                    item.getProduct().getId(),
                    item.getProduct().getName(),
                    item.getProduct().getPrice(),
                    item.getQty()
            ));
        }
        return list;
    }
}
