package com.daspos.feature.printer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;

import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.transaction.StrukActivity;
import com.daspos.shared.util.ViewUtils;
import com.google.android.material.textfield.TextInputEditText;

public class PrinterSettingActivity extends BaseActivity {
    private static final int REQ_BT = 701;
    private String selectedBluetoothName = "";
    private String selectedBluetoothAddress = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_printer_setting);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.printer));
        final RadioGroup group = findViewById(R.id.rgPrinterType);
        final View layoutNetwork = findViewById(R.id.layoutNetwork);
        final View btnChooseBluetooth = findViewById(R.id.btnChooseBluetooth);
        final TextInputEditText etIp = findViewById(R.id.etPrinterIp);
        final TextInputEditText etPort = findViewById(R.id.etPrinterPort);

        selectedBluetoothName = PrinterConfigStore.getBluetoothName(this);
        selectedBluetoothAddress = PrinterConfigStore.getBluetoothAddress(this);
        String type = PrinterConfigStore.getType(this);
        etIp.setText(PrinterConfigStore.getIp(this));
        etPort.setText(PrinterConfigStore.getPort(this));

        if ("bluetooth".equals(type)) group.check(R.id.rbBluetooth);
        else if ("network".equals(type)) group.check(R.id.rbNetwork);
        else if ("usb".equals(type)) group.check(R.id.rbUsb);
        else group.check(R.id.rbNone);

        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int checkedId) {
                layoutNetwork.setVisibility(checkedId == R.id.rbNetwork ? View.VISIBLE : View.GONE);
                btnChooseBluetooth.setVisibility(checkedId == R.id.rbBluetooth ? View.VISIBLE : View.GONE);
            }
        });
        group.check(group.getCheckedRadioButtonId());

        findViewById(R.id.btnChooseBluetooth).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivityForResult(new Intent(PrinterSettingActivity.this, BluetoothScanActivity.class), REQ_BT);
            }
        });
        findViewById(R.id.btnTestPrint).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!saveConfig()) return;
                if ("network".equals(currentType())) testNetworkPrinter();
                else {
                    Intent i = new Intent(PrinterSettingActivity.this, StrukActivity.class);
                    i.putExtra("auto_print", true);
                    startActivity(i);
                }
            }
        });
        findViewById(R.id.btnPrinterSave).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!saveConfig()) return;
                ViewUtils.toast(PrinterSettingActivity.this, getString("network".equals(currentType()) ? R.string.network_printer_saved : R.string.printer_saved));
            }
        });
        findViewById(R.id.btnPrinterCancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_BT && resultCode == RESULT_OK && data != null) {
            selectedBluetoothName = String.valueOf(data.getStringExtra("printer_name") == null ? "" : data.getStringExtra("printer_name"));
            selectedBluetoothAddress = String.valueOf(data.getStringExtra("printer_address") == null ? "" : data.getStringExtra("printer_address"));
            ViewUtils.toast(this, getString(R.string.selected_printer) + ": " + selectedBluetoothName);
        }
    }

    private String currentType() {
        RadioGroup group = findViewById(R.id.rgPrinterType);
        if (group.getCheckedRadioButtonId() == R.id.rbBluetooth) return "bluetooth";
        if (group.getCheckedRadioButtonId() == R.id.rbUsb) return "usb";
        if (group.getCheckedRadioButtonId() == R.id.rbNetwork) return "network";
        return "none";
    }

    private boolean saveConfig() {
        TextInputEditText etIp = findViewById(R.id.etPrinterIp);
        TextInputEditText etPort = findViewById(R.id.etPrinterPort);
        String type = currentType();
        String ip = String.valueOf(etIp.getText() == null ? "" : etIp.getText()).trim();
        String port = String.valueOf(etPort.getText() == null ? "" : etPort.getText()).trim();
        if ("network".equals(type)) {
            try {
                int portNum = Integer.parseInt(port);
                if (ip.isEmpty() || portNum <= 0 || portNum > 65535) { ViewUtils.toast(this, getString(R.string.network_printer_invalid)); return false; }
            } catch (Exception e) { ViewUtils.toast(this, getString(R.string.network_printer_invalid)); return false; }
        }
        PrinterConfigStore.save(this, type, selectedBluetoothName, selectedBluetoothAddress, ip, port);
        return true;
    }

    private void testNetworkPrinter() {
        String ip = PrinterConfigStore.getIp(this);
        int port;
        try { port = Integer.parseInt(PrinterConfigStore.getPort(this)); }
        catch (Exception e) { ViewUtils.toast(this, getString(R.string.network_printer_invalid)); return; }
        EscPosNetworkPrinterHelper.PrintResult result = EscPosNetworkPrinterHelper.testConnection(this, ip, port);
        if (result == EscPosNetworkPrinterHelper.PrintResult.SUCCESS) ViewUtils.toast(this, getString(R.string.network_test_success));
        else if (result == EscPosNetworkPrinterHelper.PrintResult.INVALID_CONFIG) ViewUtils.toast(this, getString(R.string.network_printer_invalid));
        else ViewUtils.toast(this, getString(R.string.network_test_failed));
    }
}
