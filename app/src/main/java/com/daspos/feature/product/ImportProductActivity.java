package com.daspos.feature.product;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.model.Product;
import com.daspos.repository.ProductRepository;
import com.daspos.shared.util.DownloadsUriHelper;
import com.daspos.shared.util.NotificationDialogHelper;
import com.daspos.shared.util.ViewUtils;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ImportProductActivity extends BaseActivity {
    private static final int REQ_IMPORT = 502;
    private static final int REQ_EXPORT_LOG = 503;

    private final List<Product> pendingImport = new ArrayList<>();
    private boolean templateAsXlsx = true;
    private String lastImportInfo = "";
    private ProductImportHelper.ParsedImport parsedImport;
    private boolean presetLoaded = false;
    private String currentInvalidLog = "";
    private AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_product);

        findViewById(R.id.btnClose).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });

        findViewById(R.id.btnDownloadTemplate).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { chooseTemplateFormat(); }
        });
        findViewById(R.id.btnChooseFile).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openImportFile(); }
        });
    }

    private void chooseTemplateFormat() {
        NotificationDialogHelper.showWarningConfirmation(
                this,
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
        String fileName = templateAsXlsx ? "daspos_template_produk.xlsx" : "daspos_template_produk.csv";
        String mimeType = templateAsXlsx
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "text/csv";
        Uri uri = DownloadsUriHelper.createDownloadUri(this, fileName, mimeType);
        if (uri == null) {
            showDownloadResultNotification(false, getString(R.string.export_failed));
            return;
        }

        showProgressDialog("Sedang membuat template...");
        boolean ok = templateAsXlsx
                ? ProductImportHelper.writeTemplateXlsx(this, uri)
                : ProductImportHelper.writeTemplateCsv(this, uri);
        hideProgressDialog();
        showDownloadResultNotification(ok,
                getString(ok ? R.string.template_download_success : R.string.export_failed)
                        + (ok ? "\nTersimpan di Download/DasPos" : ""));
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();

        if (requestCode == REQ_IMPORT) {
            try {
                final int flags = data.getFlags() &
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            try {
                String type = getContentResolver().getType(uri);
                String path = String.valueOf(uri);

                if ((type != null && type.contains("spreadsheet")) || path.endsWith(".xlsx")) {
                    parsedImport = ProductImportHelper.parseSpreadsheet(this, uri);
                    ViewUtils.toast(this, getString(R.string.xlsx_import_detected));
                    lastImportInfo = "Mode: XLSX • Header: "
                            + (parsedImport.isHeaderDetected() ? "otomatis" : "manual/default");
                } else {
                    parsedImport = ProductImportHelper.parseCsv(this, uri);
                    ViewUtils.toast(this, getString(R.string.csv_import_detected));
                    ViewUtils.toast(this, getString(R.string.csv_separator_detected));
                    String sep = parsedImport.getSeparator();
                    String label = ",".equals(sep) ? "koma" : (";".equals(sep) ? "titik koma" : "tab");
                    lastImportInfo = "Mode: CSV • Separator: " + label + " • Header: "
                            + (parsedImport.isHeaderDetected() ? "otomatis" : "manual/default");
                }

                int[] mapping = loadInitialMapping();
                pendingImport.clear();
                pendingImport.addAll(parsedImport.buildProducts(mapping));

                ViewUtils.toast(this, getString(
                        parsedImport.isHeaderDetected()
                                ? R.string.import_header_detected
                                : R.string.import_header_unknown));

                if (parsedImport.getRawRowCount() == 0) {
                    ViewUtils.toast(this, getString(R.string.no_import_rows));
                    return;
                }

                showPreviewDialog(mapping);

            } catch (Exception e) {
                ViewUtils.toast(this, getString(R.string.import_failed));
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

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        int padding = Math.round(getResources().getDisplayMetrics().density * 20);
        container.setPadding(padding, padding, padding, padding);

        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);

        TextView textView = new TextView(this);
        textView.setText(message);
        textView.setPadding(Math.round(getResources().getDisplayMetrics().density * 16), 0, 0, 0);

        container.addView(progressBar);
        container.addView(textView);

        progressDialog = new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
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
        new MaterialAlertDialogBuilder(this)
                .setTitle(success ? "Berhasil" : "Gagal")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private boolean writeImportLog(Uri uri, String content) {
        try {
            OutputStream out = getContentResolver().openOutputStream(uri);
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
                this,
                parsedImport.getSignature(),
                parsedImport.getColumns().length
        );
        presetLoaded = preset != null;
        if (preset != null) {
            ViewUtils.toast(this, getString(R.string.preset_mapping_loaded));
            return preset;
        }
        return parsedImport.getSuggestedMapping();
    }

    private void showPreviewDialog(int[] initialMapping) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_import_preview, null, false);

        RecyclerView rv = dialogView.findViewById(R.id.rvPreview);
        rv.setLayoutManager(new LinearLayoutManager(this));

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

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                parsedImport.getColumns()
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spName.setAdapter(spinnerAdapter);
        spPrice.setAdapter(spinnerAdapter);
        spStock.setAdapter(spinnerAdapter);

        ArrayAdapter<String> presetAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                ImportMappingPresetStore.getNamedPresets(this)
        );
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPresetList.setAdapter(presetAdapter);

        spName.setSelection(initialMapping[0]);
        spPrice.setSelection(initialMapping[1]);
        spStock.setSelection(initialMapping[2]);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.DasPosAlertDialog).setView(dialogView).create();
        View btnCancel = dialogView.findViewById(R.id.btnPreviewCancel);
        View btnImport = dialogView.findViewById(R.id.btnPreviewImport);

        btnResetPreset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ImportMappingPresetStore.clear(ImportProductActivity.this, parsedImport.getSignature());
                presetLoaded = false;
                spName.setSelection(parsedImport.getSuggestedMapping()[0]);
                spPrice.setSelection(parsedImport.getSuggestedMapping()[1]);
                spStock.setSelection(parsedImport.getSuggestedMapping()[2]);
                tvImportInfo.setText(lastImportInfo);
                ViewUtils.toast(ImportProductActivity.this, getString(R.string.preset_mapping_reset));
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
                        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(
                            ClipData.newPlainText("invalid_rows", currentInvalidLog)
                    );
                    ViewUtils.toast(ImportProductActivity.this, getString(R.string.invalid_rows_copied));
                }
            }
        });

        btnSaveNamedPreset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String presetName = String.valueOf(
                        etPresetName.getText() == null ? "" : etPresetName.getText()
                ).trim();

                if (presetName.isEmpty()) {
                    ViewUtils.toast(ImportProductActivity.this, getString(R.string.preset_name_required));
                    return;
                }

                int[] mapping = currentMapping(spName, spPrice, spStock);
                if (!isDistinct(mapping)) {
                    ViewUtils.toast(ImportProductActivity.this, getString(R.string.mapping_duplicate_columns));
                    return;
                }

                ImportMappingPresetStore.saveNamed(ImportProductActivity.this, presetName, mapping);
                presetAdapter.clear();
                presetAdapter.addAll(ImportMappingPresetStore.getNamedPresets(ImportProductActivity.this));
                presetAdapter.notifyDataSetChanged();
                ViewUtils.toast(ImportProductActivity.this, getString(R.string.named_preset_saved));
            }
        });

        btnLoadNamedPreset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Object selected = spPresetList.getSelectedItem();
                if (selected == null) {
                    ViewUtils.toast(ImportProductActivity.this, getString(R.string.named_preset_missing));
                    return;
                }

                int[] mapping = ImportMappingPresetStore.loadNamed(
                        ImportProductActivity.this,
                        String.valueOf(selected),
                        parsedImport.getColumns().length
                );

                if (mapping == null) {
                    ViewUtils.toast(ImportProductActivity.this, getString(R.string.named_preset_missing));
                    return;
                }

                spName.setSelection(mapping[0]);
                spPrice.setSelection(mapping[1]);
                spStock.setSelection(mapping[2]);
                ViewUtils.toast(ImportProductActivity.this, getString(R.string.named_preset_loaded));
            }
        });

        Runnable refreshPreview = new Runnable() {
            @Override public void run() {
                int[] mapping = currentMapping(spName, spPrice, spStock);
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

        refreshPreview.run();

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int[] mapping = currentMapping(spName, spPrice, spStock);
                if (!isDistinct(mapping)) {
                    ViewUtils.toast(ImportProductActivity.this, getString(R.string.mapping_duplicate_columns));
                    return;
                }

                ImportMappingPresetStore.save(
                        ImportProductActivity.this,
                        parsedImport.getSignature(),
                        mapping
                );
                ViewUtils.toast(ImportProductActivity.this, getString(R.string.preset_mapping_saved));

                int inserted = importPendingProducts();
                ViewUtils.toast(ImportProductActivity.this,
                        getString(R.string.import_success) + " (" + inserted + " item)");

                dialog.dismiss();
                finish();
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

    private int[] currentMapping(Spinner spName, Spinner spPrice, Spinner spStock) {
        return new int[] {
                spName.getSelectedItemPosition(),
                spPrice.getSelectedItemPosition(),
                spStock.getSelectedItemPosition()
        };
    }

    private boolean isDistinct(int[] mapping) {
        return mapping[0] != mapping[1]
                && mapping[0] != mapping[2]
                && mapping[1] != mapping[2];
    }

    private String buildDuplicateInfo() {
        int duplicates = countDuplicatesByName();
        return duplicates > 0
                ? ("Duplikat terdeteksi: " + duplicates + " baris akan dilewati")
                : getString(R.string.duplicate_info);
    }

    private int countDuplicatesByName() {
        List<Product> existing = ProductRepository.getAll(this);
        int count = 0;

        for (Product row : pendingImport) {
            for (Product p : existing) {
                if (p.getName().trim().equalsIgnoreCase(row.getName().trim())) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private int importPendingProducts() {
        List<Product> existing = ProductRepository.getAll(this);
        int inserted = 0;

        for (Product row : pendingImport) {
            boolean duplicate = false;
            for (Product p : existing) {
                if (p.getName().trim().toLowerCase(Locale.ROOT)
                        .equals(row.getName().trim().toLowerCase(Locale.ROOT))) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                ProductRepository.add(this, row.getName(), row.getPrice(), row.getStock());
                inserted++;
            }
        }
        return inserted;
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
