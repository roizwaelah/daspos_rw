package com.daspos.feature.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.daspos.R;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.shared.util.ViewUtils;

public class ContactUsFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_contact_us, container, false);
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
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.contact_us));
        root.findViewById(R.id.btnWhatsapp).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String number = "628112630731";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + number));
                try { startActivity(intent); } catch (Exception e) { ViewUtils.toast(requireActivity(), "WhatsApp/browser tidak tersedia"); }
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
