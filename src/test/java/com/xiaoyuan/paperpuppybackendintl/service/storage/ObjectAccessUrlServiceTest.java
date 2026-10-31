package com.xiaoyuan.paperpuppybackendintl.service.storage;

import com.xiaoyuan.paperpuppybackendintl.config.ObjectDeliveryWorkerProperties;
import com.xiaoyuan.paperpuppybackendintl.config.StorageR2Properties;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObjectAccessUrlServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-04T00:00:00Z");

    @Test
    void publicObjectUsesStoredPublicUrl() {
        Fixture fixture = new Fixture();
        ObjectStorageFile file = publicFile();

        assertEquals("https://assets.example.com/dev/uploads/a.pdf", fixture.service().publicUrl(file));
    }

    @Test
    void privateFileSignsWorkerUrlWhenPrivateChannelEnabled() {
        Fixture fixture = new Fixture();
        ObjectStorageFile file = privateFile("dev/uploads/36/a.pdf");

        String url = fixture.service().privateFileUrl(file, Duration.ofMinutes(5));

        URI uri = URI.create(url);
        Map<String, String> query = query(uri);
        assertEquals("file.example.com", uri.getHost());
        assertEquals("/dev/uploads/36/a.pdf", uri.getPath());
        ObjectResourceTokenSigner.Payload token = fixture.verify(query.get("rt"));
        assertNotNull(token);
        assertEquals("private", token.scope());
        assertEquals("36", token.subject());
        assertEquals(List.of("dev/uploads/36/"), token.prefixes());
        assertEquals(String.valueOf(FIXED_NOW.plus(Duration.ofMinutes(5)).getEpochSecond()),
                String.valueOf(token.expiresAt()));
    }

    @Test
    void privateImageSignsTransformParams() {
        Fixture fixture = new Fixture();
        ObjectStorageFile file = privateFile("dev/pdf-assets/7/page.png");

        String url = fixture.service().privateImageUrl(file, Map.of(
                "preset", "ai-outline",
                "w", "1600",
                "q", "85",
                "format", "jpeg"
        ), Duration.ofMinutes(15));

        URI uri = URI.create(url);
        Map<String, String> query = query(uri);
        assertEquals("img.example.com", uri.getHost());
        assertEquals("1600", query.get("w"));
        assertEquals("85", query.get("q"));
        ObjectResourceTokenSigner.Payload token = fixture.verify(query.get("rt"));
        assertNotNull(token);
        assertEquals("private", token.scope());
        assertEquals(List.of("dev/pdf-assets/7/"), token.prefixes());
    }

    @Test
    void publicChannelFallsBackDuringMigration() {
        Fixture fixture = new Fixture();
        ObjectStorageFile file = publicFile();

        assertEquals("https://assets.example.com/dev/uploads/a.pdf",
                fixture.service().privateFileUrl(file, Duration.ofMinutes(5)));
    }

    @Test
    void privateFileUsesR2PresignedUrlWithoutDeliveryWorker() {
        Fixture fixture = new Fixture();
        fixture.workerProperties.setEnabled(false);
        ObjectStorageFile file = privateFile("workbench/private/publish/36/document.md");
        Duration ttl = Duration.ofMinutes(5);
        when(fixture.r2Client.generatePresignedGetUrl(StorageChannel.PRIVATE, file.getObjectKey(), ttl))
                .thenReturn("https://r2.example.com/signed-document");

        assertEquals("https://r2.example.com/signed-document", fixture.service().privateFileUrl(file, ttl));
        assertNull(fixture.service().publicUrl(file));
    }

    @Test
    void privateUserResourceTokenGrantsDefaultUserPrefixes() {
        Fixture fixture = new Fixture();

        ObjectAccessUrlService.ResourceTokenGrant grant = fixture.service()
                .privateUserResourceToken(36L, Duration.ofMinutes(10));

        assertNotNull(grant);
        assertEquals("https://file.example.com", grant.fileBaseUrl());
        assertEquals("https://img.example.com", grant.imageBaseUrl());
        assertEquals("private", grant.scope());
        assertEquals(600, grant.ttlSeconds());
        assertEquals(List.of(
                        "dev/uploads/36/",
                        "dev/generated/36/",
                        "dev/pdf-assets/36/",
                        "dev/docx-assets/36/",
                        "dev/pptx-assets/36/"),
                grant.prefixes());

        ObjectResourceTokenSigner.Payload token = fixture.verify(grant.resourceToken());
        assertNotNull(token);
        assertEquals("36", token.subject());
        assertEquals(grant.prefixes(), token.prefixes());
    }

    private ObjectStorageFile publicFile() {
        ObjectStorageFile file = new ObjectStorageFile();
        file.setStorageChannel(StorageChannel.PUBLIC.value());
        file.setBucket("paperpuppy");
        file.setObjectKey("dev/uploads/a.pdf");
        file.setPublicDomain("https://assets.example.com/");
        file.setPublicPath("/dev/uploads/a.pdf");
        return file;
    }

    private ObjectStorageFile privateFile(String objectKey) {
        ObjectStorageFile file = new ObjectStorageFile();
        file.setStorageChannel(StorageChannel.PRIVATE.value());
        file.setBucket("paperpuppy-private");
        file.setObjectKey(objectKey);
        file.setOwnerUserId(ownerUserIdFromObjectKey(objectKey));
        return file;
    }

    private Long ownerUserIdFromObjectKey(String objectKey) {
        String[] segments = objectKey == null ? new String[0] : objectKey.split("/");
        if (segments.length < 3) {
            return null;
        }
        try {
            return Long.parseLong(segments[2]);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, String> query(URI uri) {
        Map<String, String> params = new LinkedHashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    private static class Fixture {
        private final ObjectDeliveryWorkerProperties workerProperties = workerProperties();
        private final ObjectResourceTokenSigner tokenSigner = new ObjectResourceTokenSigner(
                workerProperties,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
        private final R2Client r2Client = mock(R2Client.class);
        private final StorageR2Properties r2Properties = r2Properties();
        private final ObjectAccessUrlService service = new ObjectAccessUrlService(
                workerProperties,
                tokenSigner,
                r2Client,
                r2Properties
        );

        Fixture() {
            when(r2Client.buildPublicUrl("dev/uploads/a.pdf"))
                    .thenReturn("https://assets.example.com/dev/uploads/a.pdf");
        }

        ObjectAccessUrlService service() {
            return service;
        }

        ObjectResourceTokenSigner.Payload verify(String token) {
            return tokenSigner.verify(token);
        }

        private ObjectDeliveryWorkerProperties workerProperties() {
            ObjectDeliveryWorkerProperties properties = new ObjectDeliveryWorkerProperties();
            properties.setEnabled(true);
            properties.setFileDomain("https://file.example.com");
            properties.setImageDomain("https://img.example.com");
            properties.setResourceTokenKeyId("kid-test");
            properties.setResourceTokenSecret("unit-test-secret");
            return properties;
        }

        private StorageR2Properties r2Properties() {
            StorageR2Properties properties = new StorageR2Properties();
            properties.setBucket("paperpuppy");
            properties.setPathPrefix("dev");
            properties.setPublicDomain("https://assets.example.com");
            return properties;
        }
    }
}
