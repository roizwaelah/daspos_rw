package com.daspos.feature.product;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.core.app.MainActivity;
import com.daspos.feature.auth.MenuAccessGuard;
import com.daspos.feature.auth.MenuAccessStore;
import com.daspos.model.Product;
import com.daspos.repository.ProductRepository;
import com.daspos.shared.util.NotificationDialogHelper;
import com.daspos.shared.util.ViewUtils;
import com.daspos.ui.UiStateRenderer;
import com.daspos.ui.state.ListUiState;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class ProductFragment extends Fragment {
    private static final long SEARCH_DEBOUNCE_MS = 250L;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private ProductAdapter adapter;
    private EditText etSearch;
    private Spinner spSort;
    private ProductViewModel viewModel;
    private String lastSearchQuery = "";
    private int lastSortPosition = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!MenuAccessGuard.ensureFragmentAccess(this, MenuAccessStore.MENU_PRODUCT)) return;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_product, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        Toolbar toolbar = root.findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.manage_products));

        MaterialButton btnAdd = root.findViewById(R.id.btnAddProduct);
        MaterialButton btnImport = root.findViewById(R.id.btnImportProduct);
        etSearch = root.findViewById(R.id.etSearch);
        spSort = root.findViewById(R.id.spSort);
        final RecyclerView rv = root.findViewById(R.id.rvProducts);
        final View layoutState = root.findViewById(R.id.layoutProductState);
        final ProgressBar progress = root.findViewById(R.id.progressProduct);
        final TextView tvState = root.findViewById(R.id.tvProductState);

        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.product_sort_options, android.R.layout.simple_spinner_item);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSort.setAdapter(sortAdapter);
        etSearch.setText(lastSearchQuery);
        spSort.setSelection(lastSortPosition);

        final LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        rv.setLayoutManager(layoutManager);
        adapter = new ProductAdapter(new ProductAdapter.Listener() {
            @Override public void onEdit(Product product) {
                ((MainActivity) ProductFragment.this.requireActivity()).navigateTo(ProductFormFragment.newInstance(product.getId()));
            }

            @Override public void onDelete(final Product product) {
                NotificationDialogHelper.showWarningConfirmation(
                        ProductFragment.this.requireActivity(),
                        ProductFragment.this.getString(R.string.delete_product_title),
                        ProductFragment.this.getString(R.string.delete_product_message, product.getName()),
                        ProductFragment.this.getString(R.string.delete),
                        () -> ProductRepository.deleteAsync(ProductFragment.this.requireContext(), product.getId(),
                                () -> ProductFragment.this.reload(),
                                error -> ViewUtils.toast(ProductFragment.this.requireActivity(), ProductFragment.this.getString(R.string.delete_product_failed)))
                );
            }
        });
        rv.setAdapter(adapter);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0) return;
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                if (firstVisibleItemPosition >= 0 && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 10) {
                    viewModel.loadNextPage();
                }
            }
        });

        viewModel = new ViewModelProvider(requireActivity()).get(ProductViewModel.class);
        viewModel.getProductsLiveData().observe(getViewLifecycleOwner(), new Observer<List<Product>>() {
            @Override public void onChanged(List<Product> products) { adapter.submit(products); }
        });
        viewModel.getProductsUiState().observe(getViewLifecycleOwner(), new Observer<ListUiState<Product>>() {
            @Override public void onChanged(ListUiState<Product> state) {
                UiStateRenderer.renderListState(state, rv, layoutState, progress, tvState, getString(R.string.loading));
            }
        });

        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ((MainActivity) ProductFragment.this.requireActivity()).navigateTo(ProductFormFragment.newInstance(null)); }
        });
        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ((MainActivity) ProductFragment.this.requireActivity()).navigateTo(new ImportProductFragment()); }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacksAndMessages(null);
                searchHandler.postDelayed(new Runnable() {
                    @Override public void run() { ProductFragment.this.reload(); }
                }, SEARCH_DEBOUNCE_MS);
            }
        });
        spSort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) { ProductFragment.this.reload(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        reload();
    }

    @Override
    public void onDestroyView() {
        lastSearchQuery = etSearch == null ? "" : String.valueOf(etSearch.getText());
        lastSortPosition = spSort == null ? 0 : spSort.getSelectedItemPosition();
        searchHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    private void reload() {
        if (viewModel == null) return;
        viewModel.loadProducts(etSearch == null ? "" : String.valueOf(etSearch.getText()), spSort == null ? 0 : spSort.getSelectedItemPosition());
    }
}
