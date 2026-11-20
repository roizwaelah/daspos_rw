package com.daspos.supabase;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SupabaseConfigTest {
    @Test
    public void httpsUrlsAreAllowed() {
        assertTrue(SupabaseConfig.isAllowedRemoteUrl("https://abc.supabase.co"));
        assertTrue(SupabaseConfig.isAllowedRemoteUrl("https://example.com:8443/path"));
    }

    @Test
    public void httpRemoteUrlsAreRejected() {
        assertFalse(SupabaseConfig.isAllowedRemoteUrl("http://evil.example.com"));
        assertFalse(SupabaseConfig.isAllowedRemoteUrl("http://supabase.co:8080"));
    }

    @Test
    public void loopbackHttpIsAllowedForDevelopment() {
        assertTrue(SupabaseConfig.isAllowedRemoteUrl("http://localhost:54321"));
        assertTrue(SupabaseConfig.isAllowedRemoteUrl("http://127.0.0.1:8000"));
    }

    @Test
    public void emptyAndGarbageUrlsAreRejected() {
        assertFalse(SupabaseConfig.isAllowedRemoteUrl(""));
        assertFalse(SupabaseConfig.isAllowedRemoteUrl("   "));
        assertFalse(SupabaseConfig.isAllowedRemoteUrl("supabase.co"));
        assertFalse(SupabaseConfig.isAllowedRemoteUrl("ftp://example.com"));
    }
}
