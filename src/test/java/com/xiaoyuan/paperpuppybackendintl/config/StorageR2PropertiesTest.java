package com.xiaoyuan.paperpuppybackendintl.config;

import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StorageR2PropertiesTest {

    @Test
    void privateNodeFallsBackToPublicConnectionButNotPublicBucketOrDomain() {
        StorageR2Properties properties = new StorageR2Properties();
        properties.setEndpoint("https://account.r2.cloudflarestorage.com");
        properties.setRegion("auto");
        properties.setBucket("paperpuppy");
        properties.setAccessKey("public-ak");
        properties.setSecretKey("public-sk");
        properties.setPathPrefix("dev");
        properties.setPublicDomain("https://assets.example.com");

        StorageR2Properties.Node privateNode = properties.effectiveNode(StorageChannel.PRIVATE);

        assertEquals("https://account.r2.cloudflarestorage.com", privateNode.getEndpoint());
        assertEquals("auto", privateNode.getRegion());
        assertEquals("public-ak", privateNode.getAccessKey());
        assertEquals("public-sk", privateNode.getSecretKey());
        assertEquals("dev", privateNode.getPathPrefix());
        assertEquals("paperpuppy-private", privateNode.getBucket());
        assertNull(privateNode.getPublicDomain());
    }

    @Test
    void privateNodeUsesExplicitPrivateBucketWhenConfigured() {
        StorageR2Properties properties = new StorageR2Properties();
        properties.setEndpoint("https://public-account.r2.cloudflarestorage.com");
        properties.setBucket("paperpuppy");
        properties.setAccessKey("public-ak");
        properties.setSecretKey("public-sk");

        StorageR2Properties.Node configuredPrivate = new StorageR2Properties.Node();
        configuredPrivate.setBucket("paperpuppy-private-prod");
        configuredPrivate.setAccessKey("private-ak");
        configuredPrivate.setSecretKey("private-sk");
        properties.setPrivate(configuredPrivate);

        StorageR2Properties.Node privateNode = properties.effectiveNode(StorageChannel.PRIVATE);

        assertEquals("https://public-account.r2.cloudflarestorage.com", privateNode.getEndpoint());
        assertEquals("paperpuppy-private-prod", privateNode.getBucket());
        assertEquals("private-ak", privateNode.getAccessKey());
        assertEquals("private-sk", privateNode.getSecretKey());
        assertNull(privateNode.getPublicDomain());
    }
}
