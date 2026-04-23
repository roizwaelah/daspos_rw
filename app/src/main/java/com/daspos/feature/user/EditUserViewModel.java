package com.daspos.feature.user;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.daspos.feature.auth.MenuAccessStore;
import com.daspos.model.User;
import com.daspos.repository.UserRepository;
import com.daspos.ui.state.ConsumableEvent;
import com.daspos.ui.state.FormUiEffect;

import java.util.List;

public class EditUserViewModel extends AndroidViewModel {
    private final MutableLiveData<User> userLiveData = new MutableLiveData<>();
    private final MutableLiveData<ConsumableEvent<FormUiEffect>> uiEffect = new MutableLiveData<>();

    public EditUserViewModel(@NonNull Application application) {
        super(application);
    }

    public MutableLiveData<User> getUserLiveData() { return userLiveData; }
    public MutableLiveData<ConsumableEvent<FormUiEffect>> getUiEffect() { return uiEffect; }

    public void loadUser(String username) {
        UserRepository.getByUsernameAsync(getApplication(), username, userLiveData::setValue,
                throwable -> uiEffect.setValue(new ConsumableEvent<>(FormUiEffect.showMessage("Gagal memuat user"))));
    }

    public void updateRoleAndAccess(String username, String role, List<String> allowedMenus) {
        UserRepository.updateRoleAsync(getApplication(), username, role,
                () -> {
                    MenuAccessStore.saveForUser(getApplication(), username, allowedMenus);
                    uiEffect.setValue(new ConsumableEvent<>(FormUiEffect.closeScreen("Data berhasil disimpan")));
                },
                throwable -> {
                    String msg = throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()
                            ? "Gagal menyimpan data"
                            : throwable.getMessage().trim();
                    uiEffect.setValue(new ConsumableEvent<>(FormUiEffect.showMessage(msg)));
                });
    }

    public void deleteUser(String username) {
        UserRepository.deleteAsync(getApplication(), username,
                () -> {
                    MenuAccessStore.clearForUser(getApplication(), username);
                    uiEffect.setValue(new ConsumableEvent<>(FormUiEffect.closeScreen("User dihapus")));
                },
                throwable -> uiEffect.setValue(new ConsumableEvent<>(FormUiEffect.showMessage("Gagal menghapus user"))));
    }
}
