package com.daspos.feature.transaction;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.feature.auth.MenuAccessGuard;
import com.daspos.feature.auth.MenuAccessStore;
import com.daspos.feature.product.ProductSearchAdapter;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
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

public class TransactionFragment extends Fragment {
    private static final long SEARCH_DEBOUNCE_MS = 250L;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private String pendingSearchQuery = "";
    private CartAdapter cartAdapter;
    private ProductSearchAdapter searchAdapter;
    private TextView tvTotal;
    private TextView tvChange;
    private EditText etPay;
    private EditText etDiscount;
    private EditText etSearchProduct;
    private EditText etManualName;
    private EditText etManualPrice;
    private TransactionViewModel viewModel;
    private RecyclerView rvCart;
    private View layoutCartState;
    private MaterialButton btnFinish;
    private View searchDropdownCard;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!MenuAccessGuard.ensureFragmentAccess(this, MenuAccessStore.MENU_TRANSACTION)) return;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_transaction, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        Toolbar toolbar = root.findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.transactions));

        final RecyclerView rvSearch = root.findViewById(R.id.rvSearchResult);
        rvCart = root.findViewById(R.id.rvCart);
        etSearchProduct = root.findViewById(R.id.etSearchProduct);
        tvTotal = root.findViewById(R.id.tvTotal);
        tvChange = root.findViewById(R.id.tvChange);
        etPay = root.findViewById(R.id.etPay);
        etDiscount = root.findViewById(R.id.etDiscount);
        etManualName = root.findViewById(R.id.etManualName);
        etManualPrice = root.findViewById(R.id.etManualPrice);
        applyPayHintStyle();
        MaterialButton btnCancel = root.findViewById(R.id.btnCancel);
        MaterialButton btnAddManual = root.findViewById(R.id.btnAddManualItem);
        btnFinish = root.findViewById(R.id.btnFinish);
        searchDropdownCard = root.findViewById(R.id.searchDropdownCard);
        final View layoutSearchState = root.findViewById(R.id.layoutSearchState);
        final ProgressBar progressSearch = root.findViewById(R.id.progressSearch);
        final TextView tvSearchState = root.findViewById(R.id.tvSearchState);
        layoutCartState = root.findViewById(R.id.layoutCartState);

        rvSearch.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCart.setLayoutManager(new LinearLayoutManager(requireContext()));

        searchAdapter = new ProductSearchAdapter(new ProductSearchAdapter.Listener() {
            @Override public void onAdd(Product product) {
                viewModel.addToCart(product, new Runnable() {
                    @Override public void run() {
                        clearSearchInput();
                    }
                });
            }
        });
        cartAdapter = new CartAdapter(new CartAdapter.Listener() {
            @Override public void onMinus(CartItem item) { viewModel.decrementCartItem(item); }
            @Override public void onPlus(CartItem item) { viewModel.incrementCartItem(item); }
            @Override public void onUnitChanged(CartItem item, String unitCode) { viewModel.changeCartItemUnit(item, unitCode); }
        });
        rvSearch.setAdapter(searchAdapter);
        rvCart.setAdapter(cartAdapter);
        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);

        viewModel.getSearchResults().observe(getViewLifecycleOwner(), new Observer<List<Product>>() {
            @Override public void onChanged(List<Product> results) {
                if (isSearchQueryEmpty()) {
                    searchAdapter.submit(new ArrayList<Product>());
                    return;
                }
                searchAdapter.submit(results == null ? new ArrayList<Product>() : results);
            }
        });
        viewModel.getSearchUiState().observe(getViewLifecycleOwner(), new Observer<ListUiState<Product>>() {
            @Override public void onChanged(ListUiState<Product> state) {
                UiStateRenderer.renderListState(state, rvSearch, layoutSearchState, progressSearch, tvSearchState, getString(R.string.loading));
                if (isSearchQueryEmpty()) {
                    searchDropdownCard.setVisibility(View.GONE);
                    return;
                }
                searchDropdownCard.setVisibility(state != null && state.getStatus() != ListUiState.Status.SUCCESS ? View.VISIBLE : (searchAdapter.getItemCount() > 0 ? View.VISIBLE : View.GONE));
            }
        });
        viewModel.getCartItems().observe(getViewLifecycleOwner(), new Observer<List<CartItem>>() {
            @Override public void onChanged(List<CartItem> items) {
                cartAdapter.submit(items == null ? new ArrayList<CartItem>() : new ArrayList<CartItem>(items));
            }
        });
        viewModel.getScreenState().observe(getViewLifecycleOwner(), new Observer<TransactionScreenState>() {
            @Override public void onChanged(TransactionScreenState state) {
                if (state == null) return;
                layoutCartState.setVisibility(state.isCartEmpty() ? View.VISIBLE : View.GONE);
                rvCart.setVisibility(state.isCartEmpty() ? View.GONE : View.VISIBLE);
                tvTotal.setText(CurrencyUtils.formatRupiah(state.getTotal()));
                tvChange.setText(CurrencyUtils.formatRupiah(state.getChange()));
                btnFinish.setEnabled(state.getCheckoutStatus() != TransactionScreenState.CheckoutStatus.PROCESSING);
            }
        });
        viewModel.getUiEffect().observe(getViewLifecycleOwner(), new Observer<ConsumableEvent<TransactionUiEffect>>() {
            @Override public void onChanged(ConsumableEvent<TransactionUiEffect> wrapper) {
                if (wrapper == null) return;
                TransactionUiEffect effect = wrapper.consume();
                if (effect == null) return;
                if (effect.getType() == TransactionUiEffect.Type.SHOW_MESSAGE) {
                    ViewUtils.toast(requireActivity(), effect.getMessage());
                    viewModel.clearCheckoutStatus();
                } else if (effect.getType() == TransactionUiEffect.Type.NAVIGATE_TO_RECEIPT) {
                    ViewUtils.toast(requireActivity(), effect.getMessage());
                    clearInputFields();
                    FragmentTransaction ft = requireActivity().getSupportFragmentManager().beginTransaction();
                    ft.replace(R.id.fragment_container, StrukFragment.newInstance(effect.getTransactionId(), false))
                            .addToBackStack(null)
                            .commit();
                    viewModel.clearCheckoutStatus();
                }
            }
        });

        etSearchProduct.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s == null ? "" : s.toString();
                pendingSearchQuery = query;
                searchHandler.removeCallbacksAndMessages(null);
                searchHandler.postDelayed(new Runnable() {
                    @Override public void run() {
                        viewModel.searchProducts(pendingSearchQuery);
                    }
                }, SEARCH_DEBOUNCE_MS);
                if (query.trim().isEmpty()) searchDropdownCard.setVisibility(View.GONE);
                else searchDropdownCard.setVisibility(View.VISIBLE);
            }
        });
        etPay.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { viewModel.updatePayText(getPayText()); }
        });
        etDiscount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { viewModel.updateDiscountText(getDiscountText()); }
        });
        btnAddManual.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (viewModel.addManualItem(getManualName(), getManualPrice())) {
                    etManualName.setText("");
                    etManualPrice.setText("");
                }
            }
        });
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                clearInputFields();
                viewModel.clearCart();
            }
        });
        btnFinish.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { viewModel.checkout(getPayText()); }
        });

        viewModel.updatePayText(getPayText());
        viewModel.updateDiscountText(getDiscountText());
    }

    @Override
    public void onDestroyView() {
        searchHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    private void clearInputFields() {
        if (etPay != null) etPay.setText("");
        if (etDiscount != null) etDiscount.setText("");
        if (etManualName != null) etManualName.setText("");
        if (etManualPrice != null) etManualPrice.setText("");
    }

    private void clearSearchInput() {
        if (etSearchProduct == null) return;
        etSearchProduct.setText("");
        etSearchProduct.clearFocus();
        searchDropdownCard.setVisibility(View.GONE);
    }

    private String getPayText() {
        return etPay == null ? "" : String.valueOf(etPay.getText());
    }

    private String getDiscountText() {
        return etDiscount == null ? "" : String.valueOf(etDiscount.getText());
    }

    private String getManualName() {
        return etManualName == null ? "" : String.valueOf(etManualName.getText());
    }

    private String getManualPrice() {
        return etManualPrice == null ? "" : String.valueOf(etManualPrice.getText());
    }

    private boolean isSearchQueryEmpty() {
        return etSearchProduct == null || String.valueOf(etSearchProduct.getText()).trim().isEmpty();
    }

    private void applyPayHintStyle() {
        if (etPay == null) return;
        String hintText = getString(R.string.pay_amount_hint);
        SpannableString styledHint = new SpannableString(hintText);
        int hintSizePx = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                14f,
                requireContext().getResources().getDisplayMetrics()
        ));
        styledHint.setSpan(new AbsoluteSizeSpan(hintSizePx), 0, styledHint.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        etPay.setHint(styledHint);
    }
}