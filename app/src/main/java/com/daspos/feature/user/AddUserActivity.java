package com.daspos.feature.user;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.feature.auth.MenuAccessStore;
import com.daspos.shared.util.PasswordFieldToggleHelper;
import com.daspos.shared.util.ViewUtils;
import com.daspos.ui.state.ConsumableEvent;
import com.daspos.ui.state.FormUiEffect;
import com.daspos.ui.state.ValidationState;
import com.daspos.viewmodel.ViewModelFactoryHelper;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class AddUserActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthSessionStore.isAdmin(this)) {
            ViewUtils.toast(this, getString(R.string.admin_only_feature));
            finish();
            return;
        }
        setContentView(R.layout.activity_add_user);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.add_user));

        final Spinner spinner = findViewById(R.id.spAddRole);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Collections.singletonList("Kasir"));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setEnabled(false);

        final EditText etUsername = findViewById(R.id.etAddUsername);
        final EditText etPassword = findViewById(R.id.etAddPassword);
        PasswordFieldToggleHelper.attach(etPassword);
        final CheckBox cbTransaction = findViewById(R.id.cbAddMenuTransaction);
        final CheckBox cbProduct = findViewById(R.id.cbAddMenuProduct);
        final CheckBox cbBestSeller = findViewById(R.id.cbAddMenuBestSeller);
        final CheckBox cbReport = findViewById(R.id.cbAddMenuReport);
        final CheckBox cbBackup = findViewById(R.id.cbAddMenuBackup);

        cbTransaction.setChecked(true);
        cbProduct.setChecked(true);
        cbBestSeller.setChecked(true);
        cbReport.setChecked(true);
        cbBackup.setChecked(false);

        final UserViewModel viewModel = ViewModelFactoryHelper.get(this, UserViewModel.class);
        viewModel.getValidationState().observe(this, new Observer<ValidationState>() {
            @Override public void onChanged(ValidationState state) {
                if (state == null || state.getCode().isEmpty()) return;
                if ("EMAIL_REQUIRED".equals(state.getCode())) etUsername.setError(getString(R.string.email_required));
                else if ("EMAIL_INVALID".equals(state.getCode())) etUsername.setError(getString(R.string.invalid_email));
                else if ("PASSWORD_REQUIRED".equals(state.getCode())) etPassword.setError(getString(R.string.password_required));
            }
        });
        viewModel.getUiEffect().observe(this, new Observer<ConsumableEvent<FormUiEffect>>() {
            @Override public void onChanged(ConsumableEvent<FormUiEffect> wrapper) {
                if (wrapper == null) return;
                FormUiEffect effect = wrapper.consume();
                if (effect == null) return;
                ViewUtils.toast(AddUserActivity.this, effect.getMessage());
                if (effect.getType() == FormUiEffect.Type.CLOSE_SCREEN) finish();
            }
        });

        findViewById(R.id.btnAddUserCancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnAddUserSave).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String email = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                if (!viewModel.validateNewUser(email, password)) return;
                List<String> allowedMenus = new ArrayList<>();
                if (cbTransaction.isChecked()) allowedMenus.add(MenuAccessStore.MENU_TRANSACTION);
                if (cbProduct.isChecked()) allowedMenus.add(MenuAccessStore.MENU_PRODUCT);
                if (cbBestSeller.isChecked()) allowedMenus.add(MenuAccessStore.MENU_BEST_SELLER);
                if (cbReport.isChecked()) allowedMenus.add(MenuAccessStore.MENU_REPORT);
                if (cbBackup.isChecked()) allowedMenus.add(MenuAccessStore.MENU_BACKUP);
                if (allowedMenus.isEmpty()) {
                    ViewUtils.toast(AddUserActivity.this, getString(R.string.menu_access_required));
                    return;
                }
                viewModel.addUser(email, password, "Kasir", allowedMenus);
            }
        });
    }
}
