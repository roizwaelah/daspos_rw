package com.daspos.feature.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.daspos.R;
import com.daspos.BuildConfig;
import com.daspos.core.app.MainActivity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.feature.auth.LoginActivity;
import com.daspos.repository.UserRepository;
import com.daspos.repository.TransactionRepository;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.LoadingDialogHelper;
import com.daspos.shared.util.NetworkUtils;
import com.daspos.shared.util.NotificationDialogHelper;
import com.daspos.shared.util.ViewUtils;
import com.daspos.supabase.SupabaseAuthService;
import com.daspos.supabase.SupabaseConfig;

public class AccountSettingFragment extends Fragment {
    private AlertDialog loadingDialog;

    @Override
    public void onResume() {
        super.onResume();
        bindAccountInfo();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_account_setting, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        Toolbar toolbar = root.findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.account));
        bindAccountInfo();
        bindSettingItem(root, R.id.itemChangePassword, R.string.change_password, R.drawable.ic_lock);
        bindSettingItem(root, R.id.itemAccountLogout, R.string.logout, R.drawable.ic_logout);
        bindSettingItem(root, R.id.itemDeleteAccount, R.string.delete_account, R.drawable.ic_delete_account);
        bindSettingItem(root, R.id.itemDeleteOutletPurge, R.string.purge_outlet, R.drawable.ic_delete_outlet);
        root.findViewById(R.id.itemDeleteOutletPurge).setVisibility(AuthSessionStore.isAdmin(requireContext()) ? View.VISIBLE : View.GONE);

        root.findViewById(R.id.itemChangePassword).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ((MainActivity) AccountSettingFragment.this.requireActivity()).navigateTo(new ChangePasswordFragment()); }
        });
        root.findViewById(R.id.itemAccountLogout).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                NotificationDialogHelper.showWarningConfirmation(
                        AccountSettingFragment.this.requireActivity(),
                        R.string.logout,
                        R.string.logout_confirm_message,
                        R.string.logout,
                        () -> {
                            AuthSessionStore.clearSession(AccountSettingFragment.this.requireContext());
                            AccountSettingFragment.this.startActivity(new Intent(AccountSettingFragment.this.requireContext(), LoginActivity.class));
                            AccountSettingFragment.this.requireActivity().finishAffinity();
                        }
                );
            }
        });
        root.findViewById(R.id.itemDeleteAccount).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                NotificationDialogHelper.showWarningConfirmation(
                        AccountSettingFragment.this.requireActivity(),
                        R.string.delete_account,
                        R.string.delete_account_confirm_message,
                        R.string.delete_account,
                        () -> AccountSettingFragment.this.deleteCurrentAccount(false)
                );
            }
        });
        root.findViewById(R.id.itemDeleteOutletPurge).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                NotificationDialogHelper.showWarningConfirmation(
                        AccountSettingFragment.this.requireActivity(),
                        R.string.purge_outlet,
                        R.string.purge_outlet_confirm_message,
                        R.string.delete_account,
                        () -> AccountSettingFragment.this.deleteCurrentAccount(true)
                );
            }
        });
    }

    @Override
    public void onDestroyView() {
        LoadingDialogHelper.dismiss(loadingDialog);
        loadingDialog = null;
        super.onDestroyView();
    }

    private void bindAccountInfo() {
        View root = getView();
        if (root == null) return;
        String email = AuthSessionStore.getUsername(requireContext());
        String role = AuthSessionStore.getRole(requireContext());
        String storeName = StoreConfigStore.getStoreName(requireContext());

        if (email == null || email.trim().isEmpty()) email = "-";
        if (role == null || role.trim().isEmpty()) role = "-";
        if (storeName == null || storeName.trim().isEmpty()) storeName = "-";
        String source = AuthSessionStore.getSource(requireContext());
        boolean supabaseSessionValid = AuthSessionStore.hasValidSupabaseSession(requireContext());
        boolean repoSupabaseActive = TransactionRepository.isSupabaseSessionActive(requireContext());
        String debugInfo = "Build " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                + "\nSource: " + source
                + "\nSupabaseSessionValid: " + (supabaseSessionValid ? "YES" : "NO")
                + "\nRepoSupabaseActive: " + (repoSupabaseActive ? "YES" : "NO")
                + "\nReportQueryMode: period-filter";

        ((TextView) root.findViewById(R.id.tvUsernameInfo)).setText(getString(R.string.account_username_format, email));
        ((TextView) root.findViewById(R.id.tvRoleInfo)).setText(getString(R.string.account_role_format, role));
        ((TextView) root.findViewById(R.id.tvStoreInfo)).setText(getString(R.string.account_store_format, storeName));
        ((TextView) root.findViewById(R.id.tvDebugInfo)).setText(debugInfo);
    }

    private void bindSettingItem(View root, int includeId, int textRes, int iconRes) {
        View include = root.findViewById(includeId);
        ((TextView) include.findViewById(R.id.tvSettingTitle)).setText(getString(textRes));
        ((ImageView) include.findViewById(R.id.imgSettingIcon)).setImageResource(iconRes);
    }

    private void deleteCurrentAccount(boolean purgeOutlet) {
        String email = AuthSessionStore.getUsername(requireContext());
        if (email == null || email.trim().isEmpty()) {
            ViewUtils.toast(requireActivity(), getString(R.string.delete_account_failed));
            return;
        }
        String source = AuthSessionStore.getSource(requireContext());
        if ("supabase".equalsIgnoreCase(source)) {
            if (!SupabaseConfig.isConfigured() || !NetworkUtils.isOnline(requireContext())) {
                ViewUtils.toast(requireActivity(), getString(R.string.delete_account_need_internet));
                return;
            }

            setLoading(true);
            String accessToken = AuthSessionStore.getAccessToken(requireContext());
            String userId = AuthSessionStore.getUserId(requireContext());
            DbExecutor.runAsync(
                    () -> SupabaseAuthService.deleteCurrentAccount(accessToken, userId, purgeOutlet),
                    result -> {
                        setLoading(false);
                        if (!result.success) {
                            String msg = result.message == null || result.message.trim().isEmpty()
                                    ? getString(purgeOutlet ? R.string.purge_outlet_failed : R.string.delete_account_failed)
                                    : result.message;
                            ViewUtils.toast(requireActivity(), msg);
                            return;
                        }
                        UserRepository.deleteAsync(
                                requireContext(),
                                email,
                                () -> AccountSettingFragment.this.completeDeleteAccountSuccess(purgeOutlet),
                                throwable -> AccountSettingFragment.this.completeDeleteAccountSuccess(purgeOutlet)
                        );
                    },
                    throwable -> {
                        setLoading(false);
                        String msg = throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()
                                ? getString(purgeOutlet ? R.string.purge_outlet_failed : R.string.delete_account_failed)
                                : throwable.getMessage().trim();
                        ViewUtils.toast(requireActivity(), msg);
                    }
            );
            return;
        }

        setLoading(true);
        UserRepository.deleteAsync(
                requireContext(),
                email,
                () -> {
                    setLoading(false);
                    AccountSettingFragment.this.completeDeleteAccountSuccess(purgeOutlet);
                },
                throwable -> {
                    setLoading(false);
                    ViewUtils.toast(requireActivity(), getString(purgeOutlet ? R.string.purge_outlet_failed : R.string.delete_account_failed));
                }
        );
    }

    private void completeDeleteAccountSuccess(boolean purgeOutlet) {
        ViewUtils.toast(requireActivity(), getString(purgeOutlet ? R.string.purge_outlet_success : R.string.delete_account_success));
        AuthSessionStore.clearSession(requireContext());
        startActivity(new Intent(requireContext(), LoginActivity.class));
        requireActivity().finishAffinity();
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            loadingDialog = LoadingDialogHelper.show(requireActivity(), getString(R.string.loading));
            return;
        }
        LoadingDialogHelper.dismiss(loadingDialog);
        loadingDialog = null;
    }
}
