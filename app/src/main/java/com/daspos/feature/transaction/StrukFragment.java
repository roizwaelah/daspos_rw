package com.daspos.feature.transaction;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.daspos.R;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.feature.printer.EscPosBluetoothPrinterHelper;
import com.daspos.feature.printer.EscPosNetworkPrinterHelper;
import com.daspos.feature.printer.PrinterConfigStore;
import com.daspos.feature.settings.ReceiptConfigStore;
import com.daspos.feature.settings.StoreConfigStore;
import com.daspos.model.CartItem;
import com.daspos.model.SalesUnit;
import com.daspos.model.TransactionRecord;
import com.daspos.repository.TransactionRepository;
import com.daspos.shared.util.CurrencyUtils;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.ImageDecodeHelper;
import com.daspos.shared.util.ViewUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class StrukFragment extends Fragment {
    public static final String ARG_TRANSACTION_ID = "arg_transaction_id";
    public static final String ARG_AUTO_PRINT = "arg_auto_print";
    private static final int REQ_BT_PRINT = 811;
    private static final int LOGO_MAX_DIMENSION = 512;
    private TransactionRecord currentTransaction;
    private boolean autoPrintPending;

    public static StrukFragment newInstance(String transactionId, boolean autoPrint) {
        StrukFragment fragment = new StrukFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TRANSACTION_ID, transactionId);
        args.putBoolean(ARG_AUTO_PRINT, autoPrint);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_struk, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        Bundle args = getArguments();
        String transactionId = args == null ? null : args.getString(ARG_TRANSACTION_ID);
        autoPrintPending = args != null && args.getBoolean(ARG_AUTO_PRINT, false);
        loadTransaction(transactionId);

        root.findViewById(R.id.btnPrintReceipt).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { printReceipt(); }
        });
        root.findViewById(R.id.btnDeleteReceipt).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmDeleteReceipt(); }
        });
        root.findViewById(R.id.btnFinish).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeSelf(); }
        });

        root.findViewById(R.id.btnDeleteReceipt).setVisibility(canDeleteTransaction() ? View.VISIBLE : View.GONE);
    }

    private void closeSelf() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            requireActivity().finish();
        }
    }

    private void loadTransaction(String transactionId) {
        TransactionRepository.getByIdAsync(requireContext(), transactionId, record -> {
            if (record != null) {
                currentTransaction = record;
                renderReceipt();
                return;
            }
            TransactionRepository.getLastAsync(requireContext(), last -> {
                currentTransaction = last;
                renderReceipt();
            }, error -> renderReceipt());
        }, error -> TransactionRepository.getLastAsync(requireContext(), last -> {
            currentTransaction = last;
            renderReceipt();
        }, error2 -> renderReceipt()));
    }

    private void printReceipt() {
        if (currentTransaction == null) {
            ViewUtils.toast(requireActivity(), "Data struk tidak ditemukan");
            return;
        }
        String type = PrinterConfigStore.getType(requireContext());
        if ("bluetooth".equals(type)) printBluetooth();
        else if ("network".equals(type)) printNetwork();
        else {
            ReceiptPrintHelper.print(requireContext(), currentTransaction);
            ViewUtils.toast(requireActivity(), getString(R.string.print_started));
        }
    }

    private void printBluetooth() {
        if (PrinterConfigStore.getBluetoothAddress(requireContext()).isEmpty() && PrinterConfigStore.getBluetoothName(requireContext()).isEmpty()) {
            ViewUtils.toast(requireActivity(), getString(R.string.printer_not_configured));
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT_PRINT);
            return;
        }

        final TransactionRecord trx = currentTransaction;
        DbExecutor.runAsync(
                () -> EscPosBluetoothPrinterHelper.printReceipt(requireContext(), trx),
                result -> {
                    if (result == EscPosBluetoothPrinterHelper.PrintResult.SUCCESS) ViewUtils.toast(requireActivity(), getString(R.string.print_started));
                    else if (result == EscPosBluetoothPrinterHelper.PrintResult.PERMISSION_REQUIRED) ViewUtils.toast(requireActivity(), getString(R.string.bluetooth_permission_required));
                    else if (result == EscPosBluetoothPrinterHelper.PrintResult.PRINTER_NOT_FOUND) ViewUtils.toast(requireActivity(), getString(R.string.printer_not_configured));
                    else if (result == EscPosBluetoothPrinterHelper.PrintResult.CONNECTION_FAILED) ViewUtils.toast(requireActivity(), getString(R.string.printer_connection_failed));
                    else ViewUtils.toast(requireActivity(), getString(R.string.printer_write_failed));
                },
                error -> ViewUtils.toast(requireActivity(), getString(R.string.printer_write_failed))
        );
    }

    private void printNetwork() {
        String ip = PrinterConfigStore.getIp(requireContext());
        int port;
        try { port = Integer.parseInt(PrinterConfigStore.getPort(requireContext())); }
        catch (Exception e) {
            ViewUtils.toast(requireActivity(), getString(R.string.network_printer_invalid));
            return;
        }

        final TransactionRecord trx = currentTransaction;
        final String ipAddress = ip;
        DbExecutor.runAsync(
                () -> EscPosNetworkPrinterHelper.printReceipt(requireContext(), ipAddress, port, trx),
                result -> {
                    if (result == EscPosNetworkPrinterHelper.PrintResult.SUCCESS) ViewUtils.toast(requireActivity(), getString(R.string.network_print_started));
                    else if (result == EscPosNetworkPrinterHelper.PrintResult.INVALID_CONFIG) ViewUtils.toast(requireActivity(), getString(R.string.network_printer_invalid));
                    else if (result == EscPosNetworkPrinterHelper.PrintResult.CONNECTION_FAILED) ViewUtils.toast(requireActivity(), getString(R.string.network_printer_connect_failed));
                    else ViewUtils.toast(requireActivity(), getString(R.string.printer_write_failed));
                },
                error -> ViewUtils.toast(requireActivity(), getString(R.string.printer_write_failed))
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_PRINT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) printBluetooth();
            else ViewUtils.toast(requireActivity(), getString(R.string.bluetooth_permission_required));
        }
    }

    private void confirmDeleteReceipt() {
        if (!canDeleteTransaction()) {
            ViewUtils.toast(requireActivity(), getString(R.string.menu_access_denied));
            return;
        }
        if (currentTransaction == null) {
            ViewUtils.toast(requireActivity(), getString(R.string.delete_receipt_failed));
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.delete_receipt))
                .setMessage(getString(R.string.delete_receipt_confirmation))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> deleteReceipt())
                .show();
    }

    private void deleteReceipt() {
        if (!canDeleteTransaction()) {
            ViewUtils.toast(requireActivity(), getString(R.string.menu_access_denied));
            return;
        }
        if (currentTransaction == null) {
            ViewUtils.toast(requireActivity(), getString(R.string.delete_receipt_failed));
            return;
        }

        String transactionId = currentTransaction.getId();
        TransactionRepository.deleteByIdAsync(requireContext(), transactionId, deleted -> {
            if (Boolean.TRUE.equals(deleted)) {
                ViewUtils.toast(requireActivity(), getString(R.string.delete_receipt_success));
                closeSelf();
            } else {
                ViewUtils.toast(requireActivity(), getString(R.string.delete_receipt_failed));
            }
        }, throwable -> ViewUtils.toast(requireActivity(), getString(R.string.delete_receipt_failed)));
    }

    private boolean canDeleteTransaction() {
        String role = AuthSessionStore.getRole(requireContext());
        return "admin".equalsIgnoreCase(role) || "owner".equalsIgnoreCase(role);
    }

    private void renderReceipt() {
        View root = getView();
        TransactionRecord transaction = currentTransaction;
        if (root == null || transaction == null) return;

        String receiptHeader = ReceiptConfigStore.getHeader(requireContext());
        String receiptFooter = ReceiptConfigStore.getFooter(requireContext());
        boolean showLogo = ReceiptConfigStore.shouldShowLogo(requireContext());

        TextView headerView = root.findViewById(R.id.tvReceiptHeader);
        ((TextView) root.findViewById(R.id.tvReceiptStoreName)).setText(StoreConfigStore.getStoreName(requireContext()));
        ((TextView) root.findViewById(R.id.tvReceiptDate)).setText(transaction.getDate() + " " + transaction.getTime());
        ((TextView) root.findViewById(R.id.tvReceiptTransactionNumber)).setText(getString(R.string.receipt_transaction_number, transaction.getId()));
        ((TextView) root.findViewById(R.id.tvReceiptAddress)).setText(StoreConfigStore.getAddress(requireContext()));
        ((TextView) root.findViewById(R.id.tvReceiptPhone)).setText(StoreConfigStore.getPhone(requireContext()));
        ((TextView) root.findViewById(R.id.tvReceiptEmail)).setText(StoreConfigStore.getEmail(requireContext()));
        ((TextView) root.findViewById(R.id.tvTotalSummary)).setText("Total: " + CurrencyUtils.formatRupiah(transaction.getTotal()));
        TextView discountView = root.findViewById(R.id.tvDiscountSummary);
        discountView.setText("Diskon: " + CurrencyUtils.formatRupiah(transaction.getDiscount()));
        discountView.setVisibility(transaction.getDiscount() > 0 ? View.VISIBLE : View.GONE);
        ((TextView) root.findViewById(R.id.tvPaySummary)).setText("Bayar: " + CurrencyUtils.formatRupiah(transaction.getPay()));
        ((TextView) root.findViewById(R.id.tvChangeSummary)).setText("Kembalian: " + CurrencyUtils.formatRupiah(transaction.getChange()));
        ((TextView) root.findViewById(R.id.tvReceiptFooter)).setText(receiptFooter);

        headerView.setText(receiptHeader);
        headerView.setVisibility(receiptHeader.isEmpty() ? View.GONE : View.VISIBLE);

        boolean hasIdentityLine = !StoreConfigStore.getAddress(requireContext()).isEmpty()
                || !StoreConfigStore.getPhone(requireContext()).isEmpty()
                || !StoreConfigStore.getEmail(requireContext()).isEmpty();
        root.findViewById(R.id.layoutReceiptIdentity).setVisibility(hasIdentityLine ? View.VISIBLE : View.GONE);

        ImageView logo = root.findViewById(R.id.imgReceiptStoreLogo);
        String logoUri = StoreConfigStore.getLogoUri(requireContext());
        if (showLogo && logoUri != null && !logoUri.trim().isEmpty() && loadLogoInto(logo, logoUri)) {
            logo.setVisibility(View.VISIBLE);
        } else {
            logo.setVisibility(View.GONE);
        }

        LinearLayout layoutItems = root.findViewById(R.id.layoutItems);
        layoutItems.removeAllViews();
        for (CartItem item : transaction.getItems()) {
            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_receipt_line, layoutItems, false);
            ((TextView) row.findViewById(R.id.tvReceiptItemName)).setText(item.getProduct().getName());
            ((TextView) row.findViewById(R.id.tvReceiptItemDetail)).setText(getString(
                    R.string.receipt_item_format,
                    item.getQty(),
                    displayUnit(item),
                    CurrencyUtils.formatRupiah(item.getUnitPrice())
            ));
            ((TextView) row.findViewById(R.id.tvReceiptItemSubtotal)).setText(CurrencyUtils.formatRupiah(item.getSubtotal()));
            layoutItems.addView(row);
        }

        if (autoPrintPending) {
            autoPrintPending = false;
            root.findViewById(R.id.btnPrintReceipt).post(new Runnable() {
                @Override public void run() { printReceipt(); }
            });
        }
    }

    private boolean loadLogoInto(ImageView logo, String logoUri) {
        Bitmap bitmap = ImageDecodeHelper.decodeSampled(requireContext(), logoUri, LOGO_MAX_DIMENSION);
        if (bitmap == null) return false;
        logo.setImageBitmap(bitmap);
        return true;
    }

    private static String displayUnit(CartItem item) {
        if (item == null || item.getProduct() == null) return "pcs";
        String unitCode = SalesUnit.normalize(item.getUnitCode());
        if (item.getProduct().isTierPricingEnabled() && !SalesUnit.ECER.equals(unitCode)) return SalesUnit.displayName(unitCode);
        String unitName = item.getProduct().getUnitName();
        return unitName == null || unitName.trim().isEmpty() ? "pcs" : unitName.trim();
    }
}
