package com.daspos.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "products", indices = {@Index(value = {"name"})})
public class ProductEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String name;
    public String barcode;
    public double price;
    public double costPrice;
    public int stock;
    public String unitName;
    public boolean trackStock;

    @Ignore
    public ProductEntity(@NonNull String id, String name, double price, int stock) {
        this(id, name, "", price, 0, stock, "pcs", true);
    }

    public ProductEntity(@NonNull String id, String name, String barcode, double price, double costPrice, int stock, String unitName, boolean trackStock) {
        this.id = id;
        this.name = name;
        this.barcode = barcode == null ? "" : barcode.trim();
        this.price = price;
        this.costPrice = Math.max(0, costPrice);
        this.stock = stock;
        this.unitName = unitName == null || unitName.trim().isEmpty() ? "pcs" : unitName.trim();
        this.trackStock = trackStock;
    }
}
