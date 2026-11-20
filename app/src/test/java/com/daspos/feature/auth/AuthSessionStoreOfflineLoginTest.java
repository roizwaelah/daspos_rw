package com.daspos.feature.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class AuthSessionStoreOfflineLoginTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void supabaseOfflineLoginSucceedsWithCorrectPassword() {
        AuthSessionStore.saveOfflineSupabaseCredential(
                context,
                "kasir@test.dev",
                "rahasia123",
                "kasir",
                "user-1",
                "outlet-1",
                "Toko A",
                "Jl. Test",
                "0812",
                "t@t.dev",
                "access-token",
                "refresh-token"
        );

        AuthSessionStore.OfflineSupabaseSession session =
                AuthSessionStore.tryOfflineSupabaseLogin(context, "kasir@test.dev", "rahasia123");

        assertNotNull(session);
        assertEquals("kasir@test.dev", session.username);
        assertEquals("kasir", session.role);
        assertEquals("user-1", session.userId);
        assertEquals("outlet-1", session.outletId);
        assertEquals("Toko A", session.outletName);
        assertEquals("access-token", session.accessToken);
        assertEquals("refresh-token", session.refreshToken);
    }

    @Test
    public void supabaseOfflineLoginRejectsWrongCredentials() {
        AuthSessionStore.saveOfflineSupabaseCredential(
                context, "a@b.dev", "secret", "kasir", "u1", "o1",
                "", "", "", "", "tok", "ref");

        assertNull(AuthSessionStore.tryOfflineSupabaseLogin(context, "a@b.dev", "wrong"));
        assertNull(AuthSessionStore.tryOfflineSupabaseLogin(context, "lain@b.dev", "secret"));
        assertNull(AuthSessionStore.tryOfflineSupabaseLogin(context, "", "secret"));
    }

    @Test
    public void supabaseOfflineLoginRequiresUserAndOutlet() {
        // Missing userId/outletId must not produce a usable session.
        AuthSessionStore.saveOfflineSupabaseCredential(
                context, "a@b.dev", "secret", "kasir", "", "",
                "", "", "", "", "tok", "ref");

        assertNull(AuthSessionStore.tryOfflineSupabaseLogin(context, "a@b.dev", "secret"));
    }

    @Test
    public void remoteOfflineLoginIsScopedToProvider() {
        AuthSessionStore.saveOfflineRemoteCredential(
                context,
                AuthSessionStore.PROVIDER_FIREBASE,
                "kasir@firebase.dev",
                "pass123",
                "kasir",
                "u-fb",
                "outlet-fb",
                "Toko FB",
                "",
                "",
                "",
                "at",
                "rt",
                Arrays.asList("product", "transaction")
        );

        AuthSessionStore.OfflineRemoteSession session = AuthSessionStore.tryOfflineRemoteLogin(
                context, AuthSessionStore.PROVIDER_FIREBASE, "kasir@firebase.dev", "pass123");
        assertNotNull(session);
        assertEquals("u-fb", session.userId);
        assertEquals("outlet-fb", session.outletId);
        assertTrue(session.allowedMenus.contains("product"));

        // Wrong provider must not match.
        assertNull(AuthSessionStore.tryOfflineRemoteLogin(
                context, AuthSessionStore.PROVIDER_APPWRITE, "kasir@firebase.dev", "pass123"));
        // Wrong password must not match.
        assertNull(AuthSessionStore.tryOfflineRemoteLogin(
                context, AuthSessionStore.PROVIDER_FIREBASE, "kasir@firebase.dev", "nope"));
    }

    @Test
    public void remoteOfflineLoginRejectsSupabaseProvider() {
        // Supabase has its own offline credential path; the generic remote
        // store must refuse it to avoid two competing caches.
        AuthSessionStore.saveOfflineRemoteCredential(
                context,
                AuthSessionStore.PROVIDER_SUPABASE,
                "a@b.dev",
                "pass",
                "admin",
                "u",
                "o",
                "",
                "",
                "",
                "",
                "",
                "",
                Collections.<String>emptyList()
        );

        assertNull(AuthSessionStore.tryOfflineRemoteLogin(
                context, AuthSessionStore.PROVIDER_SUPABASE, "a@b.dev", "pass"));
    }
}
