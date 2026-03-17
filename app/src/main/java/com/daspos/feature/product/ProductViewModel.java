package com.daspos.feature.product;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.daspos.model.Product;
import com.daspos.repository.ProductRepository;
import com.daspos.ui.state.ListUiState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ProductViewModel extends AndroidViewModel {
    private final MutableLiveData<List<Product>> productsLiveData = new MutableLiveData<>(new ArrayList<Product>());
    private final MutableLiveData<ListUiState<Product>> productsUiState = new MutableLiveData<ListUiState<Product>>();

    public ProductViewModel(@NonNull Application application) {
        super(application);
    }

    public MutableLiveData<List<Product>> getProductsLiveData() {
        return productsLiveData;
    }

    public MutableLiveData<ListUiState<Product>> getProductsUiState() {
        return productsUiState;
    }

    public void loadProducts(String query, int sortPosition) {
        productsUiState.setValue(ListUiState.<Product>loading());
        ProductRepository.getAllAsync(getApplication(), products -> {
            List<Product> filtered = new ArrayList<Product>();

            String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            for (Product p : products) {
                if (q.isEmpty() || p.getName().toLowerCase(Locale.ROOT).contains(q)) {
                    filtered.add(p);
                }
            }

            sortProducts(filtered, sortPosition);

            productsLiveData.setValue(filtered);
            if (filtered.isEmpty()) {
                productsUiState.setValue(ListUiState.<Product>empty("Produk tidak ditemukan"));
            } else {
                productsUiState.setValue(ListUiState.success(filtered));
            }
        }, throwable -> {
            productsUiState.setValue(ListUiState.<Product>error("Gagal memuat produk"));
        });
    }

    private void sortProducts(List<Product> filtered, int sortPosition) {
        if (sortPosition == 0) {
            Collections.sort(filtered, new Comparator<Product>() {
                @Override
                public int compare(Product a, Product b) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
        } else if (sortPosition == 1) {
            Collections.sort(filtered, new Comparator<Product>() {
                @Override
                public int compare(Product a, Product b) {
                    return b.getName().compareToIgnoreCase(a.getName());
                }
            });
        } else if (sortPosition == 2) {
            Collections.sort(filtered, new Comparator<Product>() {
                @Override
                public int compare(Product a, Product b) {
                    return Double.compare(a.getPrice(), b.getPrice());
                }
            });
        } else if (sortPosition == 3) {
            Collections.sort(filtered, new Comparator<Product>() {
                @Override
                public int compare(Product a, Product b) {
                    return Double.compare(b.getPrice(), a.getPrice());
                }
            });
        } else if (sortPosition == 4) {
            Collections.sort(filtered, new Comparator<Product>() {
                @Override
                public int compare(Product a, Product b) {
                    return Integer.compare(b.getStock(), a.getStock());
                }
            });
        }
    }
}
