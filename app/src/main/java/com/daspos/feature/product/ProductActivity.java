package com.daspos.feature.product;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.model.Product;
import com.daspos.repository.ProductRepository;
import com.daspos.shared.util.ViewUtils;
import com.daspos.ui.UiStateRenderer;
import com.daspos.ui.state.ListUiState;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class ProductActivity extends BaseActivity {
    private ProductAdapter adapter;
    private EditText etSearch;
    private Spinner spSort;
    private ProductViewModel viewModel;

    @Override protected void onResume() {
        super.onResume();
        reload();
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.manage_products));

        MaterialButton btnAdd = findViewById(R.id.btnAddProduct);
        MaterialButton btnImport = findViewById(R.id.btnImportProduct);
        etSearch = findViewById(R.id.etSearch);
        spSort = findViewById(R.id.spSort);
        final RecyclerView rv = findViewById(R.id.rvProducts);
        final View layoutState = findViewById(R.id.layoutProductState);
        final ProgressBar progress = findViewById(R.id.progressProduct);
        final TextView tvState = findViewById(R.id.tvProductState);

        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(this, R.array.product_sort_options, android.R.layout.simple_spinner_item);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSort.setAdapter(sortAdapter);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter(new ProductAdapter.Listener() {
            @Override public void onEdit(Product product) {
                Intent intent = new Intent(ProductActivity.this, ProductFormActivity.class);
                intent.putExtra("product_id", product.getId());
                startActivity(intent);
            }

            @Override public void onDelete(final Product product) {
                new AlertDialog.Builder(ProductActivity.this)
                        .setTitle("Hapus produk")
                        .setMessage("Yakin ingin menghapus " + product.getName() + "?")
                        .setPositiveButton("Ya", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                ProductRepository.delete(ProductActivity.this, product.getId());
                                reload();
                            }
                        })
                        .setNegativeButton("Batal", null)
                        .show();
            }
        });
        rv.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(ProductViewModel.class);
        viewModel.getProductsLiveData().observe(this, new Observer<List<Product>>() {
            @Override public void onChanged(List<Product> products) { adapter.submit(products); }
        });
        viewModel.getProductsUiState().observe(this, new Observer<ListUiState<Product>>() {
            @Override public void onChanged(ListUiState<Product> state) {
                UiStateRenderer.renderListState(state, rv, layoutState, progress, tvState, getString(R.string.loading));
            }
        });

        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(ProductActivity.this, ProductFormActivity.class)); }
        });
        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(ProductActivity.this, ImportProductActivity.class)); }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { reload(); }
        });
        spSort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) { reload(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        reload();
    }

    private void reload() {
        viewModel.loadProducts(etSearch == null ? "" : String.valueOf(etSearch.getText()), spSort == null ? 0 : spSort.getSelectedItemPosition());
    }
}
