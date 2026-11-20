package com.daspos.feature.auth;

import android.app.Activity;

import androidx.fragment.app.Fragment;

import com.daspos.R;
import com.daspos.shared.util.ViewUtils;

public final class MenuAccessGuard {
    private MenuAccessGuard() {
    }

    public static boolean ensureFragmentAccess(Fragment fragment, String menuKey) {
        if (fragment == null || fragment.getContext() == null) return false;
        String role = AuthSessionStore.getRole(fragment.requireContext());
        if ("admin".equalsIgnoreCase(role) || "owner".equalsIgnoreCase(role)) return true;

        String username = AuthSessionStore.getUsername(fragment.requireContext());
        boolean allowed = MenuAccessStore.hasAccess(fragment.requireContext(), username, role, menuKey);
        if (!allowed) {
            ViewUtils.toast(fragment.requireActivity(), fragment.getString(R.string.menu_access_denied));
            if (fragment.getParentFragmentManager().getBackStackEntryCount() > 0) {
                fragment.getParentFragmentManager().popBackStack();
            } else {
                fragment.requireActivity().finish();
            }
        }
        return allowed;
    }

    public static boolean ensureAccess(Activity activity, String menuKey) {
        if (activity == null) return false;
        String role = AuthSessionStore.getRole(activity);
        if ("admin".equalsIgnoreCase(role) || "owner".equalsIgnoreCase(role)) return true;

        String username = AuthSessionStore.getUsername(activity);
        boolean allowed = MenuAccessStore.hasAccess(activity, username, role, menuKey);
        if (!allowed) {
            ViewUtils.toast(activity, activity.getString(R.string.menu_access_denied));
            activity.finish();
        }
        return allowed;
    }
}
