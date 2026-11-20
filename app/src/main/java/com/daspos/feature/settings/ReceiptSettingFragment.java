package com.daspos.feature.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.daspos.R;
import com.daspos.core.app.MainActivity;
import com.daspos.shared.util.ViewUtils;

public class ReceiptSettingFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_receipt_setting, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        Toolbar toolbar = root.findViewById(R.id.toolbar);
        final EditText etReceiptHeader = root.findViewById(R.id.etReceiptHeader);
        final EditText etReceiptFooter = root.findViewById(R.id.etReceiptFooter);
        final Switch switchReceiptLogo = root.findViewById(R.id.switchReceiptLogo);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.receipt));
        etReceiptHeader.setText(ReceiptConfigStore.getHeader(requireContext()));
        etReceiptFooter.setText(ReceiptConfigStore.getFooter(requireContext()));
        switchReceiptLogo.setChecked(ReceiptConfigStore.shouldShowLogo(requireContext()));
        root.findViewById(R.id.btnPreviewReceipt).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ReceiptConfigStore.save(
                        ReceiptSettingFragment.this.requireContext(),
                        etReceiptHeader.getText().toString(),
                        etReceiptFooter.getText().toString(),
                        switchReceiptLogo.isChecked()
                );
                ((MainActivity) ReceiptSettingFragment.this.requireActivity()).navigateTo(new ReceiptPreviewFragment());
            }
        });
        root.findViewById(R.id.btnReceiptCancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ReceiptSettingFragment.this.closeSelf(); }
        });
        root.findViewById(R.id.btnReceiptSave).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ReceiptConfigStore.save(
                        ReceiptSettingFragment.this.requireContext(),
                        etReceiptHeader.getText().toString(),
                        etReceiptFooter.getText().toString(),
                        switchReceiptLogo.isChecked()
                );
                ViewUtils.toast(ReceiptSettingFragment.this.requireActivity(), ReceiptSettingFragment.this.getString(R.string.saved_success));
            }
        });
    }

    private void closeSelf() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            requireActivity().finish();
        }
    }
}
