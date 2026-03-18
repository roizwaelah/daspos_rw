package com.daspos.feature.printer;

import android.content.Context;

import com.daspos.feature.settings.ReceiptConfigStore;
import com.daspos.feature.settings.StoreConfigStore;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.TransactionRecord;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

public class EscPosNetworkPrinterHelper {
    public enum PrintResult {
        SUCCESS,
        INVALID_CONFIG,
        CONNECTION_FAILED,
        WRITE_FAILED
    }

    public static PrintResult printReceipt(Context context, String ip, int port, TransactionRecord trx) {
        if (trx == null || ip == null || ip.trim().isEmpty() || port <= 0) return PrintResult.INVALID_CONFIG;

        String receiptHeader = ReceiptConfigStore.getHeader(context);
        String receiptFooter = ReceiptConfigStore.getFooter(context);
        String storeName = StoreConfigStore.getStoreName(context);
        String addressLine = StoreConfigStore.getAddress(context);
        String phoneLine = StoreConfigStore.getPhone(context);
        String emailLine = StoreConfigStore.getEmail(context);

        Socket socket = null;
        OutputStream out = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip.trim(), port), 3000);
            socket.setSoTimeout(3000);
            out = socket.getOutputStream();

            byte[] init = new byte[]{0x1B, 0x40};
            byte[] alignCenter = new byte[]{0x1B, 0x61, 0x01};
            byte[] alignLeft = new byte[]{0x1B, 0x61, 0x00};
            byte[] cut = new byte[]{0x1D, 0x56, 0x41, 0x10};

            out.write(init);
            out.write(alignCenter);
            if (!receiptHeader.isEmpty()) out.write((receiptHeader + "\n").getBytes(StandardCharsets.UTF_8));
            out.write((storeName + "\n").getBytes(StandardCharsets.UTF_8));
            if (!addressLine.isEmpty()) out.write((addressLine + "\n").getBytes(StandardCharsets.UTF_8));
            if (!phoneLine.isEmpty()) out.write((phoneLine + "\n").getBytes(StandardCharsets.UTF_8));
            if (!emailLine.isEmpty()) out.write((emailLine + "\n").getBytes(StandardCharsets.UTF_8));
            out.write((trx.getDate() + " " + trx.getTime() + "\n").getBytes(StandardCharsets.UTF_8));
            out.write("--------------------------------\n".getBytes(StandardCharsets.UTF_8));
            out.write(alignLeft);

            for (CartItem item : trx.getItems()) {
                String line = item.getProduct().getName() + " x" + item.getQty() + "  " + ((long) item.getSubtotal()) + "\n";
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }

            out.write("--------------------------------\n".getBytes(StandardCharsets.UTF_8));
            out.write(("Total: " + ((long) trx.getTotal()) + "\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Bayar: " + ((long) trx.getPay()) + "\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Kembali: " + ((long) trx.getChange()) + "\n").getBytes(StandardCharsets.UTF_8));
            out.write(alignCenter);
            out.write((receiptFooter + "\n").getBytes(StandardCharsets.UTF_8));
            out.write(cut);
            out.flush();
            return PrintResult.SUCCESS;
        } catch (java.io.IOException e) {
            return PrintResult.CONNECTION_FAILED;
        } catch (Exception e) {
            return PrintResult.WRITE_FAILED;
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) { }
            try { if (socket != null) socket.close(); } catch (Exception ignored) { }
        }
    }

    public static PrintResult testConnection(Context context, String ip, int port) {
        TransactionRecord dummy = new TransactionRecord(
                "#TEST",
                "01 Jan 2026",
                "10:00",
                1000,
                1000,
                0,
                new ArrayList<CartItem>() {{
                    add(new CartItem(new Product(UUID.randomUUID().toString(), "Test Print", 1000, 1), 1));
                }}
        );
        return printReceipt(context, ip, port, dummy);
    }
}
