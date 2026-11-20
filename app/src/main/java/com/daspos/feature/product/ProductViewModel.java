package com.daspos.feature.product;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.daspos.model.Product;
import com.daspos.repository.ProductRepository;
import com.daspos.ui.state.ListUiState;

import java.util.ArrayList;
import java.util.List;

public class ProductViewModel extends AndroidViewModel {
    public static final int PAGE_SIZE = 50;

    private final MutableLiveData<List<Product>> productsLiveData = new MutableLiveData<>(new ArrayList<Product>());
    private final MutableLiveData<ListUiState<Product>> productsUiState = new MutableLiveData<ListUiState<Product>>();
    private final List<Product> accumulatedProducts = new ArrayList<>();

    private String currentQuery = "";
    private int currentSortPosition = 0;
    private int currentPage = 0;
    private boolean isLoadingPage = false;
    private boolean hasMorePages = true;
    private int requestGeneration = 0;

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
        currentQuery = query == null ? "" : query.trim();
        currentSortPosition = sortPosition;
        currentPage = 0;
        hasMorePages = true;
        isLoadingPage = true;
        accumulatedProducts.clear();
        final int generation = ++requestGeneration;

        productsUiState.setValue(ListUiState.<Product>loading());
        fetchPage(0, generation, true);
    }

    public void loadNextPage() {
        if (isLoadingPage || !hasMorePages) return;
        isLoadingPage = true;
        final int nextPage = currentPage + 1;
        final int generation = requestGeneration;
        fetchPage(nextPage, generation, false);
    }

    private void fetchPage(final int page, final int generation, final boolean isFirstPage) {
        final int offset = page * PAGE_SIZE;
        if (currentQuery.isEmpty()) {
            ProductRepository.getAllSortedPagedAsync(getApplication(), currentSortPosition, PAGE_SIZE, offset, products -> {
                onPageLoaded(page, generation, isFirstPage, products);
            }, throwable -> {
                onPageFailed(generation, isFirstPage);
            });
        } else {
            ProductRepository.searchProductsSortedPagedAsync(getApplication(), currentQuery, currentSortPosition, PAGE_SIZE, offset, products -> {
                onPageLoaded(page, generation, isFirstPage, products);
            }, throwable -> {
                onPageFailed(generation, isFirstPage);
            });
        }
    }

    private void onPageLoaded(int page, int generation, boolean isFirstPage, List<Product> products) {
        if (generation != requestGeneration) return;
        isLoadingPage = false;
        currentPage = page;
        List<Product> safeProducts = products == null ? new ArrayList<Product>() : products;
        hasMorePages = safeProducts.size() >= PAGE_SIZE;

        if (isFirstPage) {
            accumulatedProducts.clear();
        }
        accumulatedProducts.addAll(safeProducts);

        List<Product> snapshot = new ArrayList<>(accumulatedProducts);
        productsLiveData.setValue(snapshot);
        if (snapshot.isEmpty()) {
            productsUiState.setValue(ListUiState.<Product>empty("Produk tidak ditemukan"));
        } else {
            productsUiState.setValue(ListUiState.success(snapshot));
        }
    }

    private void onPageFailed(int generation, boolean isFirstPage) {
        if (generation != requestGeneration) return;
        isLoadingPage = false;
        if (isFirstPage) {
            productsUiState.setValue(ListUiState.<Product>error("Gagal memuat produk"));
        }
    }
}
