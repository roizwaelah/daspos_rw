package com.daspos.feature.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.daspos.BuildConfig;
import com.daspos.feature.auth.AuthSessionStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.daspos.R;
import com.daspos.shared.util.ViewUtils;

public class AppInfoFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_app_info, container, false);
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
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.app_info));

        TextView tvAppVersion = root.findViewById(R.id.tvAppVersion);
        tvAppVersion.setText(
                getString(R.string.app_version_build_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        );
    }

    private void closeSelf() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            requireActivity().finish();
        }
    }
}
