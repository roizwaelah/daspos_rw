package com.daspos.model;

public class ReportItem {
    private String id;
    private String date;
    private String time;
    private double total;

    public ReportItem(String id, String date, String time, double total) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.total = total;
    }

    public String getId() { return id; }
    public String getDate() { return date; }
    public String getTime() { return time; }
    public double getTotal() { return total; }
}
