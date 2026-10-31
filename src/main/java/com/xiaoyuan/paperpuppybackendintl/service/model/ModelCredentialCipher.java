package com.xiaoyuan.paperpuppybackendintl.service.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** Server-only AES-256-GCM envelope for user-supplied model API keys. */
@Component
public class ModelCredentialCipher {

    public static final int KEY_VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String encodedKey;
    private final SecureRandom random = new SecureRandom();

    public ModelCredentialCipher(@Value("${app.model-credentials.encryption-key:}") String encodedKey) {
        // Do not decode or log the key during ordinary app startup; BYOK operations fail closed if absent.
        this.encodedKey = encodedKey;
    }

    public Encrypted encrypt(Long userId, String providerId, String apiKey) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(userId, providerId, KEY_VERSION));
            return new Encrypted(cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8)), nonce, KEY_VERSION);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Model credential encryption failed", e);
        }
    }

    public String decrypt(Long userId, String providerId, byte[] ciphertext, byte[] nonce, int keyVersion) {
        if (keyVersion != KEY_VERSION || nonce == null || nonce.length != NONCE_BYTES) {
            throw new IllegalStateException("Unsupported or invalid model credential envelope");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(userId, providerId, keyVersion));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Model credential decryption failed", e);
        }
    }

    private SecretKeySpec requireKey() {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("APP_MODEL_CREDENTIAL_ENCRYPTION_KEY is required for BYOK operations");
        }
        final byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_MODEL_CREDENTIAL_ENCRYPTION_KEY must be base64-encoded", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("APP_MODEL_CREDENTIAL_ENCRYPTION_KEY must decode to 32 bytes");
        }
        return new SecretKeySpec(raw, "AES");
    }

    private byte[] aad(Long userId, String providerId, int version) {
        return ("model-credential:v" + version + ":" + userId + ":" + providerId)
                .getBytes(StandardCharsets.UTF_8);
    }

    public record Encrypted(byte[] ciphertext, byte[] nonce, int keyVersion) {
    }
}
