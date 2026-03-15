package com.daspos.viewmodel;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class ViewModelFactoryHelper {
    public static <T extends ViewModel> T get(ComponentActivity activity, Class<T> clazz) {
        return new ViewModelProvider(activity, ViewModelProvider.AndroidViewModelFactory.getInstance(activity.getApplication()))
                .get(clazz);
    }
}
