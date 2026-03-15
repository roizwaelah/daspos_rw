package com.daspos.feature.transaction;

import android.content.Context;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.WebView;

import com.daspos.feature.settings.StoreConfigStore;
import com.daspos.model.CartItem;
import com.daspos.model.TransactionRecord;
import com.daspos.shared.util.CurrencyUtils;

public class ReceiptPrintHelper {
    public static void print(Context context, TransactionRecord trx) {
        if (trx == null) return;
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:sans-serif;padding:24px;'>");
        html.append("<h2 style='text-align:center;'>").append(StoreConfigStore.getStoreName(context)).append("</h2>");
        if (!StoreConfigStore.getAddress(context).isEmpty()) {
            html.append("<div style='text-align:center;'>").append(StoreConfigStore.getAddress(context)).append("</div>");
        }
        if (!StoreConfigStore.getPhone(context).isEmpty()) {
            html.append("<div style='text-align:center;'>").append(StoreConfigStore.getPhone(context)).append("</div>");
        }
        if (!StoreConfigStore.getEmail(context).isEmpty()) {
            html.append("<div style='text-align:center;'>").append(StoreConfigStore.getEmail(context)).append("</div>");
        }
        html.append("<div style='text-align:center;'>").append(trx.getDate()).append(" ").append(trx.getTime()).append("</div>");
        html.append("<hr/>");
        for (CartItem item : trx.getItems()) {
            html.append("<div style='display:flex;justify-content:space-between;'>")
                .append("<span>").append(item.getProduct().getName()).append(" x").append(item.getQty()).append("</span>")
                .append("<span>").append(CurrencyUtils.formatRupiah(item.getSubtotal())).append("</span>")
                .append("</div>");
        }
        html.append("<hr/>");
        html.append("<div>Total: ").append(CurrencyUtils.formatRupiah(trx.getTotal())).append("</div>");
        html.append("<div>Bayar: ").append(CurrencyUtils.formatRupiah(trx.getPay())).append("</div>");
        html.append("<div>Kembalian: ").append(CurrencyUtils.formatRupiah(trx.getChange())).append("</div>");
        html.append("</body></html>");

        WebView webView = new WebView(context);
        webView.loadDataWithBaseURL(null, html.toString(), "text/HTML", "UTF-8", null);
        PrintManager printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
        if (printManager != null) {
            printManager.print("daspos_receipt", webView.createPrintDocumentAdapter("daspos_receipt"),
                    new PrintAttributes.Builder().build());
        }
    }
}
