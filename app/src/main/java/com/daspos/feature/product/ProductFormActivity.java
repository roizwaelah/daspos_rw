package com.daspos.feature.product;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.model.Product;
import com.daspos.model.SalesUnit;
import com.daspos.repository.ProductRepository;
import com.daspos.shared.util.LoadingDialogHelper;
import com.daspos.shared.util.ViewUtils;
import com.daspos.ui.state.ConsumableEvent;
import com.daspos.ui.state.FormUiEffect;
import com.daspos.viewmodel.ViewModelFactoryHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class ProductFormActivity extends BaseActivity {
    private Product editingProduct;
    private AlertDialog loadingDialog;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_form);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.product_form_title));

        final EditText etName = findViewById(R.id.etProductName);
        final EditText etStock = findViewById(R.id.etStock);
        final EditText etPriceEcer = findViewById(R.id.etPriceEcer);
        final EditText etPriceRenteng = findViewById(R.id.etPriceRenteng);
        final EditText etPricePak = findViewById(R.id.etPricePak);
        final EditText etPriceKarton = findViewById(R.id.etPriceKarton);
        final EditText etFactorRenteng = findViewById(R.id.etFactorRenteng);
        final EditText etFactorPak = findViewById(R.id.etFactorPak);
        final EditText etFactorKarton = findViewById(R.id.etFactorKarton);
        final SwitchMaterial swUseTierPricing = findViewById(R.id.swUseTierPricing);
        MaterialButton btnCancel = findViewById(R.id.btnCancel);
        MaterialButton btnSave = findViewById(R.id.btnSave);
        View formContent = findViewById(R.id.layoutProductFormContent);
        View tierPricingSection = findViewById(R.id.layoutTierPricingSection);

        swUseTierPricing.setChecked(true);
        setTierPricingSectionVisibility(tierPricingSection, true);
        swUseTierPricing.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setTierPricingSectionVisibility(tierPricingSection, isChecked);
            etPriceRenteng.setError(null);
            etFactorRenteng.setError(null);
        });

        final ProductFormViewModel viewModel = ViewModelFactoryHelper.get(this, ProductFormViewModel.class);
        viewModel.getValidationState().observe(this, new Observer<com.daspos.ui.state.ValidationState>() {
            @Override public void onChanged(com.daspos.ui.state.ValidationState state) {
                if (state == null || state.getCode().isEmpty()) return;
                if ("NAME_REQUIRED".equals(state.getCode())) etName.setError(getString(R.string.product_name));
                else if ("INVALID_PRICE".equals(state.getCode())) etPriceEcer.setError(getString(R.string.invalid_price));
                else if ("INVALID_TIER_PRICE".equals(state.getCode())) etPriceRenteng.setError(getString(R.string.invalid_price));
                else if ("INVALID_STOCK".equals(state.getCode())) etStock.setError(getString(R.string.invalid_stock));
                else if ("INVALID_FACTOR".equals(state.getCode())) etFactorRenteng.setError(getString(R.string.invalid_factor));
            }
        });
        viewModel.getUiEffect().observe(this, new Observer<ConsumableEvent<FormUiEffect>>() {
            @Override public void onChanged(ConsumableEvent<FormUiEffect> wrapper) {
                if (wrapper == null) return;
                FormUiEffect effect = wrapper.consume();
                if (effect == null) return;
                ViewUtils.toast(ProductFormActivity.this, effect.getMessage());
                if (effect.getType() == FormUiEffect.Type.CLOSE_SCREEN) finish();
            }
        });

        String productId = getIntent().getStringExtra("product_id");
        if (productId != null) {
            setLoading(true, btnSave, btnCancel, formContent);
            ProductRepository.getByIdAsync(this, productId, product -> {
                editingProduct = product;
                if (editingProduct != null) {
                    etName.setText(editingProduct.getName());
                    etPriceEcer.setText(String.valueOf((int) editingProduct.getPriceEcer()));
                    etPriceRenteng.setText(String.valueOf((int) editingProduct.getPriceRenteng()));
                    etPricePak.setText(String.valueOf((int) editingProduct.getPricePak()));
                    etPriceKarton.setText(String.valueOf((int) editingProduct.getPriceKarton()));
                    etFactorRenteng.setText(String.valueOf(editingProduct.getFactorRenteng()));
                    etFactorPak.setText(String.valueOf(editingProduct.getFactorPak()));
                    etFactorKarton.setText(String.valueOf(editingProduct.getFactorKarton()));
                    etStock.setText(String.valueOf(editingProduct.getStock()));
                    boolean useTierPricing = isTierPricingEnabled(editingProduct);
                    swUseTierPricing.setChecked(useTierPricing);
                    setTierPricingSectionVisibility(tierPricingSection, useTierPricing);
                }
                setLoading(false, btnSave, btnCancel, formContent);
            }, throwable -> {
                setLoading(false, btnSave, btnCancel, formContent);
                ViewUtils.toast(ProductFormActivity.this, "Gagal memuat data produk");
            });
        }

        btnCancel.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { finish(); }
        });
        btnSave.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                String name = etName.getText().toString().trim();
                String stockText = etStock.getText().toString().trim();
                String priceEcerText = etPriceEcer.getText().toString().trim();
                String priceRentengText = etPriceRenteng.getText().toString().trim();
                String pricePakText = etPricePak.getText().toString().trim();
                String priceKartonText = etPriceKarton.getText().toString().trim();
                String factorRentengText = etFactorRenteng.getText().toString().trim();
                String factorPakText = etFactorPak.getText().toString().trim();
                String factorKartonText = etFactorKarton.getText().toString().trim();
                boolean useTierPricing = swUseTierPricing.isChecked();
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
                        stockText,
                        priceEcerText,
                        priceRentengText,
                        pricePakText,
                        priceKartonText,
                        factorRentengText,
                        factorPakText,
                        factorKartonText,
                        useTierPricing
                )) return;
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
                            stock,
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
                    editingProduct.setStock(stock);
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

    private void setTierPricingSectionVisibility(View section, boolean visible) {
        if (section == null) return;
        section.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private boolean isTierPricingEnabled(Product product) {
        return product == null || product.isTierPricingEnabled();
    }

    private void setLoading(boolean isLoading, MaterialButton btnSave, MaterialButton btnCancel, View formContent) {
        btnSave.setEnabled(!isLoading);
        btnCancel.setEnabled(!isLoading);
        formContent.setAlpha(isLoading ? 0.6f : 1f);
        if (isLoading) {
            loadingDialog = LoadingDialogHelper.show(this, getString(R.string.loading));
        } else {
            LoadingDialogHelper.dismiss(loadingDialog);
            loadingDialog = null;
        }
    }
}
