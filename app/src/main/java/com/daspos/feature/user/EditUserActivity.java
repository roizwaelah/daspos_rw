package com.daspos.feature.user;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.feature.auth.MenuAccessStore;
import com.daspos.model.User;
import com.daspos.shared.util.LoadingDialogHelper;
import com.daspos.shared.util.NotificationDialogHelper;
import com.daspos.shared.util.ViewUtils;
import com.daspos.ui.state.ConsumableEvent;
import com.daspos.ui.state.FormUiEffect;
import com.daspos.viewmodel.ViewModelFactoryHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EditUserActivity extends BaseActivity {
    private AlertDialog loadingDialog;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthSessionStore.isAdmin(this)) {
            ViewUtils.toast(this, getString(R.string.admin_only_feature));
            finish();
            return;
        }
        setContentView(R.layout.activity_edit_user);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.edit_user));

        final Spinner spinner = findViewById(R.id.spEditRole);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Collections.singletonList("Kasir"));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setEnabled(false);

        final TextView tvUsername = findViewById(R.id.tvEditUsername);
        final CheckBox cbTransaction = findViewById(R.id.cbEditMenuTransaction);
        final CheckBox cbProduct = findViewById(R.id.cbEditMenuProduct);
        final CheckBox cbBestSeller = findViewById(R.id.cbEditMenuBestSeller);
        final CheckBox cbReport = findViewById(R.id.cbEditMenuReport);
        final CheckBox cbBackup = findViewById(R.id.cbEditMenuBackup);
        final View formContent = findViewById(R.id.layoutEditUserContent);
        String username = getIntent().getStringExtra("username");
        final EditUserViewModel viewModel = ViewModelFactoryHelper.get(this, EditUserViewModel.class);

        setLoading(true, formContent);

        viewModel.getUserLiveData().observe(this, new Observer<User>() {
            @Override public void onChanged(User user) {
                setLoading(false, formContent);
                final User safeUser = user == null ? new User("kasir_01", "Kasir") : user;
                tvUsername.setText(safeUser.getUsername());
                boolean isAdminRole = "Admin".equalsIgnoreCase(safeUser.getRole());
                if ("Admin".equalsIgnoreCase(safeUser.getRole())) {
                    ArrayAdapter<String> adminAdapter = new ArrayAdapter<>(EditUserActivity.this, android.R.layout.simple_spinner_item, Collections.singletonList("Admin"));
                    adminAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(adminAdapter);
                } else {
                    ArrayAdapter<String> kasirAdapter = new ArrayAdapter<>(EditUserActivity.this, android.R.layout.simple_spinner_item, Collections.singletonList("Kasir"));
                    kasirAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(kasirAdapter);
                }
                applyMenuCheckboxEnabled(cbTransaction, !isAdminRole);
                applyMenuCheckboxEnabled(cbProduct, !isAdminRole);
                applyMenuCheckboxEnabled(cbBestSeller, !isAdminRole);
                applyMenuCheckboxEnabled(cbReport, !isAdminRole);
                applyMenuCheckboxEnabled(cbBackup, !isAdminRole);

                List<String> allowedMenus = MenuAccessStore.getForUser(EditUserActivity.this, safeUser.getUsername(), safeUser.getRole());
                cbTransaction.setChecked(allowedMenus.contains(MenuAccessStore.MENU_TRANSACTION));
                cbProduct.setChecked(allowedMenus.contains(MenuAccessStore.MENU_PRODUCT));
                cbBestSeller.setChecked(allowedMenus.contains(MenuAccessStore.MENU_BEST_SELLER));
                cbReport.setChecked(allowedMenus.contains(MenuAccessStore.MENU_REPORT));
                cbBackup.setChecked(allowedMenus.contains(MenuAccessStore.MENU_BACKUP));

                findViewById(R.id.btnSaveUserEdit).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        List<String> newAllowedMenus = new ArrayList<>();
                        if (cbTransaction.isChecked()) newAllowedMenus.add(MenuAccessStore.MENU_TRANSACTION);
                        if (cbProduct.isChecked()) newAllowedMenus.add(MenuAccessStore.MENU_PRODUCT);
                        if (cbBestSeller.isChecked()) newAllowedMenus.add(MenuAccessStore.MENU_BEST_SELLER);
                        if (cbReport.isChecked()) newAllowedMenus.add(MenuAccessStore.MENU_REPORT);
                        if (cbBackup.isChecked()) newAllowedMenus.add(MenuAccessStore.MENU_BACKUP);
                        if (!isAdminRole && newAllowedMenus.isEmpty()) {
                            ViewUtils.toast(EditUserActivity.this, getString(R.string.menu_access_required));
                            return;
                        }
                        viewModel.updateRoleAndAccess(safeUser.getUsername(), spinner.getSelectedItem().toString(), newAllowedMenus);
                    }
                });
                findViewById(R.id.btnDeleteUser).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        NotificationDialogHelper.showWarningConfirmation(
                                EditUserActivity.this,
                                getString(R.string.delete),
                                getString(R.string.delete_user_message, safeUser.getUsername()),
                                getString(R.string.delete),
                                () -> viewModel.deleteUser(safeUser.getUsername())
                        );
                    }
                });
            }
        });

        viewModel.getUiEffect().observe(this, new Observer<ConsumableEvent<FormUiEffect>>() {
            @Override public void onChanged(ConsumableEvent<FormUiEffect> wrapper) {
                if (wrapper == null) return;
                FormUiEffect effect = wrapper.consume();
                if (effect == null) return;
                setLoading(false, formContent);
                ViewUtils.toast(EditUserActivity.this, effect.getMessage());
                if (effect.getType() == FormUiEffect.Type.CLOSE_SCREEN) finish();
            }
        });

        viewModel.loadUser(username != null ? username : "kasir_01");
    }

    private void setLoading(boolean isLoading, View formContent) {
        formContent.setAlpha(isLoading ? 0.6f : 1f);
        formContent.setEnabled(!isLoading);
        if (isLoading) {
            loadingDialog = LoadingDialogHelper.show(this, getString(R.string.loading));
        } else {
            LoadingDialogHelper.dismiss(loadingDialog);
            loadingDialog = null;
        }
    }

    private void applyMenuCheckboxEnabled(CheckBox checkBox, boolean enabled) {
        if (checkBox == null) return;
        checkBox.setEnabled(enabled);
        checkBox.setAlpha(enabled ? 1f : 0.6f);
    }
}
