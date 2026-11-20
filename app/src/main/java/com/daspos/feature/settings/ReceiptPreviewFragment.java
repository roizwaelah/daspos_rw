package com.daspos.feature.settings;

import android.graphics.Bitmap;
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
import com.daspos.shared.util.ImageDecodeHelper;
import com.daspos.shared.util.ViewUtils;

public class ReceiptPreviewFragment extends Fragment {
    private static final int LOGO_MAX_DIMENSION = 512;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_receipt_preview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        Toolbar toolbar = root.findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.receipt_preview));

        String receiptHeader = ReceiptConfigStore.getHeader(requireContext());
        String receiptFooter = ReceiptConfigStore.getFooter(requireContext());
        boolean showLogo = ReceiptConfigStore.shouldShowLogo(requireContext());

        TextView headerView = root.findViewById(R.id.tvReceiptHeader);
        ((TextView) root.findViewById(R.id.tvReceiptStoreName)).setText(StoreConfigStore.getStoreName(requireContext()));
        ((TextView) root.findViewById(R.id.tvReceiptAddress)).setText(StoreConfigStore.getAddress(requireContext()));
        ((TextView) root.findViewById(R.id.tvReceiptPhone)).setText(StoreConfigStore.getPhone(requireContext()));
        ((TextView) root.findViewById(R.id.tvReceiptEmail)).setText(StoreConfigStore.getEmail(requireContext()));
        ((TextView) root.findViewById(R.id.tvReceiptFooter)).setText(receiptFooter);

        headerView.setText(receiptHeader);
        headerView.setVisibility(receiptHeader.isEmpty() ? View.GONE : View.VISIBLE);

        boolean hasIdentityLine = !StoreConfigStore.getAddress(requireContext()).isEmpty()
                || !StoreConfigStore.getPhone(requireContext()).isEmpty()
                || !StoreConfigStore.getEmail(requireContext()).isEmpty();
        root.findViewById(R.id.layoutReceiptIdentity).setVisibility(hasIdentityLine ? View.VISIBLE : View.GONE);

        ImageView logo = root.findViewById(R.id.imgReceiptStoreLogo);
        String logoUri = StoreConfigStore.getLogoUri(requireContext());
        if (showLogo && logoUri != null && !logoUri.trim().isEmpty() && loadLogoInto(logo, logoUri)) {
            logo.setVisibility(View.VISIBLE);
        } else {
            logo.setVisibility(View.GONE);
        }
    }

    private boolean loadLogoInto(ImageView logo, String logoUri) {
        Bitmap bitmap = ImageDecodeHelper.decodeSampled(requireContext(), logoUri, LOGO_MAX_DIMENSION);
        if (bitmap == null) return false;
        logo.setImageBitmap(bitmap);
        return true;
    }
}
