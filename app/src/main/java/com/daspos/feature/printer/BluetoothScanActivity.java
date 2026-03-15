package com.daspos.feature.printer;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.shared.util.ViewUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class BluetoothScanActivity extends BaseActivity {
    private static final int REQ_BT_CONNECT = 801;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_scan);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.bluetooth_scan));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT_CONNECT);
        } else loadDevices();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_CONNECT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) loadDevices();
            else {
                ViewUtils.toast(this, getString(R.string.bluetooth_permission_required));
                loadDevices();
            }
        }
    }

    private void loadDevices() {
        RecyclerView rv = findViewById(R.id.rvBluetoothDevices);
        rv.setLayoutManager(new LinearLayoutManager(this));
        BluetoothDeviceAdapter adapter = new BluetoothDeviceAdapter(new BluetoothDeviceAdapter.Listener() {
            @Override public void onClick(BluetoothDeviceAdapter.DeviceItem device) {
                Intent data = new Intent();
                data.putExtra("printer_name", device.getDisplayName());
                data.putExtra("printer_address", device.getAddress());
                setResult(RESULT_OK, data);
                finish();
            }
        });
        rv.setAdapter(adapter);
        adapter.submit(getDevices());
    }

    private List<BluetoothDeviceAdapter.DeviceItem> getDevices() {
        List<BluetoothDeviceAdapter.DeviceItem> list = new ArrayList<BluetoothDeviceAdapter.DeviceItem>();
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt != null) {
                Set<BluetoothDevice> bonded = bt.getBondedDevices();
                if (bonded != null) for (BluetoothDevice d : bonded) {
                    String name = d.getName() == null ? "Unknown Device" : d.getName();
                    list.add(new BluetoothDeviceAdapter.DeviceItem(name, d.getAddress()));
                }
            }
        } catch (Exception ignored) { }
        if (list.isEmpty()) {
            list.addAll(Arrays.asList(
                new BluetoothDeviceAdapter.DeviceItem("Printer Kasir A", "00:11:22:33:44:55"),
                new BluetoothDeviceAdapter.DeviceItem("Printer Gudang", "00:11:22:33:44:66"),
                new BluetoothDeviceAdapter.DeviceItem("BT-58MM-01", "00:11:22:33:44:77")
            ));
        }
        return list;
    }
}
