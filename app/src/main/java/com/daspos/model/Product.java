package com.daspos.model;

public class Product {
    private String id;
    private String name;
    private double price;
    private int stock;
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
        this.price = price;
        this.stock = stock;
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
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
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
    public double getPrice() { return price; }
    public int getStock() { return stock; }
    public double getPriceEcer() { return priceEcer; }
    public double getPriceRenteng() { return priceRenteng; }
    public double getPricePak() { return pricePak; }
    public double getPriceKarton() { return priceKarton; }
    public int getFactorRenteng() { return factorRenteng; }
    public int getFactorPak() { return factorPak; }
    public int getFactorKarton() { return factorKarton; }

    public void setName(String name) { this.name = name; }
    public void setPrice(double price) {
        this.price = price;
        this.priceEcer = price;
    }
    public void setStock(int stock) { this.stock = stock; }
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
}
