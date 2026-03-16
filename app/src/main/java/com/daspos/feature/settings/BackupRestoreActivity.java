package com.daspos.feature.settings;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.shared.util.ViewUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackupRestoreActivity extends BaseActivity {
    private static final int REQ_BACKUP = 901;
    private static final int REQ_RESTORE = 902;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup_restore);
        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.backup_restore));

        Spinner spinner = findViewById(R.id.spBackupInterval);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.backup_interval_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        findViewById(R.id.btnBackupNow).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { createBackupFile(); }
        });
        findViewById(R.id.btnChooseRestoreFile).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { chooseRestoreFile(); }
        });
        findViewById(R.id.btnResetAllData).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(BackupRestoreActivity.this)
                        .setTitle(getString(R.string.reset_data))
                        .setMessage(getString(R.string.reset_warning))
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                ioExecutor.execute(new Runnable() {
                                    @Override public void run() {
                                        BackupRestoreHelper.resetAll(BackupRestoreActivity.this);
                                        runOnUiThread(new Runnable() {
                                            @Override public void run() {
                                                ViewUtils.toast(BackupRestoreActivity.this, getString(R.string.reset_success));
                                            }
                                        });
                                    }
                                });
                            }
                        })
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show();
            }
        });
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
    }

    private void createBackupFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_TITLE, "daspos_backup.json");
        startActivityForResult(intent, REQ_BACKUP);
    }

    private void chooseRestoreFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_RESTORE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == REQ_BACKUP) {
            ioExecutor.execute(new Runnable() {
                @Override public void run() {
                    final boolean ok = BackupRestoreHelper.backup(BackupRestoreActivity.this, uri);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            ViewUtils.toast(BackupRestoreActivity.this, getString(ok ? R.string.backup_success : R.string.export_failed));
                        }
                    });
                }
            });
        } else if (requestCode == REQ_RESTORE) {
            try {
                final int flags = data.getFlags() &
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) { }
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.restore))
                    .setMessage(getString(R.string.restore_confirm))
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            ioExecutor.execute(new Runnable() {
                                @Override public void run() {
                                    final BackupRestoreHelper.RestoreStatus status = BackupRestoreHelper.restore(BackupRestoreActivity.this, uri);
                                    runOnUiThread(new Runnable() {
                                        @Override public void run() {
                                            if (status == BackupRestoreHelper.RestoreStatus.SUCCESS) ViewUtils.toast(BackupRestoreActivity.this, getString(R.string.restore_success));
                                            else if (status == BackupRestoreHelper.RestoreStatus.INCOMPATIBLE_VERSION) ViewUtils.toast(BackupRestoreActivity.this, getString(R.string.backup_incompatible));
                                            else ViewUtils.toast(BackupRestoreActivity.this, getString(R.string.restore_invalid));
                                        }
                                    });
                                }
                            });
                        }
                    })
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
        }
    }
}
