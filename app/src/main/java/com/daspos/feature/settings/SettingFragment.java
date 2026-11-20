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
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.daspos.R;
import com.daspos.core.app.MainActivity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.feature.auth.LoginActivity;
import com.daspos.feature.user.UserListFragment;
import com.daspos.shared.util.NotificationDialogHelper;
import com.daspos.shared.util.ViewUtils;

public class SettingFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_setting, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        Toolbar toolbar = root.findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.settings));
        boolean isAdmin = AuthSessionStore.isAdmin(requireContext());

        bindSettingItem(root, R.id.itemStoreInfo, R.string.business_info, R.drawable.ic_store);
        bindSettingItem(root, R.id.itemPrinter, R.string.printer, R.drawable.ic_menu_printer);
        bindSettingItem(root, R.id.itemReceipt, R.string.receipt, R.drawable.ic_receipt);
        bindSettingItem(root, R.id.itemAccount, R.string.account, R.drawable.ic_user);
        bindSettingItem(root, R.id.itemContact, R.string.contact_us, R.drawable.ic_whatsapp);
        bindSettingItem(root, R.id.itemManageUsers, R.string.user_list, R.drawable.ic_user_group);
        bindSettingItem(root, R.id.itemSelectDatabase, R.string.select_database, R.drawable.ic_menu_database);
        bindSettingItem(root, R.id.itemAppInfo, R.string.app_info, R.drawable.ic_info);
        root.findViewById(R.id.itemManageUsers).setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.itemStoreInfo).setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.itemContact).setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.itemSelectDatabase).setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.itemAppInfo).setVisibility(isAdmin ? View.VISIBLE : View.GONE);

        root.findViewById(R.id.itemStoreInfo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) requireActivity()).navigateTo(new StoreInfoFragment());
            }
        });

        root.findViewById(R.id.itemPrinter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) requireActivity()).navigateTo(new com.daspos.feature.printer.PrinterSettingFragment());
            }
        });

        root.findViewById(R.id.itemReceipt).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) requireActivity()).navigateTo(new ReceiptSettingFragment());
            }
        });

        root.findViewById(R.id.itemAccount).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) requireActivity()).navigateTo(new AccountSettingFragment());
            }
        });

        root.findViewById(R.id.itemContact).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) requireActivity()).navigateTo(new ContactUsFragment());
            }
        });

        root.findViewById(R.id.itemManageUsers).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) requireActivity()).navigateTo(new UserListFragment());
            }
        });

        root.findViewById(R.id.itemSelectDatabase).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(requireContext(), SelectDatabaseActivity.class));
            }
        });

        root.findViewById(R.id.itemAppInfo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) requireActivity()).navigateTo(new AppInfoFragment());
            }
        });

        root.findViewById(R.id.btnLogout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NotificationDialogHelper.showWarningConfirmation(
                        requireActivity(),
                        R.string.logout,
                        R.string.logout_confirm_message,
                        R.string.logout,
                        () -> {
                            AuthSessionStore.clearSession(requireContext());
                            startActivity(new Intent(requireContext(), LoginActivity.class));
                            requireActivity().finishAffinity();
                        }
                );
            }
        });
    }

    private void bindSettingItem(View root, int includeId, int textRes, int iconRes) {
        View include = root.findViewById(includeId);
        ((TextView) include.findViewById(R.id.tvSettingTitle)).setText(getString(textRes));
        ((ImageView) include.findViewById(R.id.imgSettingIcon)).setImageResource(iconRes);
    }
}
