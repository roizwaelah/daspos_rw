package com.daspos.feature.settings;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.daspos.R;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.shared.util.DbExecutor;
import com.daspos.shared.util.ImageDecodeHelper;
import com.daspos.shared.util.LoadingDialogHelper;
import com.daspos.shared.util.NetworkUtils;
import com.daspos.shared.util.ViewUtils;
import com.daspos.supabase.SupabaseConfig;
import com.daspos.supabase.SupabaseOutletService;

import java.util.Locale;

public class StoreInfoFragment extends Fragment {
    private static final int REQ_PICK_LOGO = 1201;
    private static final int LOGO_MAX_DIMENSION = 512;
    private AlertDialog loadingDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthSessionStore.isAdmin(requireContext())) {
            ViewUtils.toast(requireActivity(), getString(R.string.admin_only_feature));
            closeSelf();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_store_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        if (!AuthSessionStore.isAdmin(requireContext())) return;

        Toolbar toolbar = root.findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.business_info));

        final EditText etStoreName = root.findViewById(R.id.etStoreName);
        final EditText etStoreAddress = root.findViewById(R.id.etStoreAddress);
        final EditText etStorePhone = root.findViewById(R.id.etStorePhone);
        final EditText etStoreEmail = root.findViewById(R.id.etStoreEmail);

        etStoreName.setText(StoreConfigStore.getStoreName(requireContext()));
        etStoreAddress.setText(StoreConfigStore.getAddress(requireContext()));
        etStorePhone.setText(StoreConfigStore.getPhone(requireContext()));
        etStoreEmail.setText(StoreConfigStore.getEmail(requireContext()));
        renderLogo(etStoreName.getText().toString());
        etStoreName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderLogo(String.valueOf(s));
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        root.findViewById(R.id.btnSelectLogo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, REQ_PICK_LOGO);
            }
        });
        root.findViewById(R.id.btnRemoveLogo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                StoreConfigStore.clearLogo(requireContext());
                renderLogo(etStoreName.getText().toString());
                ViewUtils.toast(requireActivity(), "Logo dihapus");
            }
        });
        root.findViewById(R.id.btnStoreCancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeSelf(); }
        });
        root.findViewById(R.id.btnStoreSave).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String storeName = etStoreName.getText() == null ? "" : etStoreName.getText().toString();
                if (storeName.trim().isEmpty()) {
                    ViewUtils.toast(requireActivity(), getString(R.string.store_name_required));
                    return;
                }
                final String storeAddress = etStoreAddress.getText().toString();
                final String storePhone = etStorePhone.getText().toString();
                final String storeEmail = etStoreEmail.getText().toString();

                StoreConfigStore.save(
                        requireContext(),
                        storeName,
                        storeAddress,
                        storePhone,
                        storeEmail
                );
                String syncBlockedReason = getSyncBlockedReason();
                if (syncBlockedReason != null) {
                    ViewUtils.toast(
                            requireActivity(),
                            getString(R.string.saved_local_sync_pending) + " (" + syncBlockedReason + ")"
                    );
                    closeSelf();
                    return;
                }

                setLoading(true);
                final String outletId = AuthSessionStore.getOutletId(requireContext());
                final String accessToken = AuthSessionStore.getAccessToken(requireContext());
                DbExecutor.runAsync(() -> {
                    SupabaseOutletService.updateOutletInfo(
                            outletId,
                            accessToken,
                            storeName,
                            storeAddress,
                            storePhone,
                            storeEmail
                    );
                    return null;
                }, ignored -> {
                    setLoading(false);
                    ViewUtils.toast(requireActivity(), getString(R.string.saved_success));
                    closeSelf();
                }, throwable -> {
                    setLoading(false);
                    String err = throwable == null || throwable.getMessage() == null
                            ? ""
                            : throwable.getMessage().trim();
                    if (err.isEmpty()) {
                        ViewUtils.toast(requireActivity(), getString(R.string.saved_local_sync_failed));
                    } else {
                        ViewUtils.toast(requireActivity(), getString(R.string.saved_local_sync_failed) + ": " + err);
                    }
                    closeSelf();
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        setLoading(false);
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_LOGO && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String previousLogoUri = StoreConfigStore.getLogoUri(requireContext());
            try {
                requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) { }
            releaseLogoPermission(previousLogoUri, uri.toString());
            StoreConfigStore.saveLogoUri(requireContext(), uri.toString());
            EditText etStoreName = findViewById(R.id.etStoreName);
            renderLogo(etStoreName.getText().toString());
            ViewUtils.toast(requireActivity(), "Logo toko dipilih");
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T findViewById(int id) {
        View v = getView();
        return v == null ? null : (T) v.findViewById(id);
    }

    private void renderLogo(String storeName) {
        ImageView img = findViewById(R.id.imgStoreLogo);
        TextView tvInitial = findViewById(R.id.tvStoreLogoInitial);
        if (img == null || tvInitial == null) return;
        String logoUri = StoreConfigStore.getLogoUri(requireContext());
        if (logoUri != null && !logoUri.trim().isEmpty() && loadLogoInto(img, logoUri)) {
            tvInitial.setVisibility(View.GONE);
            return;
        }

        if (logoUri != null && !logoUri.trim().isEmpty()) {
            releaseLogoPermission(logoUri, "");
            StoreConfigStore.clearLogo(requireContext());
        }
        img.setImageDrawable(null);
        tvInitial.setText(extractStoreInitial(storeName));
        tvInitial.setVisibility(View.VISIBLE);
    }

    private boolean loadLogoInto(ImageView img, String logoUri) {
        Bitmap bitmap = ImageDecodeHelper.decodeSampled(requireContext(), logoUri, LOGO_MAX_DIMENSION);
        if (bitmap == null) return false;
        img.setImageBitmap(bitmap);
        return true;
    }

    private void releaseLogoPermission(String oldLogoUri, String newLogoUri) {
        if (oldLogoUri == null || oldLogoUri.trim().isEmpty()) return;
        if (oldLogoUri.equals(newLogoUri)) return;
        try {
            requireContext().getContentResolver().releasePersistableUriPermission(
                    Uri.parse(oldLogoUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (Exception ignored) { }
    }

    private String extractStoreInitial(String storeName) {
        if (storeName == null) return "D";
        String trimmed = storeName.trim();
        if (trimmed.isEmpty()) return "D";
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String getSyncBlockedReason() {
        if (!SupabaseConfig.isConfigured()) return "Supabase belum dikonfigurasi";
        String outletId = AuthSessionStore.getOutletId(requireContext());
        String accessToken = AuthSessionStore.getAccessToken(requireContext());
        if (outletId == null || outletId.trim().isEmpty()
                || accessToken == null || accessToken.trim().isEmpty()) {
            return "Sesi Supabase tidak valid, login ulang diperlukan";
        }
        if (!NetworkUtils.isOnline(requireContext())) return "Perangkat offline";
        return null;
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            loadingDialog = LoadingDialogHelper.show(requireActivity(), getString(R.string.loading));
            return;
        }
        LoadingDialogHelper.dismiss(loadingDialog);
        loadingDialog = null;
    }

    private void closeSelf() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) getParentFragmentManager().popBackStack();
        else requireActivity().finish();
    }
}
