package com.daspos.feature.transaction;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.repository.ProductRepository;
import com.daspos.repository.TransactionRepository;
import com.daspos.ui.state.ConsumableEvent;
import com.daspos.ui.state.ListUiState;
import com.daspos.ui.state.TransactionScreenState;
import com.daspos.ui.state.TransactionUiEffect;

import java.util.ArrayList;
import java.util.List;

public class TransactionViewModel extends AndroidViewModel {
    private final MutableLiveData<List<Product>> searchResults = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<ListUiState<Product>> searchUiState = new MutableLiveData<>();
    private final MutableLiveData<TransactionScreenState> screenState = new MutableLiveData<>(TransactionScreenState.empty());
    private final MutableLiveData<ConsumableEvent<TransactionUiEffect>> uiEffect = new MutableLiveData<>();

    public TransactionViewModel(@NonNull Application application) {
        super(application);
    }

    public MutableLiveData<List<Product>> getSearchResults() { return searchResults; }
    public MutableLiveData<ListUiState<Product>> getSearchUiState() { return searchUiState; }
    public MutableLiveData<TransactionScreenState> getScreenState() { return screenState; }
    public MutableLiveData<ConsumableEvent<TransactionUiEffect>> getUiEffect() { return uiEffect; }

    public void searchProducts(String query) {
        searchUiState.setValue(ListUiState.loading());
        if (query == null || query.trim().isEmpty()) {
            searchResults.setValue(new ArrayList<>());
            searchUiState.setValue(ListUiState.success(new ArrayList<>()));
            return;
        }
        ProductRepository.searchAsync(getApplication(), query, results -> {
            searchResults.setValue(results);
            if (results == null || results.isEmpty()) {
                searchUiState.setValue(ListUiState.empty("Produk tidak ditemukan"));
            } else {
                searchUiState.setValue(ListUiState.success(results));
            }
        }, throwable -> searchUiState.setValue(ListUiState.error("Gagal mencari produk")));
    }

    public void updateScreenState(List<CartItem> cartItems, String payText) {
        double total = 0;
        if (cartItems != null) {
            for (CartItem item : cartItems) total += item.getSubtotal();
        }

        double pay = 0;
        try {
            pay = Double.parseDouble(payText == null || payText.trim().isEmpty() ? "0" : payText.trim());
        } catch (Exception ignored) { }

        double change = Math.max(0, pay - total);
        TransactionScreenState current = screenState.getValue();
        TransactionScreenState.CheckoutStatus status = current == null ? TransactionScreenState.CheckoutStatus.IDLE : current.getCheckoutStatus();
        String message = current == null ? "" : current.getMessage();
        screenState.setValue(new TransactionScreenState(cartItems == null || cartItems.isEmpty(), total, pay, change, status, message));
    }

    public void checkout(List<CartItem> cartItems, String payText) {
        double total = 0;
        if (cartItems != null) {
            for (CartItem item : cartItems) total += item.getSubtotal();
        }

        double pay = 0;
        try {
            pay = Double.parseDouble(payText == null || payText.trim().isEmpty() ? "0" : payText.trim());
        } catch (Exception ignored) { }

        double change = Math.max(0, pay - total);
        TransactionScreenState base = TransactionScreenState.of(cartItems == null || cartItems.isEmpty(), total, pay, change);

        if (cartItems == null || cartItems.isEmpty()) {
            screenState.setValue(base.withCheckout(TransactionScreenState.CheckoutStatus.ERROR, "Keranjang masih kosong"));
            uiEffect.setValue(new ConsumableEvent<>(TransactionUiEffect.showMessage("Keranjang masih kosong")));
            return;
        }

        ProductRepository.hasEnoughStockAsync(getApplication(), cartItems, hasEnoughStock -> {
            if (!hasEnoughStock) {
                screenState.setValue(base.withCheckout(TransactionScreenState.CheckoutStatus.ERROR, "Stok tidak mencukupi"));
                uiEffect.setValue(new ConsumableEvent<>(TransactionUiEffect.showMessage("Stok tidak mencukupi")));
                return;
            }

            if (pay < total) {
                screenState.setValue(base.withCheckout(TransactionScreenState.CheckoutStatus.ERROR, "Nominal bayar kurang"));
                uiEffect.setValue(new ConsumableEvent<>(TransactionUiEffect.showMessage("Nominal bayar kurang")));
                return;
            }

            screenState.setValue(base.withCheckout(TransactionScreenState.CheckoutStatus.PROCESSING, "Memproses transaksi"));
            TransactionRepository.saveAsync(getApplication(), new ArrayList<>(cartItems), total, pay, change, transactionId -> {
                screenState.setValue(TransactionScreenState.empty().withCheckout(TransactionScreenState.CheckoutStatus.SUCCESS, "Transaksi berhasil disimpan"));
                uiEffect.setValue(new ConsumableEvent<>(TransactionUiEffect.navigateToReceipt("Transaksi berhasil disimpan", transactionId)));
            }, throwable -> {
                screenState.setValue(base.withCheckout(TransactionScreenState.CheckoutStatus.ERROR, "Gagal menyimpan transaksi"));
                uiEffect.setValue(new ConsumableEvent<>(TransactionUiEffect.showMessage("Gagal menyimpan transaksi")));
            });
        }, throwable -> {
            screenState.setValue(base.withCheckout(TransactionScreenState.CheckoutStatus.ERROR, "Gagal cek stok"));
            uiEffect.setValue(new ConsumableEvent<>(TransactionUiEffect.showMessage("Gagal cek stok")));
        });
    }

    public void clearCheckoutStatus() {
        TransactionScreenState current = screenState.getValue();
        if (current == null) {
            screenState.setValue(TransactionScreenState.empty());
        } else {
            screenState.setValue(TransactionScreenState.of(current.isCartEmpty(), current.getTotal(), current.getPay(), current.getChange()));
        }
    }
}
