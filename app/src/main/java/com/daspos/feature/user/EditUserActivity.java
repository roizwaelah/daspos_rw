package com.daspos.feature.user;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.model.User;
import com.daspos.shared.util.ViewUtils;
import com.daspos.ui.state.ConsumableEvent;
import com.daspos.ui.state.FormUiEffect;
import com.daspos.viewmodel.ViewModelFactoryHelper;

public class EditUserActivity extends BaseActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_user);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.edit_user));

        final Spinner spinner = findViewById(R.id.spEditRole);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.role_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        final TextView tvUsername = findViewById(R.id.tvEditUsername);
        String username = getIntent().getStringExtra("username");
        final EditUserViewModel viewModel = ViewModelFactoryHelper.get(this, EditUserViewModel.class);

        viewModel.getUserLiveData().observe(this, new Observer<User>() {
            @Override public void onChanged(User user) {
                final User safeUser = user == null ? new User("kasir_01", "Kasir") : user;
                tvUsername.setText(safeUser.getUsername());
                spinner.setSelection("Kasir".equalsIgnoreCase(safeUser.getRole()) ? 1 : 0);

                findViewById(R.id.btnSaveUserEdit).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        viewModel.updateRole(safeUser.getUsername(), spinner.getSelectedItem().toString());
                    }
                });
                findViewById(R.id.btnDeleteUser).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        new AlertDialog.Builder(EditUserActivity.this)
                                .setTitle(getString(R.string.delete))
                                .setMessage("Hapus user " + safeUser.getUsername() + "?")
                                .setPositiveButton("Ya", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dialog, int which) {
                                        viewModel.deleteUser(safeUser.getUsername());
                                    }
                                })
                                .setNegativeButton(getString(R.string.cancel), null)
                                .show();
                    }
                });
            }
        });

        viewModel.getUiEffect().observe(this, new Observer<ConsumableEvent<FormUiEffect>>() {
            @Override public void onChanged(ConsumableEvent<FormUiEffect> wrapper) {
                if (wrapper == null) return;
                FormUiEffect effect = wrapper.consume();
                if (effect == null) return;
                ViewUtils.toast(EditUserActivity.this, effect.getMessage());
                if (effect.getType() == FormUiEffect.Type.CLOSE_SCREEN) finish();
            }
        });

        viewModel.loadUser(username != null ? username : "kasir_01");
    }
}