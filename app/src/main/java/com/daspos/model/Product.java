package com.daspos.model;

public class Product {
    private String id;
    private String name;
    private String barcode;
    private double price;
    private double costPrice;
    private int stock;
    private String unitName;
    private boolean trackStock;
    private double priceEcer;
    private double priceRenteng;
    private double pricePak;
    private double priceKarton;
    private int factorRenteng;
    private int factorPak;
    private int factorKarton;

    public Product(String id, String name, double price, int stock) {
        this.id = id;
        this.name = name;
        this.barcode = "";
        this.price = price;
        this.costPrice = 0;
        this.stock = stock;
        this.unitName = "pcs";
        this.trackStock = true;
        this.priceEcer = price;
        this.factorRenteng = SalesUnit.defaultFactor(SalesUnit.RENTENG);
        this.factorPak = SalesUnit.defaultFactor(SalesUnit.PAK);
        this.factorKarton = SalesUnit.defaultFactor(SalesUnit.KARTON);
        this.priceRenteng = price * factorRenteng;
        this.pricePak = price * factorPak;
        this.priceKarton = price * factorKarton;
    }

    public Product(
            String id,
            String name,
            double price,
            int stock,
            double priceEcer,
            double priceRenteng,
            double pricePak,
            double priceKarton
    ) {
        this(
                id,
                name,
                price,
                stock,
                priceEcer,
                priceRenteng,
                pricePak,
                priceKarton,
                SalesUnit.defaultFactor(SalesUnit.RENTENG),
                SalesUnit.defaultFactor(SalesUnit.PAK),
                SalesUnit.defaultFactor(SalesUnit.KARTON)
        );
    }

    public Product(
            String id,
            String name,
            double price,
            int stock,
            double priceEcer,
            double priceRenteng,
            double pricePak,
            double priceKarton,
            int factorRenteng,
            int factorPak,
            int factorKarton
    ) {
        this(
                id,
                name,
                "",
                price,
                0,
                stock,
                "pcs",
                true,
                priceEcer,
                priceRenteng,
                pricePak,
                priceKarton,
                factorRenteng,
                factorPak,
                factorKarton
        );
    }

    public Product(
            String id,
            String name,
            String barcode,
            double price,
            double costPrice,
            int stock,
            String unitName,
            boolean trackStock,
            double priceEcer,
            double priceRenteng,
            double pricePak,
            double priceKarton,
            int factorRenteng,
            int factorPak,
            int factorKarton
    ) {
        this.id = id;
        this.name = name;
        this.barcode = barcode == null ? "" : barcode.trim();
        this.price = price;
        this.costPrice = Math.max(0, costPrice);
        this.stock = stock;
        this.unitName = normalizeUnitName(unitName);
        this.trackStock = trackStock;
        this.priceEcer = priceEcer;
        this.priceRenteng = priceRenteng;
        this.pricePak = pricePak;
        this.priceKarton = priceKarton;
        this.factorRenteng = factorRenteng <= 0 ? SalesUnit.defaultFactor(SalesUnit.RENTENG) : factorRenteng;
        this.factorPak = factorPak <= 0 ? SalesUnit.defaultFactor(SalesUnit.PAK) : factorPak;
        this.factorKarton = factorKarton <= 0 ? SalesUnit.defaultFactor(SalesUnit.KARTON) : factorKarton;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getBarcode() { return barcode; }
    public double getPrice() { return price; }
    public double getCostPrice() { return costPrice; }
    public int getStock() { return stock; }
    public String getUnitName() { return unitName; }
    public boolean isTrackStock() { return trackStock; }
    public double getPriceEcer() { return priceEcer; }
    public double getPriceRenteng() { return priceRenteng; }
    public double getPricePak() { return pricePak; }
    public double getPriceKarton() { return priceKarton; }
    public int getFactorRenteng() { return factorRenteng; }
    public int getFactorPak() { return factorPak; }
    public int getFactorKarton() { return factorKarton; }

    public void setName(String name) { this.name = name; }
    public void setBarcode(String barcode) { this.barcode = barcode == null ? "" : barcode.trim(); }
    public void setPrice(double price) {
        this.price = price;
        this.priceEcer = price;
    }
    public void setCostPrice(double costPrice) { this.costPrice = Math.max(0, costPrice); }
    public void setStock(int stock) { this.stock = stock; }
    public void setUnitName(String unitName) { this.unitName = normalizeUnitName(unitName); }
    public void setTrackStock(boolean trackStock) { this.trackStock = trackStock; }
    public void setPriceEcer(double priceEcer) {
        this.priceEcer = priceEcer;
        this.price = priceEcer;
    }
    public void setPriceRenteng(double priceRenteng) { this.priceRenteng = priceRenteng; }
    public void setPricePak(double pricePak) { this.pricePak = pricePak; }
    public void setPriceKarton(double priceKarton) { this.priceKarton = priceKarton; }
    public void setFactorRenteng(int factorRenteng) {
        this.factorRenteng = factorRenteng <= 0 ? SalesUnit.defaultFactor(SalesUnit.RENTENG) : factorRenteng;
    }
    public void setFactorPak(int factorPak) {
        this.factorPak = factorPak <= 0 ? SalesUnit.defaultFactor(SalesUnit.PAK) : factorPak;
    }
    public void setFactorKarton(int factorKarton) {
        this.factorKarton = factorKarton <= 0 ? SalesUnit.defaultFactor(SalesUnit.KARTON) : factorKarton;
    }

    public double getUnitPrice(String unitCode) {
        String unit = SalesUnit.normalize(unitCode);
        if (SalesUnit.RENTENG.equals(unit)) return priceRenteng;
        if (SalesUnit.PAK.equals(unit)) return pricePak;
        if (SalesUnit.KARTON.equals(unit)) return priceKarton;
        return priceEcer;
    }

    public int getUnitFactor(String unitCode) {
        String unit = SalesUnit.normalize(unitCode);
        if (SalesUnit.RENTENG.equals(unit)) return factorRenteng;
        if (SalesUnit.PAK.equals(unit)) return factorPak;
        if (SalesUnit.KARTON.equals(unit)) return factorKarton;
        return 1;
    }

    public boolean isTierPricingEnabled() {
        double ecer = Math.max(0, priceEcer);
        boolean differentPrice = Math.abs(priceRenteng - ecer) > 0.0001
                || Math.abs(pricePak - ecer) > 0.0001
                || Math.abs(priceKarton - ecer) > 0.0001;
        boolean differentFactor = factorRenteng > 1 || factorPak > 1 || factorKarton > 1;
        return differentPrice || differentFactor;
    }

    private static String normalizeUnitName(String unitName) {
        String value = unitName == null ? "" : unitName.trim();
        return value.isEmpty() ? "pcs" : value;
    }
}
