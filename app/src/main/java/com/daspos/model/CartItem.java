package com.daspos.model;

public class CartItem {
    private Product product;
    private int qty;
    private String unitCode;
    private int unitFactorToBase;
    private double unitPrice;

    public CartItem(Product product, int qty) {
        this.product = product;
        this.qty = qty;
        this.unitCode = SalesUnit.ECER;
        this.unitFactorToBase = product == null ? 1 : product.getUnitFactor(SalesUnit.ECER);
        this.unitPrice = product == null ? 0 : product.getUnitPrice(SalesUnit.ECER);
    }

    public CartItem(Product product, int qty, String unitCode, int unitFactorToBase, double unitPrice) {
        this.product = product;
        this.qty = qty;
        this.unitCode = SalesUnit.normalize(unitCode);
        this.unitFactorToBase = Math.max(1, unitFactorToBase);
        this.unitPrice = Math.max(0, unitPrice);
    }

    public Product getProduct() { return product; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
    public String getUnitCode() { return unitCode; }
    public int getUnitFactorToBase() { return unitFactorToBase; }
    public double getUnitPrice() { return unitPrice; }

    public void applyUnitFromProduct(String nextUnitCode) {
        String normalized = SalesUnit.normalize(nextUnitCode);
        this.unitCode = normalized;
        if (product != null) {
            this.unitFactorToBase = Math.max(1, product.getUnitFactor(normalized));
            this.unitPrice = Math.max(0, product.getUnitPrice(normalized));
        } else {
            this.unitFactorToBase = Math.max(1, SalesUnit.defaultFactor(normalized));
        }
    }

    public double getSubtotal() {
        return unitPrice * qty;
    }

    public int getBaseQty() {
        return qty * Math.max(1, unitFactorToBase);
    }
}
