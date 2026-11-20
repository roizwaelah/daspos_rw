package com.daspos.feature.user;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

import com.daspos.R;
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

public class EditUserFragment extends Fragment {
    private static final String ARG_USERNAME = "username";

    private AlertDialog loadingDialog;

    public static EditUserFragment newInstance(String username) {
        EditUserFragment fragment = new EditUserFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, username);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_edit_user, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        if (!AuthSessionStore.isAdmin(requireContext())) {
            ViewUtils.toast(requireActivity(), getString(R.string.admin_only_feature));
            closeSelf();
            return;
        }

        Toolbar toolbar = root.findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.edit_user));

        final Spinner spinner = root.findViewById(R.id.spEditRole);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, Collections.singletonList("Kasir"));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setEnabled(false);

        final TextView tvUsername = root.findViewById(R.id.tvEditUsername);
        final CheckBox cbTransaction = root.findViewById(R.id.cbEditMenuTransaction);
        final CheckBox cbProduct = root.findViewById(R.id.cbEditMenuProduct);
        final CheckBox cbBestSeller = root.findViewById(R.id.cbEditMenuBestSeller);
        final CheckBox cbReport = root.findViewById(R.id.cbEditMenuReport);
        final CheckBox cbBackup = root.findViewById(R.id.cbEditMenuBackup);
        final View formContent = root.findViewById(R.id.layoutEditUserContent);
        String username = getArguments() != null ? getArguments().getString(ARG_USERNAME) : null;
        final EditUserViewModel viewModel = ViewModelFactoryHelper.get(requireActivity(), EditUserViewModel.class);

        setLoading(true, formContent);

        viewModel.getUserLiveData().observe(getViewLifecycleOwner(), new Observer<User>() {
            @Override public void onChanged(User user) {
                setLoading(false, formContent);
                final User safeUser = user == null ? new User("kasir_01", "Kasir") : user;
                tvUsername.setText(safeUser.getUsername());
                boolean isAdminRole = "Admin".equalsIgnoreCase(safeUser.getRole());
                if ("Admin".equalsIgnoreCase(safeUser.getRole())) {
                    ArrayAdapter<String> adminAdapter = new ArrayAdapter<>(EditUserFragment.this.requireContext(), android.R.layout.simple_spinner_item, Collections.singletonList("Admin"));
                    adminAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(adminAdapter);
                } else {
                    ArrayAdapter<String> kasirAdapter = new ArrayAdapter<>(EditUserFragment.this.requireContext(), android.R.layout.simple_spinner_item, Collections.singletonList("Kasir"));
                    kasirAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(kasirAdapter);
                }
                applyMenuCheckboxEnabled(cbTransaction, !isAdminRole);
                applyMenuCheckboxEnabled(cbProduct, !isAdminRole);
                applyMenuCheckboxEnabled(cbBestSeller, !isAdminRole);
                applyMenuCheckboxEnabled(cbReport, !isAdminRole);
                applyMenuCheckboxEnabled(cbBackup, !isAdminRole);

                List<String> allowedMenus = MenuAccessStore.getForUser(EditUserFragment.this.requireContext(), safeUser.getUsername(), safeUser.getRole());
                cbTransaction.setChecked(allowedMenus.contains(MenuAccessStore.MENU_TRANSACTION));
                cbProduct.setChecked(allowedMenus.contains(MenuAccessStore.MENU_PRODUCT));
                cbBestSeller.setChecked(allowedMenus.contains(MenuAccessStore.MENU_BEST_SELLER));
                cbReport.setChecked(allowedMenus.contains(MenuAccessStore.MENU_REPORT));
                cbBackup.setChecked(allowedMenus.contains(MenuAccessStore.MENU_BACKUP));

                root.findViewById(R.id.btnSaveUserEdit).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        List<String> newAllowedMenus = new ArrayList<>();
                        if (cbTransaction.isChecked()) newAllowedMenus.add(MenuAccessStore.MENU_TRANSACTION);
                        if (cbProduct.isChecked()) newAllowedMenus.add(MenuAccessStore.MENU_PRODUCT);
                        if (cbBestSeller.isChecked()) newAllowedMenus.add(MenuAccessStore.MENU_BEST_SELLER);
                        if (cbReport.isChecked()) newAllowedMenus.add(MenuAccessStore.MENU_REPORT);
                        if (cbBackup.isChecked()) newAllowedMenus.add(MenuAccessStore.MENU_BACKUP);
                        if (!isAdminRole && newAllowedMenus.isEmpty()) {
                            ViewUtils.toast(EditUserFragment.this.requireActivity(), getString(R.string.menu_access_required));
                            return;
                        }
                        viewModel.updateRoleAndAccess(safeUser.getUsername(), spinner.getSelectedItem().toString(), newAllowedMenus);
                    }
                });
                root.findViewById(R.id.btnDeleteUser).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        NotificationDialogHelper.showWarningConfirmation(
                                EditUserFragment.this.requireActivity(),
                                getString(R.string.delete),
                                getString(R.string.delete_user_message, safeUser.getUsername()),
                                getString(R.string.delete),
                                () -> viewModel.deleteUser(safeUser.getUsername())
                        );
                    }
                });
            }
        });

        viewModel.getUiEffect().observe(getViewLifecycleOwner(), new Observer<ConsumableEvent<FormUiEffect>>() {
            @Override public void onChanged(ConsumableEvent<FormUiEffect> wrapper) {
                if (wrapper == null) return;
                FormUiEffect effect = wrapper.consume();
                if (effect == null) return;
                setLoading(false, formContent);
                ViewUtils.toast(EditUserFragment.this.requireActivity(), effect.getMessage());
                if (effect.getType() == FormUiEffect.Type.CLOSE_SCREEN) closeSelf();
            }
        });

        viewModel.loadUser(username != null ? username : "kasir_01");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
            loadingDialog = null;
        }
    }

    private void setLoading(boolean isLoading, View formContent) {
        formContent.setAlpha(isLoading ? 0.6f : 1f);
        formContent.setEnabled(!isLoading);
        if (isLoading) {
            loadingDialog = LoadingDialogHelper.show(requireActivity(), getString(R.string.loading));
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

    private void closeSelf() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            requireActivity().finish();
        }
    }
}
