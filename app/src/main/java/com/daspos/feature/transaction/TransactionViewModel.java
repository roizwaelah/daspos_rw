package com.daspos.feature.transaction;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.daspos.R;
import com.daspos.model.CartItem;
import com.daspos.model.Product;
import com.daspos.model.SalesUnit;
import com.daspos.repository.ProductRepository;
import com.daspos.repository.TransactionRepository;
import com.daspos.ui.state.ConsumableEvent;
import com.daspos.ui.state.ListUiState;
import com.daspos.ui.state.TransactionScreenState;
import com.daspos.ui.state.TransactionUiEffect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TransactionViewModel extends AndroidViewModel {
    private static final double MAX_TRANSACTION_AMOUNT = 999_999_999D;
    private static final double MAX_MANUAL_ITEM_PRICE = 999_999_999D;

    private final MutableLiveData<List<Product>> searchResults = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<ListUiState<Product>> searchUiState = new MutableLiveData<>();
    private final MutableLiveData<List<CartItem>> cartItems = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<TransactionScreenState> screenState = new MutableLiveData<>(TransactionScreenState.empty());
    private final MutableLiveData<ConsumableEvent<TransactionUiEffect>> uiEffect = new MutableLiveData<>();
    private final Set<String> pendingStockChecks = new HashSet<>();
    private String currentPayText = "";
    private String currentDiscountText = "";

    public TransactionViewModel(@NonNull Application application) {
        super(application);
    }

    public MutableLiveData<List<Product>> getSearchResults() { return searchResults; }
    public MutableLiveData<ListUiState<Product>> getSearchUiState() { return searchUiState; }
    public MutableLiveData<List<CartItem>> getCartItems() { return cartItems; }
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

    public void updatePayText(String payText) {
        currentPayText = payText == null ? "" : payText;
        refreshScreenState();
    }

    public void updateDiscountText(String discountText) {
        currentDiscountText = discountText == null ? "" : discountText;
        refreshScreenState();
    }

    private void refreshScreenState() {
        updateScreenState(getCurrentCartItems(), currentPayText, currentDiscountText);
    }

    public void updateScreenState(List<CartItem> items, String payText, String discountText) {
        currentPayText = payText == null ? "" : payText;
        currentDiscountText = discountText == null ? "" : discountText;
        double subtotal = 0;
        if (items != null) {
            for (CartItem item : items) subtotal += item.getSubtotal();
        }

        double discount = parseAmount(currentDiscountText);
        if (discount > subtotal) discount = subtotal;
        double total = Math.max(0, subtotal - discount);
        double pay = parseAmount(currentPayText);
        double change = Math.max(0, pay - total);
        TransactionScreenState current = screenState.getValue();
        TransactionScreenState.CheckoutStatus status = current == null ? TransactionScreenState.CheckoutStatus.IDLE : current.getCheckoutStatus();
        String message = current == null ? "" : current.getMessage();
        screenState.setValue(new TransactionScreenState(items == null || items.isEmpty(), total, discount, pay, change, status, message));
    }

    public void clearCart() {
        currentPayText = "";
        currentDiscountText = "";
        cartItems.setValue(new ArrayList<>());
        updateScreenState(new ArrayList<>(), "", "");
    }

    public void addToCart(Product product, Runnable onAddedSuccess) {
        if (product == null || product.getId() == null) return;
        final String productId = product.getId();
        if (productId.trim().isEmpty() || isStockCheckPending(productId)) return;

        markStockCheckPending(productId, true);
        ProductRepository.getByIdAsync(getApplication(), productId, latest -> {
            markStockCheckPending(productId, false);
            if (latest == null) {
                showMessage(getApplication().getString(R.string.product_out_of_stock));
                return;
            }
            if (latest.isTrackStock() && latest.getStock() <= 0) {
                showMessage(getApplication().getString(R.string.product_out_of_stock));
                return;
            }

            List<CartItem> current = getCurrentCartItems();
            int existingIndex = findProductRowIndex(current, productId);
            CartItem existing = existingIndex >= 0 ? current.get(existingIndex) : null;
            String unitCode = existing == null ? SalesUnit.ECER : existing.getUnitCode();
            int unitFactor = existing == null ? 1 : Math.max(1, latest.getUnitFactor(unitCode));
            int newQty = existing == null ? 1 : existing.getQty() + 1;
            int requestedBaseQty = requestedBaseQtyForProduct(current, productId, existing)
                    + newQty * unitFactor;
            if (latest.isTrackStock() && requestedBaseQty > latest.getStock()) {
                showMessage(getApplication().getString(R.string.stock_not_enough));
                return;
            }

            CartItem updated = new CartItem(latest, newQty, unitCode, unitFactor, latest.getUnitPrice(unitCode));
            if (existing == null) {
                current.add(updated);
            } else {
                current.set(existingIndex, updated);
            }
            emitCart(current);
            if (onAddedSuccess != null) onAddedSuccess.run();
        }, error -> {
            markStockCheckPending(productId, false);
            showMessage(getApplication().getString(R.string.product_out_of_stock));
        });
    }

    public boolean addManualItem(String rawName, String rawPriceText) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            showMessage(getApplication().getString(R.string.manual_item_name_required));
            return false;
        }
        double price = parseRawAmount(rawPriceText);
        if (price <= 0 || price > MAX_MANUAL_ITEM_PRICE) {
            showMessage(getApplication().getString(R.string.manual_item_price_required));
            return false;
        }

        Product manual = new Product("", name, "", price, 0, 0, "pcs", false,
                price, price, price, price, 1, 1, 1);
        List<CartItem> current = getCurrentCartItems();
        current.add(new CartItem(manual, 1));
        emitCart(current);
        refreshScreenState();
        return true;
    }

    public void decrementCartItem(CartItem item) {
        List<CartItem> current = getCurrentCartItems();
        int index = findRowIndex(current, item);
        if (index < 0) return;
        CartItem row = current.get(index);
        if (row.getQty() <= 1) {
            current.remove(index);
        } else {
            current.set(index, copyWithQty(row, row.getQty() - 1, row.getProduct()));
        }
        emitCart(current);
        refreshScreenState();
    }

    public void incrementCartItem(CartItem item) {
        if (item == null || item.getProduct() == null) return;
        Product product = item.getProduct();
        final String productId = product.getId();
        if (productId == null || productId.trim().isEmpty() || !product.isTrackStock()) {
            applyQtyIncrement(item, product);
            return;
        }
        if (isStockCheckPending(productId)) return;

        markStockCheckPending(productId, true);
        ProductRepository.getByIdAsync(getApplication(), productId, latest -> {
            markStockCheckPending(productId, false);
            List<CartItem> current = getCurrentCartItems();
            int index = findRowIndex(current, item);
            if (index < 0) return;
            CartItem row = current.get(index);
            int requestedBaseQty = requestedBaseQtyForProduct(current, productId, row)
                    + row.getBaseQty()
                    + row.getUnitFactorToBase();
            if (latest == null || requestedBaseQty > latest.getStock()) {
                showMessage(getApplication().getString(R.string.stock_not_enough));
                return;
            }
            current.set(index, copyWithQty(row, row.getQty() + 1, latest));
            emitCart(current);
            refreshScreenState();
        }, error -> {
            markStockCheckPending(productId, false);
            showMessage(getApplication().getString(R.string.stock_not_enough));
        });
    }

    private void applyQtyIncrement(CartItem item, Product product) {
        List<CartItem> current = getCurrentCartItems();
        int index = findRowIndex(current, item);
        if (index < 0) return;
        CartItem row = current.get(index);
        current.set(index, copyWithQty(row, row.getQty() + 1, product));
        emitCart(current);
        refreshScreenState();
    }

    public void changeCartItemUnit(CartItem item, String unitCode) {
        if (item == null || item.getProduct() == null) return;
        List<CartItem> items = getCurrentCartItems();
        int index = findRowIndex(items, item);
        if (index < 0) return;
        CartItem row = items.get(index);
        Product product = row.getProduct();
        final String productId = product.getId();

        if (productId == null || productId.trim().isEmpty() || !product.isTrackStock()) {
            row.applyUnitFromProduct(SalesUnit.ECER);
            emitCart(items);
            refreshScreenState();
            return;
        }
        if (isStockCheckPending(productId)) return;

        markStockCheckPending(productId, true);
        ProductRepository.getByIdAsync(getApplication(), productId, latest -> {
            markStockCheckPending(productId, false);
            if (latest == null) return;

            product.setPriceEcer(latest.getPriceEcer());
            product.setPriceRenteng(latest.getPriceRenteng());
            product.setPricePak(latest.getPricePak());
            product.setPriceKarton(latest.getPriceKarton());
            product.setFactorRenteng(latest.getFactorRenteng());
            product.setFactorPak(latest.getFactorPak());
            product.setFactorKarton(latest.getFactorKarton());

            List<CartItem> current = getCurrentCartItems();
            int rowIndex = findRowIndex(current, row);
            if (rowIndex < 0) return;
            CartItem target = current.get(rowIndex);

            if (!latest.isTierPricingEnabled()) {
                target.applyUnitFromProduct(SalesUnit.ECER);
                emitCart(current);
                refreshScreenState();
                return;
            }

            target.applyUnitFromProduct(unitCode);
            int factor = target.getUnitFactorToBase();
            int othersBaseQty = requestedBaseQtyForProduct(current, productId, target);
            int maxQty = factor <= 0
                    ? target.getQty()
                    : (latest.getStock() - othersBaseQty) / factor;
            if (maxQty <= 0) {
                target.setQty(1);
                if (target.getBaseQty() > latest.getStock()) {
                    target.applyUnitFromProduct(SalesUnit.ECER);
                    target.setQty(1);
                }
                showMessage(getApplication().getString(R.string.stock_not_enough));
            } else if (target.getQty() > maxQty) {
                target.setQty(maxQty);
                showMessage(getApplication().getString(R.string.stock_not_enough));
            }
            emitCart(current);
            refreshScreenState();
        }, error -> markStockCheckPending(productId, false));
    }

    public void checkout(String payText) {
        TransactionScreenState current = screenState.getValue();
        if (current != null && current.getCheckoutStatus() == TransactionScreenState.CheckoutStatus.PROCESSING) {
            return;
        }

        List<CartItem> itemsSnapshot = getCurrentCartItems();
        double subtotal = 0;
        for (CartItem item : itemsSnapshot) subtotal += item.getSubtotal();

        double discount = parseAmount(currentDiscountText);
        if (discount > subtotal) discount = subtotal;
        double total = Math.max(0, subtotal - discount);
        double rawPay = parseRawAmount(payText);
        if (rawPay > MAX_TRANSACTION_AMOUNT) {
            String maxLabel = com.daspos.shared.util.CurrencyUtils.formatRupiah(MAX_TRANSACTION_AMOUNT);
            String limitMsg = "Nominal bayar melebihi batas maksimum (" + maxLabel + ")";
            TransactionScreenState overLimit = TransactionScreenState.of(itemsSnapshot.isEmpty(), total, discount, 0, 0);
            screenState.setValue(overLimit.withCheckout(TransactionScreenState.CheckoutStatus.ERROR, limitMsg));
            uiEffect.setValue(new ConsumableEvent<>(TransactionUiEffect.showMessage(limitMsg)));
            return;
        }

        double pay = parseAmount(payText);
        double change = Math.max(0, pay - total);
        TransactionScreenState base = TransactionScreenState.of(itemsSnapshot.isEmpty(), total, discount, pay, change);
        final double finalTotal = total;
        final double finalDiscount = discount;
        final double finalPay = pay;
        final double finalChange = change;

        if (itemsSnapshot.isEmpty()) {
            screenState.setValue(base.withCheckout(TransactionScreenState.CheckoutStatus.ERROR, "Keranjang masih kosong"));
            uiEffect.setValue(new ConsumableEvent<>(TransactionUiEffect.showMessage("Keranjang masih kosong")));
            return;
        }

        if (finalPay < finalTotal) {
            screenState.setValue(base.withCheckout(TransactionScreenState.CheckoutStatus.ERROR, "Nominal bayar kurang"));
            uiEffect.setValue(new ConsumableEvent<>(TransactionUiEffect.showMessage("Nominal bayar kurang")));
            return;
        }

        // Lock checkout button immediately before async stock check to prevent double-submit.
        screenState.setValue(base.withCheckout(TransactionScreenState.CheckoutStatus.PROCESSING, "Memproses transaksi"));

        ProductRepository.hasEnoughStockAsync(getApplication(), itemsSnapshot, hasEnoughStock -> {
            if (!hasEnoughStock) {
                screenState.setValue(base.withCheckout(TransactionScreenState.CheckoutStatus.ERROR, "Stok tidak mencukupi"));
                uiEffect.setValue(new ConsumableEvent<>(TransactionUiEffect.showMessage(getApplication().getString(R.string.stock_not_enough))));
                return;
            }

            TransactionRepository.saveAsync(getApplication(), itemsSnapshot, finalTotal, finalDiscount, finalPay, finalChange, transactionId -> {
                currentPayText = "";
                currentDiscountText = "";
                cartItems.setValue(new ArrayList<>());
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
            screenState.setValue(TransactionScreenState.of(current.isCartEmpty(), current.getTotal(), current.getDiscount(), current.getPay(), current.getChange()));
        }
    }

    private List<CartItem> getCurrentCartItems() {
        List<CartItem> existing = cartItems.getValue();
        return existing == null ? new ArrayList<>() : new ArrayList<>(existing);
    }

    private void emitCart(List<CartItem> items) {
        cartItems.setValue(new ArrayList<>(items));
    }

    private void showMessage(String message) {
        uiEffect.setValue(new ConsumableEvent<>(TransactionUiEffect.showMessage(message)));
    }

    private static CartItem copyWithQty(CartItem row, int qty, Product productRef) {
        Product product = productRef != null ? productRef : row.getProduct();
        String unitCode = row.getUnitCode();
        return new CartItem(product, qty, unitCode, product.getUnitFactor(unitCode), product.getUnitPrice(unitCode));
    }

    private static int findProductRowIndex(List<CartItem> items, String productId) {
        for (int i = 0; i < items.size(); i++) {
            CartItem row = items.get(i);
            if (row != null && row.getProduct() != null && productId.equals(row.getProduct().getId())) {
                return i;
            }
        }
        return -1;
    }

    private static int findRowIndex(List<CartItem> items, CartItem target) {
        if (target == null) return -1;
        int index = items.indexOf(target);
        if (index >= 0) return index;
        for (int i = 0; i < items.size(); i++) {
            CartItem row = items.get(i);
            if (row == null || row.getProduct() == null || target.getProduct() == null) continue;
            if (row.getProduct() == target.getProduct()
                    && SalesUnit.normalize(row.getUnitCode()).equals(SalesUnit.normalize(target.getUnitCode()))
                    && row.getQty() == target.getQty()) {
                return i;
            }
        }
        return -1;
    }

    private static int requestedBaseQtyForProduct(List<CartItem> items, String productId, CartItem exclude) {
        int total = 0;
        for (CartItem row : items) {
            if (row == null || row.getProduct() == null) continue;
            if (!productId.equals(row.getProduct().getId())) continue;
            if (exclude != null && row == exclude) continue;
            total += Math.max(0, row.getBaseQty());
        }
        return total;
    }

    private boolean isStockCheckPending(String productId) {
        return pendingStockChecks.contains(productId);
    }

    private void markStockCheckPending(String productId, boolean pending) {
        if (pending) {
            pendingStockChecks.add(productId);
        } else {
            pendingStockChecks.remove(productId);
        }
    }

    private double parseRawAmount(String text) {
        double amount = 0;
        try {
            amount = Double.parseDouble(text == null || text.trim().isEmpty() ? "0" : text.trim());
        } catch (Exception ignored) { }
        if (Double.isNaN(amount) || Double.isInfinite(amount)) return 0;
        return Math.max(0, amount);
    }

    private double parseAmount(String text) {
        double amount = parseRawAmount(text);
        if (amount > MAX_TRANSACTION_AMOUNT) return 0;
        return amount;
    }
}