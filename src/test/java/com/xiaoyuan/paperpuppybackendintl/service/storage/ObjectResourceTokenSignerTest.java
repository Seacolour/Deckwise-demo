package com.xiaoyuan.paperpuppybackendintl.service.storage;

import com.xiaoyuan.paperpuppybackendintl.config.ObjectDeliveryWorkerProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ObjectResourceTokenSignerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-04T00:00:00Z");

    @Test
    void signBuildsVerifiablePrefixScopedToken() {
        ObjectResourceTokenSigner signer = signerAt(FIXED_NOW);

        String token = signer.sign(
                "private",
                "36",
                List.of("dev/pdf-assets/36"),
                List.of(),
                Duration.ofMinutes(10)
        );

        ObjectResourceTokenSigner.Payload payload = signer.verify(token);

        assertEquals("36", payload.subject());
        assertEquals("private", payload.scope());
        assertEquals(List.of("dev/pdf-assets/36/"), payload.prefixes());
        assertEquals(List.of(), payload.objects());
        assertEquals(FIXED_NOW.getEpochSecond(), payload.issuedAt());
        assertEquals(FIXED_NOW.plus(Duration.ofMinutes(10)).getEpochSecond(), payload.expiresAt());
        assertEquals("kid-test", payload.keyId());
    }

    @Test
    void signBuildsObjectScopedTokenWhenPrefixIsUnavailable() {
        ObjectResourceTokenSigner signer = signerAt(FIXED_NOW);

        String token = signer.sign(
                "private",
                null,
                List.of(),
                List.of("image/0d8b94dd.png"),
                Duration.ofMinutes(5)
        );

        ObjectResourceTokenSigner.Payload payload = signer.verify(token);

        assertNull(payload.subject());
        assertEquals(List.of(), payload.prefixes());
        assertEquals(List.of("image/0d8b94dd.png"), payload.objects());
    }

    @Test
    void expiredTokenFailsVerification() {
        ObjectResourceTokenSigner signer = signerAt(FIXED_NOW);
        String token = signer.sign(
                "private",
                "36",
                List.of("dev/pdf-assets/36/"),
                List.of(),
                Duration.ofSeconds(30)
        );

        assertNull(signerAt(FIXED_NOW.plusSeconds(31)).verify(token));
    }

    @Test
    void tamperedTokenFailsVerification() {
        ObjectResourceTokenSigner signer = signerAt(FIXED_NOW);
        String token = signer.sign(
                "private",
                "36",
                List.of("dev/pdf-assets/36/"),
                List.of(),
                Duration.ofMinutes(10)
        );

        assertNull(signer.verify(token.substring(0, token.length() - 2) + "xx"));
    }

    @Test
    void objectKeyToPathEscapesSegments() {
        ObjectResourceTokenSigner signer = signerAt(FIXED_NOW);

        assertEquals("/dev/pdf-assets/36/a%20b.png", signer.objectKeyToPath("dev/pdf-assets/36/a b.png"));
    }

    private ObjectResourceTokenSigner signerAt(Instant instant) {
        ObjectDeliveryWorkerProperties properties = new ObjectDeliveryWorkerProperties();
        properties.setResourceTokenKeyId("kid-test");
        properties.setResourceTokenSecret("unit-test-secret");
        return new ObjectResourceTokenSigner(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }
}
