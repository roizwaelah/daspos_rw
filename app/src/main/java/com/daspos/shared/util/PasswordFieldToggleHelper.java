package com.daspos.shared.util;

import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.MotionEvent;
import android.widget.EditText;

import androidx.core.content.ContextCompat;

import com.daspos.R;

public final class PasswordFieldToggleHelper {
    private PasswordFieldToggleHelper() {
    }

    public static void attach(final EditText field) {
        if (field == null) return;
        applyHidden(field);

        field.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) return false;
            if (field.getCompoundDrawablesRelative()[2] == null) return false;

            int drawableWidth = field.getCompoundDrawablesRelative()[2].getBounds().width();
            int touchAreaStart = field.getWidth() - field.getPaddingEnd() - drawableWidth;
            if (event.getX() < touchAreaStart) return false;

            boolean currentlyHidden = field.getTransformationMethod() instanceof PasswordTransformationMethod;
            if (currentlyHidden) {
                applyVisible(field);
            } else {
                applyHidden(field);
            }
            field.setSelection(field.getText() == null ? 0 : field.getText().length());
            return true;
        });
    }

    private static void applyHidden(EditText field) {
        field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        field.setCompoundDrawablesRelativeWithIntrinsicBounds(
                null,
                null,
                ContextCompat.getDrawable(field.getContext(), R.drawable.ic_eye),
                null
        );
    }

    private static void applyVisible(EditText field) {
        field.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
        field.setCompoundDrawablesRelativeWithIntrinsicBounds(
                null,
                null,
                ContextCompat.getDrawable(field.getContext(), R.drawable.ic_visibility_off),
                null
        );
    }
}
