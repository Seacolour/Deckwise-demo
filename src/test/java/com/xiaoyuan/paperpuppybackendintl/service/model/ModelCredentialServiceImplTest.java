package com.xiaoyuan.paperpuppybackendintl.service.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.ModelCredentialUpsertRequest;
import com.xiaoyuan.paperpuppybackendintl.repository.UserModelCredentialRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserModelCredentialRepository.StoredCredential;
import com.xiaoyuan.paperpuppybackendintl.service.model.impl.ModelCredentialServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ModelCredentialServiceImplTest {

    @Test
    void credentialsAreScopedByUserAndResponsesContainOnlyMaskedSuffix() throws Exception {
        InMemoryRepository repository = new InMemoryRepository();
        ModelCredentialService service = new ModelCredentialServiceImpl(repository,
                new ModelCredentialCipher(Base64.getEncoder().encodeToString(new byte[32])));
        String secret = "sk-demo-private-1234";

        var response = service.upsert(7L, "deepseek", secret);
        assertEquals("****1234", response.maskedKey());
        assertTrue(response.configured());
        assertFalse(new ObjectMapper().writeValueAsString(response).contains(secret));
        assertFalse(new ModelCredentialUpsertRequest(secret).toString().contains(secret));
        assertEquals(Optional.of(secret), service.resolveApiKey(7L, "deepseek"));
        assertEquals(Optional.empty(), service.resolveApiKey(8L, "deepseek"));
        assertEquals(List.of("openai", "deepseek", "openrouter"), service.listStatus(8L).stream()
                .map(s -> s.providerId()).toList());
        assertTrue(service.listStatus(8L).stream().noneMatch(s -> s.configured()));
        assertFalse(service.delete(8L, "deepseek"));
        assertTrue(service.hasCredential(7L, "deepseek"));
        assertTrue(service.delete(7L, "deepseek"));
        assertEquals(Optional.empty(), service.resolveApiKey(7L, "deepseek"));

        var routed = service.upsert(7L, "openrouter", "or-private-5678");
        assertEquals("****5678", routed.maskedKey());
        assertEquals(Optional.of("or-private-5678"), service.resolveApiKey(7L, "openrouter"));
        assertFalse(new ObjectMapper().writeValueAsString(routed).contains("or-private-5678"));
    }

    @Test
    void rejectsUnlistedProviderAndBlankSecretWithoutWriting() {
        InMemoryRepository repository = new InMemoryRepository();
        ModelCredentialService service = new ModelCredentialServiceImpl(repository,
                new ModelCredentialCipher(Base64.getEncoder().encodeToString(new byte[32])));

        assertThrows(BusinessException.class, () -> service.upsert(7L, "custom-url", "secret"));
        assertThrows(BusinessException.class, () -> service.upsert(7L, "openai", "  "));
        assertThrows(BusinessException.class, () -> service.resolveApiKey(7L, "custom-url"));
        assertTrue(repository.rows.isEmpty());
    }

    private static class InMemoryRepository implements UserModelCredentialRepository {
        private final Map<String, StoredCredential> rows = new HashMap<>();

        private String key(Long userId, String providerId) {
            return userId + ":" + providerId;
        }

        @Override
        public Optional<StoredCredential> find(Long userId, String providerId) {
            return Optional.ofNullable(rows.get(key(userId, providerId)));
        }

        @Override
        public List<StoredCredential> list(Long userId) {
            return rows.values().stream().filter(row -> row.userId().equals(userId)).toList();
        }

        @Override
        public void upsert(StoredCredential credential) {
            rows.put(key(credential.userId(), credential.providerId()), credential);
        }

        @Override
        public boolean delete(Long userId, String providerId) {
            return rows.remove(key(userId, providerId)) != null;
        }
    }
}
