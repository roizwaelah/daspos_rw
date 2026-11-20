package com.daspos.feature.product;

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
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.daspos.R;
import com.daspos.model.Product;
import com.daspos.model.SalesUnit;
import com.daspos.repository.ProductRepository;
import com.daspos.shared.util.LoadingDialogHelper;
import com.daspos.shared.util.ViewUtils;
import com.daspos.ui.state.ConsumableEvent;
import com.daspos.ui.state.FormUiEffect;
import com.daspos.ui.state.ValidationState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class ProductFormFragment extends Fragment {
    private static final String ARG_PRODUCT_ID = "product_id";

    private Product editingProduct;
    private AlertDialog loadingDialog;

    public static ProductFormFragment newInstance(String productId) {
        ProductFormFragment fragment = new ProductFormFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PRODUCT_ID, productId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_product_form, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        Toolbar toolbar = root.findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.product_form_title));

        final EditText etName = root.findViewById(R.id.etProductName);
        final EditText etBarcode = root.findViewById(R.id.etProductBarcode);
        final EditText etCostPrice = root.findViewById(R.id.etCostPrice);
        final EditText etStock = root.findViewById(R.id.etStock);
        final EditText etUnitName = root.findViewById(R.id.etUnitName);
        final EditText etPriceEcer = root.findViewById(R.id.etPriceEcer);
        final EditText etPriceRenteng = root.findViewById(R.id.etPriceRenteng);
        final EditText etPricePak = root.findViewById(R.id.etPricePak);
        final EditText etPriceKarton = root.findViewById(R.id.etPriceKarton);
        final EditText etFactorRenteng = root.findViewById(R.id.etFactorRenteng);
        final EditText etFactorPak = root.findViewById(R.id.etFactorPak);
        final EditText etFactorKarton = root.findViewById(R.id.etFactorKarton);
        final SwitchMaterial swUseTierPricing = root.findViewById(R.id.swUseTierPricing);
        final SwitchMaterial swTrackStock = root.findViewById(R.id.swTrackStock);
        MaterialButton btnCancel = root.findViewById(R.id.btnCancel);
        MaterialButton btnSave = root.findViewById(R.id.btnSave);
        View formContent = root.findViewById(R.id.layoutProductFormContent);
        View tierPricingSection = root.findViewById(R.id.layoutTierPricingSection);
        View stockAmountSection = root.findViewById(R.id.layoutStockAmount);

        swTrackStock.setChecked(true);
        setSectionVisibility(stockAmountSection, true);
        swTrackStock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setSectionVisibility(stockAmountSection, isChecked);
            etStock.setError(null);
            if (!isChecked) etStock.setText("0");
        });
        swUseTierPricing.setChecked(true);
        setSectionVisibility(tierPricingSection, true);
        swUseTierPricing.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setSectionVisibility(tierPricingSection, isChecked);
            etPriceRenteng.setError(null);
            etFactorRenteng.setError(null);
        });

        // Fragment-scoped: a form VM must not leak validation state or a
        // pending CLOSE_SCREEN effect into the next time the form is opened.
        final ProductFormViewModel viewModel = new ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication())
        ).get(ProductFormViewModel.class);
        viewModel.getValidationState().observe(getViewLifecycleOwner(), new Observer<ValidationState>() {
            @Override public void onChanged(ValidationState state) {
                if (state == null || state.getCode().isEmpty()) return;
                if ("NAME_REQUIRED".equals(state.getCode())) etName.setError(getString(R.string.product_name));
                else if ("INVALID_COST_PRICE".equals(state.getCode())) etCostPrice.setError(getString(R.string.invalid_price));
                else if ("UNIT_REQUIRED".equals(state.getCode())) etUnitName.setError(getString(R.string.product_unit_name));
                else if ("INVALID_PRICE".equals(state.getCode())) etPriceEcer.setError(getString(R.string.invalid_price));
                else if ("INVALID_TIER_PRICE".equals(state.getCode())) etPriceRenteng.setError(getString(R.string.invalid_price));
                else if ("INVALID_STOCK".equals(state.getCode())) etStock.setError(getString(R.string.invalid_stock));
                else if ("INVALID_FACTOR".equals(state.getCode())) etFactorRenteng.setError(getString(R.string.invalid_factor));
            }
        });
        viewModel.getUiEffect().observe(getViewLifecycleOwner(), new Observer<ConsumableEvent<FormUiEffect>>() {
            @Override public void onChanged(ConsumableEvent<FormUiEffect> wrapper) {
                if (wrapper == null) return;
                FormUiEffect effect = wrapper.consume();
                if (effect == null) return;
                ViewUtils.toast(requireActivity(), effect.getMessage());
                if (effect.getType() == FormUiEffect.Type.CLOSE_SCREEN) closeSelf();
            }
        });

        Bundle args = getArguments();
        String productId = args != null ? args.getString(ARG_PRODUCT_ID) : null;
        if (productId != null) {
            setLoading(true, btnSave, btnCancel, formContent);
            // getById applies ProductUnitPriceStore internally, so tier prices
            // and conversion factors arrive already hydrated on the product.
            ProductRepository.getByIdAsync(requireContext(), productId, product -> {
                editingProduct = product;
                if (editingProduct != null) {
                    etName.setText(editingProduct.getName());
                    etBarcode.setText(editingProduct.getBarcode());
                    etCostPrice.setText(String.valueOf((int) editingProduct.getCostPrice()));
                    etUnitName.setText(editingProduct.getUnitName());
                    etPriceEcer.setText(String.valueOf((int) editingProduct.getPriceEcer()));
                    etPriceRenteng.setText(String.valueOf((int) editingProduct.getPriceRenteng()));
                    etPricePak.setText(String.valueOf((int) editingProduct.getPricePak()));
                    etPriceKarton.setText(String.valueOf((int) editingProduct.getPriceKarton()));
                    etFactorRenteng.setText(String.valueOf(editingProduct.getFactorRenteng()));
                    etFactorPak.setText(String.valueOf(editingProduct.getFactorPak()));
                    etFactorKarton.setText(String.valueOf(editingProduct.getFactorKarton()));
                    etStock.setText(String.valueOf(editingProduct.getStock()));
                    swTrackStock.setChecked(editingProduct.isTrackStock());
                    setSectionVisibility(stockAmountSection, editingProduct.isTrackStock());
                    boolean useTierPricing = editingProduct.isTierPricingEnabled();
                    swUseTierPricing.setChecked(useTierPricing);
                    setSectionVisibility(tierPricingSection, useTierPricing);
                }
                setLoading(false, btnSave, btnCancel, formContent);
            }, throwable -> {
                setLoading(false, btnSave, btnCancel, formContent);
                ViewUtils.toast(requireActivity(), "Gagal memuat data produk");
            });
        }

        btnCancel.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { closeSelf(); }
        });
        btnSave.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                String name = etName.getText().toString().trim();
                String barcode = etBarcode.getText().toString().trim();
                String costPriceText = etCostPrice.getText().toString().trim();
                String stockText = etStock.getText().toString().trim();
                String unitName = etUnitName.getText().toString().trim();
                String priceEcerText = etPriceEcer.getText().toString().trim();
                String priceRentengText = etPriceRenteng.getText().toString().trim();
                String pricePakText = etPricePak.getText().toString().trim();
                String priceKartonText = etPriceKarton.getText().toString().trim();
                String factorRentengText = etFactorRenteng.getText().toString().trim();
                String factorPakText = etFactorPak.getText().toString().trim();
                String factorKartonText = etFactorKarton.getText().toString().trim();
                boolean useTierPricing = swUseTierPricing.isChecked();
                boolean trackStock = swTrackStock.isChecked();
                if (costPriceText.isEmpty()) costPriceText = "0";
                if (!trackStock || stockText.isEmpty()) stockText = "0";
                int defaultFactorRenteng = SalesUnit.defaultFactor(SalesUnit.RENTENG);
                int defaultFactorPak = SalesUnit.defaultFactor(SalesUnit.PAK);
                int defaultFactorKarton = SalesUnit.defaultFactor(SalesUnit.KARTON);
                if (priceRentengText.isEmpty()) priceRentengText = "0";
                if (pricePakText.isEmpty()) pricePakText = "0";
                if (priceKartonText.isEmpty()) priceKartonText = "0";
                if (factorRentengText.isEmpty()) factorRentengText = String.valueOf(defaultFactorRenteng);
                if (factorPakText.isEmpty()) factorPakText = String.valueOf(defaultFactorPak);
                if (factorKartonText.isEmpty()) factorKartonText = String.valueOf(defaultFactorKarton);
                if (!viewModel.validate(
                        name,
                        costPriceText,
                        stockText,
                        unitName,
                        priceEcerText,
                        priceRentengText,
                        pricePakText,
                        priceKartonText,
                        factorRentengText,
                        factorPakText,
                        factorKartonText,
                        useTierPricing,
                        trackStock
                )) return;
                double costPrice = Double.parseDouble(costPriceText);
                int stock = Integer.parseInt(stockText);
                double priceEcer = Double.parseDouble(priceEcerText);
                double priceRenteng;
                double pricePak;
                double priceKarton;
                int factorRenteng;
                int factorPak;
                int factorKarton;
                if (useTierPricing) {
                    priceRenteng = Double.parseDouble(priceRentengText);
                    pricePak = Double.parseDouble(pricePakText);
                    priceKarton = Double.parseDouble(priceKartonText);
                    factorRenteng = Integer.parseInt(factorRentengText);
                    factorPak = Integer.parseInt(factorPakText);
                    factorKarton = Integer.parseInt(factorKartonText);
                    if (priceRenteng <= 0) priceRenteng = priceEcer * factorRenteng;
                    if (pricePak <= 0) pricePak = priceEcer * factorPak;
                    if (priceKarton <= 0) priceKarton = priceEcer * factorKarton;
                } else {
                    priceRenteng = priceEcer;
                    pricePak = priceEcer;
                    priceKarton = priceEcer;
                    factorRenteng = 1;
                    factorPak = 1;
                    factorKarton = 1;
                }

                if (editingProduct == null) {
                    viewModel.saveNew(
                            name,
                            barcode,
                            costPrice,
                            stock,
                            unitName,
                            trackStock,
                            priceEcer,
                            priceRenteng,
                            pricePak,
                            priceKarton,
                            factorRenteng,
                            factorPak,
                            factorKarton
                    );
                } else {
                    editingProduct.setName(name);
                    editingProduct.setBarcode(barcode);
                    editingProduct.setCostPrice(costPrice);
                    editingProduct.setStock(stock);
                    editingProduct.setUnitName(unitName);
                    editingProduct.setTrackStock(trackStock);
                    editingProduct.setPriceEcer(priceEcer);
                    editingProduct.setFactorRenteng(factorRenteng);
                    editingProduct.setFactorPak(factorPak);
                    editingProduct.setFactorKarton(factorKarton);
                    editingProduct.setPriceRenteng(priceRenteng);
                    editingProduct.setPricePak(pricePak);
                    editingProduct.setPriceKarton(priceKarton);
                    viewModel.saveEdit(editingProduct);
                }
            }
        });
    }

    private void closeSelf() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) getParentFragmentManager().popBackStack();
        else requireActivity().finish();
    }

    private void setSectionVisibility(View section, boolean visible) {
        if (section == null) return;
        section.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setLoading(boolean isLoading, MaterialButton btnSave, MaterialButton btnCancel, View formContent) {
        btnSave.setEnabled(!isLoading);
        btnCancel.setEnabled(!isLoading);
        formContent.setAlpha(isLoading ? 0.6f : 1f);
        if (isLoading) {
            loadingDialog = LoadingDialogHelper.show(requireActivity(), getString(R.string.loading));
        } else {
            LoadingDialogHelper.dismiss(loadingDialog);
            loadingDialog = null;
        }
    }
}
