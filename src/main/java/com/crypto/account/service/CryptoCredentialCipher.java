package com.crypto.account.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * FIX-031: encrypt user exchange credentials before they reach the database.
 * The master key must be supplied outside the database through CRYPTO_ACCOUNT_MASTER_KEY
 * as a Base64-encoded 32-byte AES key. Secrets are never returned to the browser.
 */
@Component
public class CryptoCredentialCipher {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String masterKey;

    public CryptoCredentialCipher(@Value("${crypto.account.master-key:${CRYPTO_ACCOUNT_MASTER_KEY:}}") String masterKey) {
        this.masterKey = masterKey == null ? "" : masterKey.trim();
    }

    public boolean configured() {
        if (masterKey.isBlank()) return false;
        try {
            return Base64.getDecoder().decode(masterKey).length == 32;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) return null;
        if (!configured()) {
            throw new IllegalStateException("CRYPTO_ACCOUNT_MASTER_KEY must be a Base64-encoded 32-byte key before Binance credentials can be saved.");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(masterKey), "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt crypto account credential.", ex);
        }
    }
}
