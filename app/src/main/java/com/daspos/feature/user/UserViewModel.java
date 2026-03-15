package com.daspos.feature.user;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.daspos.model.User;
import com.daspos.repository.UserRepository;
import com.daspos.shared.util.FormValidator;
import com.daspos.ui.state.ConsumableEvent;
import com.daspos.ui.state.FormUiEffect;
import com.daspos.ui.state.ListUiState;
import com.daspos.ui.state.ValidationState;

import java.util.ArrayList;
import java.util.List;

public class UserViewModel extends AndroidViewModel {
    private final MutableLiveData<List<User>> usersLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<ListUiState<User>> usersUiState = new MutableLiveData<>();
    private final MutableLiveData<ValidationState> validationState = new MutableLiveData<>(ValidationState.empty());
    private final MutableLiveData<ConsumableEvent<FormUiEffect>> uiEffect = new MutableLiveData<>();

    public UserViewModel(@NonNull Application application) {
        super(application);
    }

    public MutableLiveData<List<User>> getUsersLiveData() { return usersLiveData; }
    public MutableLiveData<ListUiState<User>> getUsersUiState() { return usersUiState; }
    public MutableLiveData<ValidationState> getValidationState() { return validationState; }
    public MutableLiveData<ConsumableEvent<FormUiEffect>> getUiEffect() { return uiEffect; }

    public void loadUsers() {
        usersUiState.setValue(ListUiState.loading());
        try {
            List<User> users = UserRepository.getAll(getApplication());
            usersLiveData.setValue(users);
            if (users == null || users.isEmpty()) {
                usersUiState.setValue(ListUiState.empty("Belum ada user"));
            } else {
                usersUiState.setValue(ListUiState.success(users));
            }
        } catch (Exception e) {
            usersUiState.setValue(ListUiState.error("Gagal memuat user"));
        }
    }

    public boolean validateNewUser(String username, String password) {
        if (FormValidator.isBlank(username)) {
            validationState.setValue(new ValidationState("USERNAME_REQUIRED", "Username wajib diisi"));
            return false;
        }
        if (FormValidator.isBlank(password)) {
            validationState.setValue(new ValidationState("PASSWORD_REQUIRED", "Password wajib diisi"));
            return false;
        }
        validationState.setValue(ValidationState.empty());
        return true;
    }

    public void addUser(String username, String password, String role) {
        UserRepository.add(getApplication(), username, password, role);
        loadUsers();
        uiEffect.setValue(new ConsumableEvent<>(FormUiEffect.closeScreen("Data berhasil disimpan")));
    }
}
