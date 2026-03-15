package com.daspos.feature.transaction;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.printer.EscPosBluetoothPrinterHelper;
import com.daspos.feature.printer.EscPosNetworkPrinterHelper;
import com.daspos.feature.printer.PrinterConfigStore;
import com.daspos.feature.settings.StoreConfigStore;
import com.daspos.model.CartItem;
import com.daspos.model.TransactionRecord;
import com.daspos.repository.TransactionRepository;
import com.daspos.shared.util.CurrencyUtils;
import com.daspos.shared.util.ViewUtils;

public class StrukActivity extends BaseActivity {
    private static final int REQ_BT_PRINT = 811;
    private TransactionRecord currentTransaction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_struk);

        currentTransaction = TransactionRepository.getLast(this);
        renderLastTransaction();

        findViewById(R.id.btnPrintReceipt).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { printReceipt(); }
        });
        findViewById(R.id.btnFinish).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        if (getIntent().getBooleanExtra("auto_print", false)) {
            findViewById(R.id.btnPrintReceipt).post(new Runnable() {
                @Override public void run() { printReceipt(); }
            });
        }
    }

    private void printReceipt() {
        String type = PrinterConfigStore.getType(this);
        if ("bluetooth".equals(type)) printBluetooth();
        else if ("network".equals(type)) printNetwork();
        else {
            ReceiptPrintHelper.print(this, currentTransaction);
            ViewUtils.toast(this, getString(R.string.print_started));
        }
    }

    private void printBluetooth() {
        if (PrinterConfigStore.getBluetoothAddress(this).isEmpty() && PrinterConfigStore.getBluetoothName(this).isEmpty()) {
            ViewUtils.toast(this, getString(R.string.printer_not_configured));
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT_PRINT);
            return;
        }

        EscPosBluetoothPrinterHelper.PrintResult result = EscPosBluetoothPrinterHelper.printReceipt(this, currentTransaction);
        if (result == EscPosBluetoothPrinterHelper.PrintResult.SUCCESS) ViewUtils.toast(this, getString(R.string.print_started));
        else if (result == EscPosBluetoothPrinterHelper.PrintResult.PERMISSION_REQUIRED) ViewUtils.toast(this, getString(R.string.bluetooth_permission_required));
        else if (result == EscPosBluetoothPrinterHelper.PrintResult.PRINTER_NOT_FOUND) ViewUtils.toast(this, getString(R.string.printer_not_configured));
        else if (result == EscPosBluetoothPrinterHelper.PrintResult.CONNECTION_FAILED) ViewUtils.toast(this, getString(R.string.printer_connection_failed));
        else ViewUtils.toast(this, getString(R.string.printer_write_failed));
    }

    private void printNetwork() {
        String ip = PrinterConfigStore.getIp(this);
        int port;
        try { port = Integer.parseInt(PrinterConfigStore.getPort(this)); }
        catch (Exception e) {
            ViewUtils.toast(this, getString(R.string.network_printer_invalid));
            return;
        }

        EscPosNetworkPrinterHelper.PrintResult result = EscPosNetworkPrinterHelper.printReceipt(this, ip, port, currentTransaction);
        if (result == EscPosNetworkPrinterHelper.PrintResult.SUCCESS) ViewUtils.toast(this, getString(R.string.network_print_started));
        else if (result == EscPosNetworkPrinterHelper.PrintResult.INVALID_CONFIG) ViewUtils.toast(this, getString(R.string.network_printer_invalid));
        else if (result == EscPosNetworkPrinterHelper.PrintResult.CONNECTION_FAILED) ViewUtils.toast(this, getString(R.string.network_printer_connect_failed));
        else ViewUtils.toast(this, getString(R.string.printer_write_failed));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_PRINT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) printBluetooth();
            else ViewUtils.toast(this, getString(R.string.bluetooth_permission_required));
        }
    }

    private void renderLastTransaction() {
        TransactionRecord transaction = currentTransaction;
        if (transaction == null) return;

        ((TextView) findViewById(R.id.tvReceiptStoreName)).setText(StoreConfigStore.getStoreName(this));
        ((TextView) findViewById(R.id.tvReceiptDate)).setText(transaction.getDate() + " " + transaction.getTime());
        ((TextView) findViewById(R.id.tvReceiptAddress)).setText(StoreConfigStore.getAddress(this));
        ((TextView) findViewById(R.id.tvReceiptPhone)).setText(StoreConfigStore.getPhone(this));
        ((TextView) findViewById(R.id.tvReceiptEmail)).setText(StoreConfigStore.getEmail(this));
        ((TextView) findViewById(R.id.tvTotalSummary)).setText("Total: " + CurrencyUtils.formatRupiah(transaction.getTotal()));
        ((TextView) findViewById(R.id.tvPaySummary)).setText("Bayar: " + CurrencyUtils.formatRupiah(transaction.getPay()));
        ((TextView) findViewById(R.id.tvChangeSummary)).setText("Kembalian: " + CurrencyUtils.formatRupiah(transaction.getChange()));

        boolean hasIdentityLine = !StoreConfigStore.getAddress(this).isEmpty()
                || !StoreConfigStore.getPhone(this).isEmpty()
                || !StoreConfigStore.getEmail(this).isEmpty();
        findViewById(R.id.layoutReceiptIdentity).setVisibility(hasIdentityLine ? View.VISIBLE : View.GONE);

        ImageView logo = findViewById(R.id.imgReceiptStoreLogo);
        String logoUri = StoreConfigStore.getLogoUri(this);
        if (logoUri != null && !logoUri.trim().isEmpty()) {
            try {
                logo.setImageURI(Uri.parse(logoUri));
                logo.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                logo.setVisibility(View.GONE);
            }
        } else {
            logo.setVisibility(View.GONE);
        }

        LinearLayout layoutItems = findViewById(R.id.layoutItems);
        layoutItems.removeAllViews();
        for (CartItem item : transaction.getItems()) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_receipt_line, layoutItems, false);
            ((TextView) row.findViewById(R.id.tvReceiptItemName)).setText(item.getProduct().getName() + " x" + item.getQty());
            ((TextView) row.findViewById(R.id.tvReceiptItemSubtotal)).setText(CurrencyUtils.formatRupiah(item.getSubtotal()));
            layoutItems.addView(row);
        }
    }
}
