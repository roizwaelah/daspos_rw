package com.daspos.model;

public class BestSellerItem {
    private final String productId;
    private final String productName;
    private final int totalQty;
    private final double totalRevenue;

    public BestSellerItem(String productId, String productName, int totalQty, double totalRevenue) {
        this.productId = productId;
        this.productName = productName;
        this.totalQty = totalQty;
        this.totalRevenue = totalRevenue;
    }

    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getTotalQty() { return totalQty; }
    public double getTotalRevenue() { return totalRevenue; }
}
