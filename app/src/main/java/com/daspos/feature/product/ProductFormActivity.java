package com.daspos.feature.product;

import android.os.Bundle;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.model.Product;
import com.daspos.repository.ProductRepository;
import com.daspos.shared.util.ViewUtils;
import com.daspos.ui.state.ConsumableEvent;
import com.daspos.ui.state.FormUiEffect;
import com.daspos.viewmodel.ViewModelFactoryHelper;
import com.google.android.material.button.MaterialButton;

public class ProductFormActivity extends BaseActivity {
    private Product editingProduct;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_form);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.product_form_title));

        final EditText etName = findViewById(R.id.etProductName);
        final EditText etPrice = findViewById(R.id.etPrice);
        final EditText etStock = findViewById(R.id.etStock);
        MaterialButton btnCancel = findViewById(R.id.btnCancel);
        MaterialButton btnSave = findViewById(R.id.btnSave);

        final ProductFormViewModel viewModel = ViewModelFactoryHelper.get(this, ProductFormViewModel.class);
        viewModel.getValidationState().observe(this, new Observer<com.daspos.ui.state.ValidationState>() {
            @Override public void onChanged(com.daspos.ui.state.ValidationState state) {
                if (state == null || state.getCode().isEmpty()) return;
                if ("NAME_REQUIRED".equals(state.getCode())) etName.setError(getString(R.string.product_name));
                else if ("INVALID_PRICE".equals(state.getCode())) etPrice.setError(getString(R.string.invalid_price));
                else if ("INVALID_STOCK".equals(state.getCode())) etStock.setError(getString(R.string.invalid_stock));
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
            ProductRepository.getByIdAsync(this, productId, product -> {
                editingProduct = product;
                if (editingProduct != null) {
                    etName.setText(editingProduct.getName());
                    etPrice.setText(String.valueOf((int) editingProduct.getPrice()));
                    etStock.setText(String.valueOf(editingProduct.getStock()));
                }
            }, throwable -> ViewUtils.toast(ProductFormActivity.this, "Gagal memuat data produk"));
        }

        btnCancel.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { finish(); }
        });
        btnSave.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) {
                String name = etName.getText().toString().trim();
                String priceText = etPrice.getText().toString().trim();
                String stockText = etStock.getText().toString().trim();
                if (!viewModel.validate(name, priceText, stockText)) return;
                double price = Double.parseDouble(priceText);
                int stock = Integer.parseInt(stockText);
                if (editingProduct == null) viewModel.saveNew(name, price, stock);
                else {
                    editingProduct.setName(name);
                    editingProduct.setPrice(price);
                    editingProduct.setStock(stock);
                    viewModel.saveEdit(editingProduct);
                }
            }
        });
    }
}