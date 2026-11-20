package com.daspos.shared.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PasswordHasherTest {
    @Test
    public void verifyAcceptsFreshPbkdfHash() {
        String hash = PasswordHasher.hash("correct horse battery staple");

        assertTrue(PasswordHasher.verify("correct horse battery staple", hash));
        assertFalse(PasswordHasher.verify("wrong password", hash));
        assertFalse(PasswordHasher.needsRehash(hash));
    }

    @Test
    public void verifyAcceptsLegacySha256HashForMigration() {
        String legacyHash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8";

        assertTrue(PasswordHasher.verify("password", legacyHash));
        assertTrue(PasswordHasher.needsRehash(legacyHash));
    }
}
