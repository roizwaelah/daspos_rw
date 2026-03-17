package com.daspos.shared.util;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
    private static final String KDF_ALGO = "PBKDF2WithHmacSHA256";
    private static final String HASH_PREFIX = "pbkdf2_sha256";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;

    private PasswordHasher() {}

    public static String hash(String rawPassword) {
        try {
            byte[] salt = new byte[SALT_LENGTH_BYTES];
            new SecureRandom().nextBytes(salt);
            byte[] derived = derive(rawPassword, salt, ITERATIONS, KEY_LENGTH_BITS);
            return HASH_PREFIX
                    + "$" + ITERATIONS
                    + "$" + base64Encode(salt)
                    + "$" + base64Encode(derived);
        } catch (Exception e) {
            throw new IllegalStateException("Tidak bisa melakukan hash password", e);
        }
    }

    public static boolean verify(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isEmpty()) return false;
        if (isKdfHash(storedHash)) {
            return verifyKdf(rawPassword, storedHash);
        }
        return sha256Hex(rawPassword).equals(storedHash);
    }

    public static boolean needsRehash(String storedHash) {
        if (storedHash == null || storedHash.isEmpty()) return true;
        if (!isKdfHash(storedHash)) return true;
        try {
            String[] parts = storedHash.split("\\$");
            int iteration = Integer.parseInt(parts[1]);
            return iteration < ITERATIONS;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean verifyKdf(String rawPassword, String storedHash) {
        try {
            String[] parts = storedHash.split("\\$");
            if (parts.length != 4 || !HASH_PREFIX.equals(parts[0])) return false;
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = base64Decode(parts[2]);
            byte[] expected = base64Decode(parts[3]);
            byte[] actual = derive(rawPassword, salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(actual, expected);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isKdfHash(String storedHash) {
        return storedHash.startsWith(HASH_PREFIX + "$");
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int lengthBits) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, lengthBits);
        SecretKeyFactory skf = SecretKeyFactory.getInstance(KDF_ALGO);
        return skf.generateSecret(spec).getEncoded();
    }

    private static String sha256Hex(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Tidak bisa melakukan hash password", e);
        }
    }

    private static String base64Encode(byte[] value) {
        return Base64.encodeToString(value, Base64.NO_WRAP);
    }

    private static byte[] base64Decode(String value) {
        return Base64.decode(value, Base64.NO_WRAP);
    }
}
