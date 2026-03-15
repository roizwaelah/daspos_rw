package com.daspos.model;

import java.util.ArrayList;
import java.util.List;

public class TransactionRecord {
    private String id;
    private String date;
    private String time;
    private double total;
    private double pay;
    private double change;
    private List<CartItem> items = new ArrayList<>();

    public TransactionRecord(String id, String date, String time, double total, double pay, double change, List<CartItem> items) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.total = total;
        this.pay = pay;
        this.change = change;
        this.items = items;
    }

    public String getId() { return id; }
    public String getDate() { return date; }
    public String getTime() { return time; }
    public double getTotal() { return total; }
    public double getPay() { return pay; }
    public double getChange() { return change; }
    public List<CartItem> getItems() { return items; }
}
