package com.daspos.feature.product;

import com.daspos.model.ReportItem;

import java.util.ArrayList;
import java.util.List;

public class SampleDataRepository {
    public static List<ReportItem> getImportPreviewRows() {
        List<ReportItem> list = new ArrayList<>();
        list.add(new ReportItem("PRD001", "14 Mar 2026", "Kopi Susu", 18000));
        list.add(new ReportItem("PRD002", "14 Mar 2026", "Mie Instan", 4500));
        list.add(new ReportItem("PRD003", "14 Mar 2026", "Air Mineral", 4000));
        list.add(new ReportItem("PRD004", "14 Mar 2026", "Roti Coklat", 7000));
        return list;
    }
}
