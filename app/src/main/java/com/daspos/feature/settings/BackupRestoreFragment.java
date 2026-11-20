package com.daspos.feature.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.daspos.R;
import com.daspos.feature.auth.MenuAccessGuard;
import com.daspos.feature.auth.MenuAccessStore;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.NotificationDialogHelper;
import com.daspos.shared.util.ViewUtils;

import java.util.concurrent.Callable;

public class BackupRestoreFragment extends Fragment {
    private static final int REQ_BACKUP = 901;
    private static final int REQ_RESTORE = 902;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MenuAccessGuard.ensureFragmentAccess(this, MenuAccessStore.MENU_BACKUP);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_backup_restore, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        Toolbar toolbar = root.findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.backup_restore));

        Spinner spinner = root.findViewById(R.id.spBackupInterval);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(), R.array.backup_interval_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        root.findViewById(R.id.btnBackupNow).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { createBackupFile(); }
        });
        root.findViewById(R.id.btnChooseRestoreFile).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { chooseRestoreFile(); }
        });
        root.findViewById(R.id.btnResetAllData).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                NotificationDialogHelper.showWarningConfirmation(
                        BackupRestoreFragment.this.requireActivity(),
                        R.string.reset_data,
                        R.string.reset_warning,
                        android.R.string.ok,
                        new Runnable() {
                            @Override public void run() { resetAllData(); }
                        }
                );
            }
        });
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

    private void resetAllData() {
        final Context appContext = requireContext().getApplicationContext();
        DbExecutor.runAsync(
                new Callable<Boolean>() {
                    @Override public Boolean call() {
                        BackupRestoreHelper.resetAll(appContext);
                        return Boolean.TRUE;
                    }
                },
                new DbExecutor.SuccessCallback<Boolean>() {
                    @Override public void onSuccess(Boolean result) {
                        if (!isAdded()) return;
                        ViewUtils.toast(requireActivity(), getString(R.string.reset_success));
                    }
                },
                new DbExecutor.ErrorCallback() {
                    @Override public void onError(Throwable throwable) {
                        if (!isAdded()) return;
                        ViewUtils.toast(requireActivity(), getString(R.string.export_failed));
                    }
                }
        );
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == REQ_BACKUP) {
            runBackup(uri);
        } else if (requestCode == REQ_RESTORE) {
            NotificationDialogHelper.showWarningConfirmation(
                    requireActivity(),
                    R.string.restore,
                    R.string.restore_confirm,
                    android.R.string.ok,
                    new Runnable() {
                        @Override public void run() { runRestore(uri); }
                    }
            );
        }
    }

    private void runBackup(final Uri uri) {
        final Context appContext = requireContext().getApplicationContext();
        // Heavy export: serialize on the export pool, then marshal the toast back to UI.
        DbExecutor.runExportAsync(new Runnable() {
            @Override public void run() {
                final boolean ok = BackupRestoreHelper.backup(appContext, uri);
                final Activity activity = getActivity();
                if (activity == null) return;
                activity.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!isAdded()) return;
                        ViewUtils.toast(requireActivity(), getString(ok ? R.string.backup_success : R.string.export_failed));
                    }
                });
            }
        });
    }

    private void runRestore(final Uri uri) {
        final Context appContext = requireContext().getApplicationContext();
        DbExecutor.runAsync(
                new Callable<BackupRestoreHelper.RestoreStatus>() {
                    @Override public BackupRestoreHelper.RestoreStatus call() {
                        return BackupRestoreHelper.restore(appContext, uri);
                    }
                },
                new DbExecutor.SuccessCallback<BackupRestoreHelper.RestoreStatus>() {
                    @Override public void onSuccess(BackupRestoreHelper.RestoreStatus status) {
                        if (!isAdded()) return;
                        if (status == BackupRestoreHelper.RestoreStatus.SUCCESS) ViewUtils.toast(requireActivity(), getString(R.string.restore_success));
                        else if (status == BackupRestoreHelper.RestoreStatus.INCOMPATIBLE_VERSION) ViewUtils.toast(requireActivity(), getString(R.string.backup_incompatible));
                        else ViewUtils.toast(requireActivity(), getString(R.string.restore_invalid));
                    }
                },
                new DbExecutor.ErrorCallback() {
                    @Override public void onError(Throwable throwable) {
                        if (!isAdded()) return;
                        ViewUtils.toast(requireActivity(), getString(R.string.restore_invalid));
                    }
                }
        );
    }
}
