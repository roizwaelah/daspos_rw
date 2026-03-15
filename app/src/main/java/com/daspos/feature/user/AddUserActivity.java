package com.daspos.feature.user;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.shared.util.ViewUtils;
import com.daspos.ui.state.ConsumableEvent;
import com.daspos.ui.state.FormUiEffect;
import com.daspos.ui.state.ValidationState;
import com.daspos.viewmodel.ViewModelFactoryHelper;

public class AddUserActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_user);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.add_user));

        final Spinner spinner = findViewById(R.id.spAddRole);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.role_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        final EditText etUsername = findViewById(R.id.etAddUsername);
        final EditText etPassword = findViewById(R.id.etAddPassword);

        final UserViewModel viewModel = ViewModelFactoryHelper.get(this, UserViewModel.class);
        viewModel.getValidationState().observe(this, new Observer<ValidationState>() {
            @Override public void onChanged(ValidationState state) {
                if (state == null || state.getCode().isEmpty()) return;
                if ("USERNAME_REQUIRED".equals(state.getCode())) etUsername.setError(getString(R.string.username_required));
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
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                if (!viewModel.validateNewUser(username, password)) return;
                viewModel.addUser(username, password, spinner.getSelectedItem().toString());
            }
        });
    }
}