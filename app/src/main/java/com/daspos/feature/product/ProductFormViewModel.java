package com.daspos.feature.product;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.daspos.model.Product;
import com.daspos.repository.ProductRepository;
import com.daspos.shared.util.FormValidator;
import com.daspos.ui.state.ConsumableEvent;
import com.daspos.ui.state.FormUiEffect;
import com.daspos.ui.state.ValidationState;

public class ProductFormViewModel extends AndroidViewModel {
    private final MutableLiveData<ValidationState> validationState = new MutableLiveData<>(ValidationState.empty());
    private final MutableLiveData<ConsumableEvent<FormUiEffect>> uiEffect = new MutableLiveData<>();

    public ProductFormViewModel(@NonNull Application application) {
        super(application);
    }

    public MutableLiveData<ValidationState> getValidationState() { return validationState; }
    public MutableLiveData<ConsumableEvent<FormUiEffect>> getUiEffect() { return uiEffect; }

    public boolean validate(
            String name,
            String costPrice,
            String stock,
            String unitName,
            String priceEcer,
            String priceRenteng,
            String pricePak,
            String priceKarton,
            String factorRenteng,
            String factorPak,
            String factorKarton,
            boolean useTierPricing,
            boolean trackStock
    ) {
        if (FormValidator.isBlank(name)) {
            validationState.setValue(new ValidationState("NAME_REQUIRED", "Nama wajib diisi"));
            return false;
        }
        if (!FormValidator.isPositiveOrZeroNumber(costPrice)) {
            validationState.setValue(new ValidationState("INVALID_COST_PRICE", "Harga pokok tidak valid"));
            return false;
        }
        if (FormValidator.isBlank(unitName)) {
            validationState.setValue(new ValidationState("UNIT_REQUIRED", "Nama satuan wajib diisi"));
            return false;
        }
        if (trackStock && !FormValidator.isPositiveOrZeroInt(stock)) {
            validationState.setValue(new ValidationState("INVALID_STOCK", "Stok tidak valid"));
            return false;
        }
        if (!FormValidator.isPositiveOrZeroNumber(priceEcer)) {
            validationState.setValue(new ValidationState("INVALID_PRICE", "Harga tidak valid"));
            return false;
        }
        if (!useTierPricing) {
            validationState.setValue(ValidationState.empty());
            return true;
        }
        if (!FormValidator.isPositiveOrZeroNumber(priceRenteng)
                || !FormValidator.isPositiveOrZeroNumber(pricePak)
                || !FormValidator.isPositiveOrZeroNumber(priceKarton)) {
            validationState.setValue(new ValidationState("INVALID_TIER_PRICE", "Harga bertingkat tidak valid"));
            return false;
        }
        if (!isPositiveInt(factorRenteng) || !isPositiveInt(factorPak) || !isPositiveInt(factorKarton)) {
            validationState.setValue(new ValidationState("INVALID_FACTOR", "Konversi unit tidak valid"));
            return false;
        }
        validationState.setValue(ValidationState.empty());
        return true;
    }

    private boolean isPositiveInt(String value) {
        try {
            return Integer.parseInt(value) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void saveNew(
            String name,
            String barcode,
            double costPrice,
            int stock,
            String unitName,
            boolean trackStock,
            double priceEcer,
            double priceRenteng,
            double pricePak,
            double priceKarton,
            int factorRenteng,
            int factorPak,
            int factorKarton
    ) {
        ProductRepository.addAsync(
                getApplication(),
                name,
                barcode,
                costPrice,
                stock,
                unitName,
                trackStock,
                priceEcer,
                priceRenteng,
                pricePak,
                priceKarton,
                factorRenteng,
                factorPak,
                factorKarton,
                () -> uiEffect.setValue(new ConsumableEvent<>(FormUiEffect.closeScreen("Produk berhasil ditambahkan"))),
                throwable -> uiEffect.setValue(new ConsumableEvent<>(FormUiEffect.showMessage(errorMessage(throwable, "Gagal menambah produk")))));
    }

    public void saveEdit(Product product) {
        ProductRepository.updateAsync(getApplication(), product,
                () -> uiEffect.setValue(new ConsumableEvent<>(FormUiEffect.closeScreen("Produk berhasil diperbarui"))),
                throwable -> uiEffect.setValue(new ConsumableEvent<>(FormUiEffect.showMessage(errorMessage(throwable, "Gagal memperbarui produk")))));
    }

    // Surface repository validation messages (e.g. duplicate product name)
    // instead of swallowing them behind a generic failure toast.
    private static String errorMessage(Throwable throwable, String fallback) {
        if (throwable instanceof IllegalArgumentException && throwable.getMessage() != null) {
            return throwable.getMessage();
        }
        return fallback;
    }
}
