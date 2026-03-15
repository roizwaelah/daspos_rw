package com.daspos.feature.user;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.daspos.model.User;
import com.daspos.repository.UserRepository;
import com.daspos.ui.state.ConsumableEvent;
import com.daspos.ui.state.FormUiEffect;

public class EditUserViewModel extends AndroidViewModel {
    private final MutableLiveData<User> userLiveData = new MutableLiveData<>();
    private final MutableLiveData<ConsumableEvent<FormUiEffect>> uiEffect = new MutableLiveData<>();

    public EditUserViewModel(@NonNull Application application) {
        super(application);
    }

    public MutableLiveData<User> getUserLiveData() { return userLiveData; }
    public MutableLiveData<ConsumableEvent<FormUiEffect>> getUiEffect() { return uiEffect; }

    public void loadUser(String username) {
        userLiveData.setValue(UserRepository.getByUsername(getApplication(), username));
    }

    public void updateRole(String username, String role) {
        UserRepository.updateRole(getApplication(), username, role);
        uiEffect.setValue(new ConsumableEvent<>(FormUiEffect.closeScreen("Data berhasil disimpan")));
    }

    public void deleteUser(String username) {
        UserRepository.delete(getApplication(), username);
        uiEffect.setValue(new ConsumableEvent<>(FormUiEffect.closeScreen("User dihapus")));
    }
}
