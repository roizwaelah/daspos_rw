package com.daspos.feature.printer;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;

import com.daspos.feature.settings.ReceiptConfigStore;
import com.daspos.feature.settings.StoreConfigStore;
import com.daspos.model.CartItem;
import com.daspos.model.TransactionRecord;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class EscPosBluetoothPrinterHelper {
    public enum PrintResult {
        SUCCESS,
        PERMISSION_REQUIRED,
        PRINTER_NOT_FOUND,
        CONNECTION_FAILED,
        WRITE_FAILED
    }

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static PrintResult printReceipt(Context context, TransactionRecord trx) {
        if (trx == null) return PrintResult.WRITE_FAILED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return PrintResult.PERMISSION_REQUIRED;
        }

        String address = PrinterConfigStore.getBluetoothAddress(context);
        String name = PrinterConfigStore.getBluetoothName(context);
        String receiptHeader = ReceiptConfigStore.getHeader(context);
        String receiptFooter = ReceiptConfigStore.getFooter(context);
        String storeName = StoreConfigStore.getStoreName(context);
        String addressLine = StoreConfigStore.getAddress(context);
        String phoneLine = StoreConfigStore.getPhone(context);
        String emailLine = StoreConfigStore.getEmail(context);

        BluetoothSocket socket = null;
        OutputStream out = null;
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return PrintResult.PRINTER_NOT_FOUND;
            if (adapter.isDiscovering()) adapter.cancelDiscovery();

            BluetoothDevice target = null;
            if (address != null && !address.isEmpty()) {
                target = adapter.getRemoteDevice(address);
            } else {
                Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                if (bonded != null) {
                    for (BluetoothDevice d : bonded) {
                        if (name != null && name.equalsIgnoreCase(d.getName())) {
                            target = d;
                            break;
                        }
                    }
                }
            }

            if (target == null) return PrintResult.PRINTER_NOT_FOUND;

            socket = target.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
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
            out.write(("Kembali: " + ((long) trx.getChange()) + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.write(alignCenter);
            out.write((receiptFooter + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.write(cut);
            out.flush();
            return PrintResult.SUCCESS;
        } catch (SecurityException e) {
            return PrintResult.PERMISSION_REQUIRED;
        } catch (java.io.IOException e) {
            return PrintResult.CONNECTION_FAILED;
        } catch (Exception e) {
            return PrintResult.WRITE_FAILED;
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) { }
            try { if (socket != null) socket.close(); } catch (Exception ignored) { }
        }
    }
}
