package com.daspos.feature.printer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.daspos.R;
import com.daspos.core.app.MainActivity;
import com.daspos.feature.transaction.StrukFragment;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.ViewUtils;
import com.google.android.material.textfield.TextInputEditText;

public class PrinterSettingFragment extends Fragment {
    private static final int REQ_BT = 701;
    private String selectedBluetoothName = "";
    private String selectedBluetoothAddress = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_printer_setting, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        Toolbar toolbar = root.findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.printer));
        final RadioGroup group = root.findViewById(R.id.rgPrinterType);
        final View layoutNetwork = root.findViewById(R.id.layoutNetwork);
        final View btnChooseBluetooth = root.findViewById(R.id.btnChooseBluetooth);
        final TextInputEditText etIp = root.findViewById(R.id.etPrinterIp);
        final TextInputEditText etPort = root.findViewById(R.id.etPrinterPort);

        selectedBluetoothName = PrinterConfigStore.getBluetoothName(requireContext());
        selectedBluetoothAddress = PrinterConfigStore.getBluetoothAddress(requireContext());
        String type = PrinterConfigStore.getType(requireContext());
        etIp.setText(PrinterConfigStore.getIp(requireContext()));
        etPort.setText(PrinterConfigStore.getPort(requireContext()));

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

        root.findViewById(R.id.btnChooseBluetooth).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivityForResult(new Intent(requireContext(), BluetoothScanActivity.class), REQ_BT);
            }
        });
        root.findViewById(R.id.btnTestPrint).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!saveConfig()) return;
                if ("network".equals(currentType())) testNetworkPrinter();
                else {
                    ((MainActivity) PrinterSettingFragment.this.requireActivity()).navigateTo(StrukFragment.newInstance(null, true));
                }
            }
        });
        root.findViewById(R.id.btnPrinterSave).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!saveConfig()) return;
                ViewUtils.toast(PrinterSettingFragment.this.requireActivity(), getString("network".equals(currentType()) ? R.string.network_printer_saved : R.string.printer_saved));
            }
        });
        root.findViewById(R.id.btnPrinterCancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeSelf(); }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_BT && resultCode == Activity.RESULT_OK && data != null) {
            selectedBluetoothName = String.valueOf(data.getStringExtra("printer_name") == null ? "" : data.getStringExtra("printer_name"));
            selectedBluetoothAddress = String.valueOf(data.getStringExtra("printer_address") == null ? "" : data.getStringExtra("printer_address"));
            ViewUtils.toast(requireActivity(), getString(R.string.selected_printer) + ": " + selectedBluetoothName);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T findViewById(int id) {
        View v = getView();
        return v == null ? null : (T) v.findViewById(id);
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
                if (ip.isEmpty() || portNum <= 0 || portNum > 65535) { ViewUtils.toast(requireActivity(), getString(R.string.network_printer_invalid)); return false; }
            } catch (Exception e) { ViewUtils.toast(requireActivity(), getString(R.string.network_printer_invalid)); return false; }
        }
        PrinterConfigStore.save(requireContext(), type, selectedBluetoothName, selectedBluetoothAddress, ip, port);
        return true;
    }

    private void testNetworkPrinter() {
        String ip = PrinterConfigStore.getIp(requireContext());
        int port;
        try { port = Integer.parseInt(PrinterConfigStore.getPort(requireContext())); }
        catch (Exception e) { ViewUtils.toast(requireActivity(), getString(R.string.network_printer_invalid)); return; }
        final String ipAddress = ip;
        DbExecutor.runAsync(
                () -> EscPosNetworkPrinterHelper.testConnection(requireContext(), ipAddress, port),
                result -> {
                    if (result == EscPosNetworkPrinterHelper.PrintResult.SUCCESS) ViewUtils.toast(requireActivity(), getString(R.string.network_test_success));
                    else if (result == EscPosNetworkPrinterHelper.PrintResult.INVALID_CONFIG) ViewUtils.toast(requireActivity(), getString(R.string.network_printer_invalid));
                    else ViewUtils.toast(requireActivity(), getString(R.string.network_test_failed));
                },
                error -> ViewUtils.toast(requireActivity(), getString(R.string.network_test_failed))
        );
    }

    private void closeSelf() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) getParentFragmentManager().popBackStack();
        else requireActivity().finish();
    }
}
