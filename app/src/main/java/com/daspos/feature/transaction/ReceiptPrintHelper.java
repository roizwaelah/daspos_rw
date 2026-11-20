package com.daspos.feature.transaction;

import android.content.Context;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.WebView;

import com.daspos.feature.settings.ReceiptConfigStore;
import com.daspos.feature.settings.StoreConfigStore;
import com.daspos.model.CartItem;
import com.daspos.model.SalesUnit;
import com.daspos.model.TransactionRecord;
import com.daspos.shared.util.CurrencyUtils;

public class ReceiptPrintHelper {
    public static void print(Context context, TransactionRecord trx) {
        if (trx == null) return;
        String receiptHeader = ReceiptConfigStore.getHeader(context);
        String receiptFooter = ReceiptConfigStore.getFooter(context);
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:sans-serif;padding:24px;'>");
        if (!receiptHeader.isEmpty()) {
            html.append("<div style='text-align:center;font-size:18px;font-weight:bold;margin-bottom:12px;'>")
                    .append(escapeHtml(receiptHeader))
                    .append("</div>");
        }
        html.append("<h2 style='text-align:center;'>").append(escapeHtml(StoreConfigStore.getStoreName(context))).append("</h2>");
        if (!StoreConfigStore.getAddress(context).isEmpty()) {
            html.append("<div style='text-align:center;'>").append(escapeHtml(StoreConfigStore.getAddress(context))).append("</div>");
        }
        if (!StoreConfigStore.getPhone(context).isEmpty()) {
            html.append("<div style='text-align:center;'>").append(escapeHtml(StoreConfigStore.getPhone(context))).append("</div>");
        }
        if (!StoreConfigStore.getEmail(context).isEmpty()) {
            html.append("<div style='text-align:center;'>").append(escapeHtml(StoreConfigStore.getEmail(context))).append("</div>");
        }
        html.append("<hr/>");
        html.append("<table style='width:100%;table-layout:fixed;border-collapse:collapse;margin-bottom:8px;'>")
                .append("<tr>")
                .append("<td style='text-align:left;font-size:13px;'>No. Transaksi: ").append(escapeHtml(trx.getId())).append("</td>")
                .append("<td style='text-align:right;font-size:13px;'>").append(escapeHtml(trx.getDate())).append(" ").append(escapeHtml(trx.getTime())).append("</td>")
                .append("</tr>")
                .append("</table>");
        html.append("<hr/>");
        for (CartItem item : trx.getItems()) {
            String detail = item.getQty()
                    + " "
                    + displayUnit(item)
                    + " x "
                    + CurrencyUtils.formatRupiah(item.getUnitPrice());
            html.append("<div style='display:flex;justify-content:space-between;'>")
                .append("<span><strong>").append(escapeHtml(item.getProduct().getName())).append("</strong><br/><small>").append(escapeHtml(detail)).append("</small></span>")
                .append("<span>").append(CurrencyUtils.formatRupiah(item.getSubtotal())).append("</span>")
                .append("</div>")
                .append("<div style='height:8px;'></div>");
        }
        html.append("<hr/>");
        if (trx.getDiscount() > 0) {
            html.append("<div style='text-align:right;font-weight:600;'>Diskon: ")
                    .append(CurrencyUtils.formatRupiah(trx.getDiscount()))
                    .append("</div>");
        }
        html.append("<div style='text-align:right;font-weight:600;'>Total: ")
                .append(CurrencyUtils.formatRupiah(trx.getTotal()))
                .append("</div>");
        html.append("<div style='text-align:right;font-weight:600;'>Bayar: ")
                .append(CurrencyUtils.formatRupiah(trx.getPay()))
                .append("</div>");
        html.append("<div style='text-align:right;font-weight:600;'>Kembalian: ")
                .append(CurrencyUtils.formatRupiah(trx.getChange()))
                .append("</div>");
        html.append("<div style='text-align:center;margin-top:16px;'>").append(escapeHtml(receiptFooter)).append("</div>");
        html.append("</body></html>");

        WebView webView = new WebView(context);
        webView.loadDataWithBaseURL(null, html.toString(), "text/HTML", "UTF-8", null);
        PrintManager printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
        if (printManager != null) {
            printManager.print("daspos_receipt", webView.createPrintDocumentAdapter("daspos_receipt"),
                    new PrintAttributes.Builder().build());
        }
    }

    private static String displayUnit(CartItem item) {
        if (item == null || item.getProduct() == null) return "pcs";
        String unitCode = SalesUnit.normalize(item.getUnitCode());
        if (item.getProduct().isTierPricingEnabled() && !SalesUnit.ECER.equals(unitCode)) {
            return SalesUnit.displayName(unitCode);
        }
        String unitName = item.getProduct().getUnitName();
        return unitName == null || unitName.trim().isEmpty() ? "pcs" : unitName.trim();
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
