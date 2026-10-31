package com.xiaoyuan.paperpuppybackendintl.service.model;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModelCredentialCipherTest {

    private ModelCredentialCipher cipher() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return new ModelCredentialCipher(Base64.getEncoder().encodeToString(key));
    }

    @Test
    void roundTripUsesRandomNonceAndBindsUserAndProvider() {
        ModelCredentialCipher cipher = cipher();
        var first = cipher.encrypt(10L, "deepseek", "test-model-key-1234");
        var second = cipher.encrypt(10L, "deepseek", "test-model-key-1234");

        assertEquals("test-model-key-1234", cipher.decrypt(10L, "deepseek",
                first.ciphertext(), first.nonce(), first.keyVersion()));
        assertFalse(java.util.Arrays.equals(first.nonce(), second.nonce()));
        assertFalse(new String(first.ciphertext(), java.nio.charset.StandardCharsets.UTF_8)
                .contains("test-model-key-1234"));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(11L, "deepseek",
                first.ciphertext(), first.nonce(), first.keyVersion()));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(10L, "openai",
                first.ciphertext(), first.nonce(), first.keyVersion()));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(10L, "deepseek",
                first.ciphertext(), first.nonce(), 2));
    }

    @Test
    void missingOrInvalidMasterKeyNeverFallsBackToPlaintext() {
        assertThrows(IllegalStateException.class,
                () -> new ModelCredentialCipher("").encrypt(1L, "openai", "secret"));
        assertThrows(IllegalStateException.class,
                () -> new ModelCredentialCipher("not-base64").encrypt(1L, "openai", "secret"));
        assertThrows(IllegalStateException.class,
                () -> new ModelCredentialCipher(Base64.getEncoder().encodeToString(new byte[16]))
                        .encrypt(1L, "openai", "secret"));
    }

    @Test
    void applicationYamlResolvesTheIdeaInjectedEnvironmentVariable() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String encoded = Base64.getEncoder().encodeToString(key);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "idea-env-file", Map.of("APP_MODEL_CREDENTIAL_ENCRYPTION_KEY", encoded)));
        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }

        String resolved = environment.getProperty("app.model-credentials.encryption-key");
        assertEquals(encoded, resolved);
        ModelCredentialCipher cipher = new ModelCredentialCipher(resolved);
        var encrypted = cipher.encrypt(1L, "deepseek", "example-key");
        assertEquals("example-key", cipher.decrypt(1L, "deepseek",
                encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion()));
    }
}
