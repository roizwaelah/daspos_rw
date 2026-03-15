package com.daspos.feature.transaction;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.feature.product.ProductSearchAdapter;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.repository.ProductRepository;
import com.daspos.shared.util.CurrencyUtils;
import com.daspos.shared.util.ViewUtils;
import com.daspos.ui.UiStateRenderer;
import com.daspos.ui.state.ConsumableEvent;
import com.daspos.ui.state.ListUiState;
import com.daspos.ui.state.TransactionScreenState;
import com.daspos.ui.state.TransactionUiEffect;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class TransactionActivity extends BaseActivity {
    private final List<CartItem> cartItems = new ArrayList<CartItem>();
    private CartAdapter cartAdapter;
    private ProductSearchAdapter searchAdapter;
    private TextView tvTotal;
    private TextView tvChange;
    private EditText etPay;
    private TransactionViewModel viewModel;
    private RecyclerView rvCart;
    private View layoutCartState;
    private MaterialButton btnFinish;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.transactions));

        final RecyclerView rvSearch = findViewById(R.id.rvSearchResult);
        rvCart = findViewById(R.id.rvCart);
        EditText etSearch = findViewById(R.id.etSearchProduct);
        tvTotal = findViewById(R.id.tvTotal);
        tvChange = findViewById(R.id.tvChange);
        etPay = findViewById(R.id.etPay);
        MaterialButton btnCancel = findViewById(R.id.btnCancel);
        btnFinish = findViewById(R.id.btnFinish);
        final View layoutSearchState = findViewById(R.id.layoutSearchState);
        final ProgressBar progressSearch = findViewById(R.id.progressSearch);
        final TextView tvSearchState = findViewById(R.id.tvSearchState);
        layoutCartState = findViewById(R.id.layoutCartState);

        rvSearch.setLayoutManager(new LinearLayoutManager(this));
        rvCart.setLayoutManager(new LinearLayoutManager(this));

        searchAdapter = new ProductSearchAdapter(new ProductSearchAdapter.Listener() {
            @Override public void onAdd(Product product) { addToCart(product); }
        });
        cartAdapter = new CartAdapter();
        rvSearch.setAdapter(searchAdapter);
        rvCart.setAdapter(cartAdapter);
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);

        viewModel.getSearchResults().observe(this, new Observer<List<Product>>() {
            @Override public void onChanged(List<Product> results) {
                searchAdapter.submit(results == null ? new ArrayList<Product>() : results);
            }
        });
        viewModel.getSearchUiState().observe(this, new Observer<ListUiState<Product>>() {
            @Override public void onChanged(ListUiState<Product> state) {
                UiStateRenderer.renderListState(state, rvSearch, layoutSearchState, progressSearch, tvSearchState, getString(R.string.loading));
            }
        });
        viewModel.getScreenState().observe(this, new Observer<TransactionScreenState>() {
            @Override public void onChanged(TransactionScreenState state) {
                if (state == null) return;
                layoutCartState.setVisibility(state.isCartEmpty() ? View.VISIBLE : View.GONE);
                rvCart.setVisibility(state.isCartEmpty() ? View.GONE : View.VISIBLE);
                tvTotal.setText(CurrencyUtils.formatRupiah(state.getTotal()));
                tvChange.setText(getString(R.string.change) + ": " + CurrencyUtils.formatRupiah(state.getChange()));
                btnFinish.setEnabled(state.getCheckoutStatus() != TransactionScreenState.CheckoutStatus.PROCESSING);
            }
        });
        viewModel.getUiEffect().observe(this, new Observer<ConsumableEvent<TransactionUiEffect>>() {
            @Override public void onChanged(ConsumableEvent<TransactionUiEffect> wrapper) {
                if (wrapper == null) return;
                TransactionUiEffect effect = wrapper.consume();
                if (effect == null) return;
                if (effect.getType() == TransactionUiEffect.Type.SHOW_MESSAGE) {
                    ViewUtils.toast(TransactionActivity.this, effect.getMessage());
                    viewModel.clearCheckoutStatus();
                } else if (effect.getType() == TransactionUiEffect.Type.NAVIGATE_TO_RECEIPT) {
                    ViewUtils.toast(TransactionActivity.this, effect.getMessage());
                    startActivity(new Intent(TransactionActivity.this, StrukActivity.class));
                    cartItems.clear();
                    cartAdapter.submit(cartItems);
                    etPay.setText("");
                    viewModel.clearCheckoutStatus();
                    viewModel.updateScreenState(cartItems, "");
                }
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { viewModel.searchProducts(s.toString()); }
        });
        etPay.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { viewModel.updateScreenState(cartItems, s.toString()); }
        });
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                cartItems.clear();
                cartAdapter.submit(cartItems);
                etPay.setText("");
                viewModel.updateScreenState(cartItems, "");
            }
        });
        btnFinish.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { viewModel.checkout(new ArrayList<CartItem>(cartItems), String.valueOf(etPay.getText())); }
        });

        viewModel.updateScreenState(cartItems, "");
    }

    private void addToCart(Product product) {
        Product latest = ProductRepository.getById(this, product.getId());
        if (latest == null || latest.getStock() <= 0) { ViewUtils.toast(this, getString(R.string.product_out_of_stock)); return; }
        for (CartItem item : cartItems) {
            if (item.getProduct().getId().equals(product.getId())) {
                if (item.getQty() >= latest.getStock()) { ViewUtils.toast(this, getString(R.string.stock_not_enough)); return; }
                item.setQty(item.getQty() + 1);
                cartAdapter.submit(cartItems);
                viewModel.updateScreenState(cartItems, String.valueOf(etPay.getText()));
                return;
            }
        }
        cartItems.add(new CartItem(latest, 1));
        cartAdapter.submit(cartItems);
        viewModel.updateScreenState(cartItems, String.valueOf(etPay.getText()));
    }
}