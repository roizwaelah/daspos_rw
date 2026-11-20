package com.daspos.feature.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.daspos.R;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.repository.UserRepository;
import com.daspos.shared.util.LoadingDialogHelper;
import com.daspos.shared.util.PasswordFieldToggleHelper;
import com.daspos.shared.util.ViewUtils;

public class ChangePasswordFragment extends Fragment {
    private AlertDialog loadingDialog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_change_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        Toolbar toolbar = root.findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.change_password));
        final EditText oldPass = root.findViewById(R.id.etOldPassword);
        final EditText newPass = root.findViewById(R.id.etNewPassword);
        final EditText confirmPass = root.findViewById(R.id.etConfirmPassword);
        PasswordFieldToggleHelper.attach(oldPass);
        PasswordFieldToggleHelper.attach(newPass);
        PasswordFieldToggleHelper.attach(confirmPass);
        View btnSave = root.findViewById(R.id.btnSave);
        View btnCancel = root.findViewById(R.id.btnCancel);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String oldVal = oldPass.getText().toString().trim();
                String newVal = newPass.getText().toString().trim();
                String confirmVal = confirmPass.getText().toString().trim();
                String username = AuthSessionStore.getUsername(ChangePasswordFragment.this.requireContext());
                if (oldVal.isEmpty() || newVal.isEmpty() || confirmVal.isEmpty()) { ViewUtils.toast(ChangePasswordFragment.this.requireActivity(), getString(R.string.field_required)); return; }
                if (!newVal.equals(confirmVal)) { ViewUtils.toast(ChangePasswordFragment.this.requireActivity(), getString(R.string.password_not_match)); return; }
                if (username == null || username.trim().isEmpty()) { ViewUtils.toast(ChangePasswordFragment.this.requireActivity(), getString(R.string.user_session_not_found)); return; }
                setLoading(true, btnSave, btnCancel);
                UserRepository.authenticateAsync(ChangePasswordFragment.this.requireContext(), username, oldVal, authenticated -> {
                    if (!authenticated) {
                        setLoading(false, btnSave, btnCancel);
                        ViewUtils.toast(ChangePasswordFragment.this.requireActivity(), getString(R.string.old_password_invalid));
                        return;
                    }
                    UserRepository.updatePasswordAsync(ChangePasswordFragment.this.requireContext(), username, newVal, () -> {
                        setLoading(false, btnSave, btnCancel);
                        ViewUtils.toast(ChangePasswordFragment.this.requireActivity(), getString(R.string.password_updated));
                        closeSelf();
                    }, throwable -> {
                        setLoading(false, btnSave, btnCancel);
                        ViewUtils.toast(ChangePasswordFragment.this.requireActivity(), getString(R.string.password_update_failed));
                    });
                }, throwable -> {
                    setLoading(false, btnSave, btnCancel);
                    ViewUtils.toast(ChangePasswordFragment.this.requireActivity(), getString(R.string.password_update_failed));
                });
            }
        });
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeSelf(); }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
            loadingDialog = null;
        }
    }

    private void setLoading(boolean isLoading, View btnSave, View btnCancel) {
        btnSave.setEnabled(!isLoading);
        btnCancel.setEnabled(!isLoading);
        if (isLoading) {
            loadingDialog = LoadingDialogHelper.show(requireActivity(), getString(R.string.loading));
        } else {
            LoadingDialogHelper.dismiss(loadingDialog);
            loadingDialog = null;
        }
    }

    private void closeSelf() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            requireActivity().finish();
        }
    }
}
