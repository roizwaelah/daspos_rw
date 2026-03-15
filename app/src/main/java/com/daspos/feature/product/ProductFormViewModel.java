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

    public boolean validate(String name, String price, String stock) {
        if (FormValidator.isBlank(name)) {
            validationState.setValue(new ValidationState("NAME_REQUIRED", "Nama wajib diisi"));
            return false;
        }
        if (!FormValidator.isPositiveOrZeroNumber(price)) {
            validationState.setValue(new ValidationState("INVALID_PRICE", "Harga tidak valid"));
            return false;
        }
        if (!FormValidator.isPositiveOrZeroInt(stock)) {
            validationState.setValue(new ValidationState("INVALID_STOCK", "Stok tidak valid"));
            return false;
        }
        validationState.setValue(ValidationState.empty());
        return true;
    }

    public void saveNew(String name, double price, int stock) {
        ProductRepository.add(getApplication(), name, price, stock);
        uiEffect.setValue(new ConsumableEvent<>(FormUiEffect.closeScreen("Produk berhasil ditambahkan")));
    }

    public void saveEdit(Product product) {
        ProductRepository.update(getApplication(), product);
        uiEffect.setValue(new ConsumableEvent<>(FormUiEffect.closeScreen("Produk berhasil diperbarui")));
    }
}
