package com.daspos.feature.product;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.daspos.R;
import com.daspos.model.Product;
import com.daspos.repository.ProductRepository;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.DownloadsUriHelper;
import com.daspos.shared.util.NotificationDialogHelper;
import com.daspos.shared.util.OutputStreamCompat;
import com.daspos.shared.util.ViewUtils;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ImportProductFragment extends Fragment {
    private static final int REQ_IMPORT = 502;
    private static final int REQ_EXPORT_LOG = 503;

    private final List<Product> pendingImport = new ArrayList<>();
    private boolean templateAsXlsx = false;
    private String lastImportInfo = "";
    private ProductImportHelper.ParsedImport parsedImport;
    private boolean presetLoaded = false;
    private String currentInvalidLog = "";
    private AlertDialog progressDialog;
    private final Set<String> existingProductNames = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_import_product, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        root.findViewById(R.id.btnClose).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeSelf(); }
        });
        root.findViewById(R.id.btnDownloadTemplate).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { chooseTemplateFormat(); }
        });
        root.findViewById(R.id.btnChooseFile).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openImportFile(); }
        });
    }

    private void closeSelf() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) getParentFragmentManager().popBackStack();
        else requireActivity().finish();
    }

    private void chooseTemplateFormat() {
        NotificationDialogHelper.showWarningConfirmation(
                requireActivity(),
                "Import Produk",
                "Pilih tindakan yang ingin dilakukan",
                "Buat Template",
                new Runnable() {
                    @Override public void run() {
                        createTemplateFile();
                    }
                }
        );
    }

    private void createTemplateFile() {
        showProgressDialog("Sedang membuat template...");
        boolean ok;
        String successMessage;

        if (templateAsXlsx) {
            Uri xlsxUri = DownloadsUriHelper.createDownloadUri(requireContext(),
                    "daspos_template_produk.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            ok = xlsxUri != null && ProductImportHelper.writeTemplateXlsx(requireContext(), xlsxUri);

            if (ok) {
                successMessage = getString(R.string.template_download_success) + "\nTersimpan di Download/DasPos";
            } else {
                Uri csvUri = DownloadsUriHelper.createDownloadUri(requireContext(),
                        "daspos_template_produk.csv",
                        "text/csv");
                ok = csvUri != null && ProductImportHelper.writeTemplateCsv(requireContext(), csvUri);
                successMessage = getString(R.string.template_download_success)
                        + "\nTemplate disimpan sebagai CSV (kompatibel Excel)"
                        + "\nTersimpan di Download/DasPos";
            }
        } else {
            Uri csvUri = DownloadsUriHelper.createDownloadUri(requireContext(),
                    "daspos_template_produk.csv",
                    "text/csv");
            ok = csvUri != null && ProductImportHelper.writeTemplateCsv(requireContext(), csvUri);
            successMessage = getString(R.string.template_download_success) + "\nTersimpan di Download/DasPos";
        }

        hideProgressDialog();
        showDownloadResultNotification(ok, ok ? successMessage : getString(R.string.export_failed));
    }

    private void openImportFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] types = new String[]{
                "text/csv",
                "text/comma-separated-values",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, types);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();

        if (requestCode == REQ_IMPORT) {
            try {
                requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            try {
                String type = requireContext().getContentResolver().getType(uri);
                String path = String.valueOf(uri);

                if ((type != null && type.contains("spreadsheet")) || path.endsWith(".xlsx")) {
                    parsedImport = ProductImportHelper.parseSpreadsheet(requireContext(), uri);
                    ViewUtils.toast(requireActivity(), getString(R.string.xlsx_import_detected));
                    lastImportInfo = "Mode: XLSX • Header: "
                            + (parsedImport.isHeaderDetected() ? "otomatis" : "manual/default");
                } else {
                    parsedImport = ProductImportHelper.parseCsv(requireContext(), uri);
                    ViewUtils.toast(requireActivity(), getString(R.string.csv_import_detected));
                    ViewUtils.toast(requireActivity(), getString(R.string.csv_separator_detected));
                    String sep = parsedImport.getSeparator();
                    String label = ",".equals(sep) ? "koma" : (";".equals(sep) ? "titik koma" : "tab");
                    lastImportInfo = "Mode: CSV • Separator: " + label + " • Header: "
                            + (parsedImport.isHeaderDetected() ? "otomatis" : "manual/default");
                }

                int[] mapping = loadInitialMapping();
                pendingImport.clear();
                pendingImport.addAll(parsedImport.buildProducts(mapping));

                ViewUtils.toast(requireActivity(), getString(
                        parsedImport.isHeaderDetected()
                                ? R.string.import_header_detected
                                : R.string.import_header_unknown));

                if (parsedImport.getRawRowCount() == 0) {
                    ViewUtils.toast(requireActivity(), getString(R.string.no_import_rows));
                    return;
                }

                showPreviewDialog(mapping);

            } catch (Exception e) {
                ViewUtils.toast(requireActivity(), getString(R.string.import_failed));
            }

        } else if (requestCode == REQ_EXPORT_LOG) {
            showProgressDialog("Menyimpan log import...");
            boolean ok = writeImportLog(uri, currentInvalidLog);
            hideProgressDialog();
            showDownloadResultNotification(ok,
                    getString(ok ? R.string.import_log_saved : R.string.export_failed));
        }
    }

    private void showProgressDialog(String message) {
        hideProgressDialog();

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        int padding = Math.round(getResources().getDisplayMetrics().density * 20);
        container.setPadding(padding, padding, padding, padding);

        ProgressBar progressBar = new ProgressBar(requireContext());
        progressBar.setIndeterminate(true);

        TextView textView = new TextView(requireContext());
        textView.setText(message);
        textView.setPadding(Math.round(getResources().getDisplayMetrics().density * 16), 0, 0, 0);

        container.addView(progressBar);
        container.addView(textView);

        progressDialog = new MaterialAlertDialogBuilder(requireActivity(), com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
                .setView(container)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
    }

    private void showDownloadResultNotification(boolean success, String message) {
        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(success ? "Berhasil" : "Gagal")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private boolean writeImportLog(Uri uri, String content) {
        try {
            OutputStream out = OutputStreamCompat.openForWrite(requireContext(), uri);
            if (out == null) return false;
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int[] loadInitialMapping() {
        int[] preset = ImportMappingPresetStore.load(
                requireContext(),
                parsedImport.getSignature(),
                parsedImport.getColumns().length
        );
        presetLoaded = preset != null;
        if (preset != null) {
            ViewUtils.toast(requireActivity(), getString(R.string.preset_mapping_loaded));
            return preset;
        }
        return parsedImport.getSuggestedMapping();
    }

    private void showPreviewDialog(int[] initialMapping) {
        View dialogView = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_import_preview, null, false);

        RecyclerView rv = dialogView.findViewById(R.id.rvPreview);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        ProductImportPreviewAdapter adapter = new ProductImportPreviewAdapter();
        rv.setAdapter(adapter);

        TextView tvImportInfo = dialogView.findViewById(R.id.tvImportInfo);
        TextView tvRowStats = dialogView.findViewById(R.id.tvRowStats);
        TextView tvInvalidPreview = dialogView.findViewById(R.id.tvInvalidPreview);
        TextView dupInfo = dialogView.findViewById(R.id.tvDuplicateInfo);

        View btnResetPreset = dialogView.findViewById(R.id.btnResetPreset);
        View btnExportImportLog = dialogView.findViewById(R.id.btnExportImportLog);
        View btnCopyInvalidRows = dialogView.findViewById(R.id.btnCopyInvalidRows);
        View btnSaveNamedPreset = dialogView.findViewById(R.id.btnSaveNamedPreset);
        View btnLoadNamedPreset = dialogView.findViewById(R.id.btnLoadNamedPreset);

        EditText etPresetName = dialogView.findViewById(R.id.etPresetName);
        Spinner spPresetList = dialogView.findViewById(R.id.spPresetList);

        tvImportInfo.setText(
                presetLoaded
                        ? (lastImportInfo + " • " + getString(R.string.preset_mapping_in_use))
                        : lastImportInfo
        );

        Spinner spName = dialogView.findViewById(R.id.spMapName);
        Spinner spPrice = dialogView.findViewById(R.id.spMapPrice);
        Spinner spStock = dialogView.findViewById(R.id.spMapStock);

        String[] columns = parsedImport.getColumns();
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                columns
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spName.setAdapter(spinnerAdapter);
        spPrice.setAdapter(spinnerAdapter);
        spStock.setAdapter(spinnerAdapter);

        // Optional fields (barcode, unit, cost, tier prices/factors) each get a
        // label + spinner; position 0 means "not imported" (-1), position n>0
        // maps to column n-1.
        LinearLayout optionalContainer = dialogView.findViewById(R.id.llOptionalMappings);
        int[] optionalLabels = {
                R.string.map_barcode_column,
                R.string.map_unit_column,
                R.string.map_cost_column,
                R.string.map_price_renteng_column,
                R.string.map_price_pak_column,
                R.string.map_price_karton_column,
                R.string.map_factor_renteng_column,
                R.string.map_factor_pak_column,
                R.string.map_factor_karton_column
        };
        List<String> optionalColumnLabels = new ArrayList<>();
        optionalColumnLabels.add(getString(R.string.mapping_empty_column));
        for (String column : columns) optionalColumnLabels.add(column);
        ArrayAdapter<String> optionalAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                optionalColumnLabels
        );
        optionalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Spinner[] optionalSpinners = new Spinner[optionalLabels.length];
        for (int i = 0; i < optionalLabels.length; i++) {
            TextView label = new TextView(requireContext());
            label.setText(optionalLabels[i]);
            label.setTextColor(getResources().getColor(R.color.text_secondary));
            label.setTextSize(14);
            int padV = Math.round(getResources().getDisplayMetrics().density * 8);
            label.setPadding(0, padV, 0, padV / 2);
            optionalContainer.addView(label);

            Spinner spinner = new Spinner(requireContext());
            spinner.setAdapter(optionalAdapter);
            spinner.setBackgroundResource(R.drawable.bg_input);
            optionalContainer.addView(spinner);
            optionalSpinners[i] = spinner;
        }

        ArrayAdapter<String> presetAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                ImportMappingPresetStore.getNamedPresets(requireContext())
        );
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPresetList.setAdapter(presetAdapter);

        applyMappingSelection(spName, spPrice, spStock, optionalSpinners, initialMapping, columns.length);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireActivity(), R.style.DasPosAlertDialog).setView(dialogView).create();
        View btnCancel = dialogView.findViewById(R.id.btnPreviewCancel);
        View btnImport = dialogView.findViewById(R.id.btnPreviewImport);

        btnResetPreset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ImportMappingPresetStore.clear(requireContext(), parsedImport.getSignature());
                presetLoaded = false;
                applyMappingSelection(spName, spPrice, spStock, optionalSpinners,
                        parsedImport.getSuggestedMapping(), columns.length);
                tvImportInfo.setText(lastImportInfo);
                ViewUtils.toast(requireActivity(), getString(R.string.preset_mapping_reset));
            }
        });

        btnExportImportLog.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE, "daspos_import_log.txt");
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(intent, REQ_EXPORT_LOG);
            }
        });

        btnCopyInvalidRows.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager clipboard =
                        (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(
                            ClipData.newPlainText("invalid_rows", currentInvalidLog)
                    );
                    ViewUtils.toast(requireActivity(), getString(R.string.invalid_rows_copied));
                }
            }
        });

        btnSaveNamedPreset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String presetName = String.valueOf(
                        etPresetName.getText() == null ? "" : etPresetName.getText()
                ).trim();

                if (presetName.isEmpty()) {
                    ViewUtils.toast(requireActivity(), getString(R.string.preset_name_required));
                    return;
                }

                int[] mapping = currentMapping(spName, spPrice, spStock, optionalSpinners);
                if (!isDistinct(mapping)) {
                    ViewUtils.toast(requireActivity(), getString(R.string.mapping_duplicate_columns));
                    return;
                }

                ImportMappingPresetStore.saveNamed(requireContext(), presetName, mapping);
                presetAdapter.clear();
                presetAdapter.addAll(ImportMappingPresetStore.getNamedPresets(requireContext()));
                presetAdapter.notifyDataSetChanged();
                ViewUtils.toast(requireActivity(), getString(R.string.named_preset_saved));
            }
        });

        btnLoadNamedPreset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Object selected = spPresetList.getSelectedItem();
                if (selected == null) {
                    ViewUtils.toast(requireActivity(), getString(R.string.named_preset_missing));
                    return;
                }

                int[] mapping = ImportMappingPresetStore.loadNamed(
                        requireContext(),
                        String.valueOf(selected),
                        columns.length
                );

                if (mapping == null) {
                    ViewUtils.toast(requireActivity(), getString(R.string.named_preset_missing));
                    return;
                }

                applyMappingSelection(spName, spPrice, spStock, optionalSpinners, mapping, columns.length);
                ViewUtils.toast(requireActivity(), getString(R.string.named_preset_loaded));
            }
        });

        Runnable refreshPreview = new Runnable() {
            @Override public void run() {
                int[] mapping = currentMapping(spName, spPrice, spStock, optionalSpinners);
                if (!isDistinct(mapping)) {
                    btnImport.setEnabled(false);
                    tvRowStats.setText(getString(R.string.mapping_duplicate_columns));
                    tvInvalidPreview.setText("");
                    dupInfo.setText(buildDuplicateInfo());
                    currentInvalidLog = "";
                    return;
                }

                btnImport.setEnabled(true);
                ProductImportHelper.Analysis analysis = parsedImport.analyze(mapping);
                pendingImport.clear();
                pendingImport.addAll(analysis.getValidProducts());
                adapter.submit(pendingImport);

                tvRowStats.setText(getString(
                        R.string.row_stats,
                        analysis.getValidProducts().size(),
                        analysis.getInvalidRows().size()
                ));
                tvInvalidPreview.setText(buildInvalidPreview(analysis.getInvalidRows()));
                currentInvalidLog = buildInvalidLog(analysis.getInvalidRows());
                dupInfo.setText(buildDuplicateInfo());
            }
        };

        spName.setOnItemSelectedListener(new SimpleItemSelectedListener(refreshPreview));
        spPrice.setOnItemSelectedListener(new SimpleItemSelectedListener(refreshPreview));
        spStock.setOnItemSelectedListener(new SimpleItemSelectedListener(refreshPreview));
        for (Spinner optionalSpinner : optionalSpinners) {
            optionalSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(refreshPreview));
        }

        refreshPreview.run();

        ProductRepository.getAllAsync(requireContext(), products -> {
            existingProductNames.clear();
            for (Product p : products) {
                existingProductNames.add(p.getName().trim().toLowerCase(Locale.ROOT));
            }
            if (dialog.isShowing()) refreshPreview.run();
        }, error -> { });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int[] mapping = currentMapping(spName, spPrice, spStock, optionalSpinners);
                if (!isDistinct(mapping)) {
                    ViewUtils.toast(requireActivity(), getString(R.string.mapping_duplicate_columns));
                    return;
                }

                ImportMappingPresetStore.save(
                        requireContext(),
                        parsedImport.getSignature(),
                        mapping
                );
                ViewUtils.toast(requireActivity(), getString(R.string.preset_mapping_saved));

                final List<Product> snapshot = new ArrayList<>(pendingImport);
                showProgressDialog(getString(R.string.loading));
                DbExecutor.runAsync(() -> importPendingProducts(snapshot),
                        inserted -> {
                            hideProgressDialog();
                            ViewUtils.toast(requireActivity(),
                                    getString(R.string.import_success) + " (" + inserted + " item)");
                            dialog.dismiss();
                            closeSelf();
                        },
                        error -> {
                            hideProgressDialog();
                            ViewUtils.toast(requireActivity(), getString(R.string.import_failed));
                        });
            }
        });

        dialog.show();
    }

    private String buildInvalidPreview(List<ProductImportHelper.InvalidRow> rows) {
        if (rows.isEmpty()) return getString(R.string.no_invalid_rows);

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(5, rows.size());
        for (int i = 0; i < limit; i++) {
            ProductImportHelper.InvalidRow row = rows.get(i);
            if (i > 0) sb.append("\n");
            sb.append(getString(R.string.invalid_row_format, row.getRowNumber(), row.getReason()));
        }
        if (rows.size() > limit) sb.append("\n...");
        return sb.toString();
    }

    private String buildInvalidLog(List<ProductImportHelper.InvalidRow> rows) {
        if (rows.isEmpty()) return getString(R.string.no_invalid_rows);

        StringBuilder sb = new StringBuilder();
        for (ProductImportHelper.InvalidRow row : rows) {
            sb.append("Baris ").append(row.getRowNumber()).append(": ").append(row.getReason());
            String[] raw = row.getRawRow();
            if (raw != null && raw.length > 0) {
                sb.append(" | Data: ");
                for (int i = 0; i < raw.length; i++) {
                    if (i > 0) sb.append(" ; ");
                    sb.append(raw[i]);
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void applyMappingSelection(
            Spinner spName,
            Spinner spPrice,
            Spinner spStock,
            Spinner[] optionalSpinners,
            int[] mapping,
            int columnCount
    ) {
        int nameIdx = mappingAt(mapping, ProductImportHelper.IDX_NAME);
        int priceIdx = mappingAt(mapping, ProductImportHelper.IDX_PRICE);
        int stockIdx = mappingAt(mapping, ProductImportHelper.IDX_STOCK);

        spName.setSelection(clampColumn(nameIdx, 0, columnCount));
        spPrice.setSelection(clampColumn(priceIdx, 1, columnCount));
        spStock.setSelection(clampColumn(stockIdx, 2, columnCount));

        for (int i = 0; i < optionalSpinners.length; i++) {
            int fieldValue = mappingAt(mapping, ProductImportHelper.IDX_BARCODE + i);
            // Optional spinner position = column + 1; position 0 = not imported.
            int selection = fieldValue < 0 ? 0 : fieldValue + 1;
            int maxSelection = optionalSpinners[i].getAdapter() == null
                    ? 0 : optionalSpinners[i].getAdapter().getCount() - 1;
            optionalSpinners[i].setSelection(Math.min(selection, maxSelection));
        }
    }

    private int[] currentMapping(Spinner spName, Spinner spPrice, Spinner spStock, Spinner[] optionalSpinners) {
        int[] mapping = new int[ProductImportHelper.FIELD_COUNT];
        java.util.Arrays.fill(mapping, -1);
        mapping[ProductImportHelper.IDX_NAME] = spName.getSelectedItemPosition();
        mapping[ProductImportHelper.IDX_PRICE] = spPrice.getSelectedItemPosition();
        mapping[ProductImportHelper.IDX_STOCK] = spStock.getSelectedItemPosition();
        for (int i = 0; i < optionalSpinners.length; i++) {
            int position = optionalSpinners[i].getSelectedItemPosition();
            mapping[ProductImportHelper.IDX_BARCODE + i] = position <= 0 ? -1 : position - 1;
        }
        return mapping;
    }

    private int mappingAt(int[] mapping, int index) {
        return mapping != null && index < mapping.length ? mapping[index] : -1;
    }

    private int clampColumn(int value, int fallback, int columnCount) {
        if (value < 0 || value >= columnCount) return Math.min(fallback, columnCount - 1);
        return value;
    }

    private boolean isDistinct(int[] mapping) {
        Set<Integer> used = new HashSet<>();
        for (int col : mapping) {
            if (col < 0) continue;
            if (!used.add(col)) return false;
        }
        return true;
    }

    private String buildDuplicateInfo() {
        int duplicates = countDuplicatesByName();
        return duplicates > 0
                ? ("Duplikat terdeteksi: " + duplicates + " baris akan dilewati")
                : getString(R.string.duplicate_info);
    }

    private int countDuplicatesByName() {
        int count = 0;

        for (Product row : pendingImport) {
            if (existingProductNames.contains(row.getName().trim().toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    private int importPendingProducts(List<Product> rows) {
        List<Product> existing = ProductRepository.getAll(requireContext());
        Set<String> existingNames = new HashSet<>();
        for (Product p : existing) {
            existingNames.add(p.getName().trim().toLowerCase(Locale.ROOT));
        }

        List<Product> toInsert = new ArrayList<>();
        for (Product row : rows) {
            String normalized = row.getName().trim().toLowerCase(Locale.ROOT);
            if (!existingNames.contains(normalized)) {
                toInsert.add(row);
                existingNames.add(normalized);
            }
        }
        return ProductRepository.addAll(requireContext(), toInsert);
    }

    private static class SimpleItemSelectedListener
            implements android.widget.AdapterView.OnItemSelectedListener {

        private final Runnable runnable;
        private boolean first = true;

        SimpleItemSelectedListener(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            if (first) {
                first = false;
                return;
            }
            runnable.run();
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }
}
