package com.xiaoyuan.paperpuppybackendintl.service.model.impl;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.ModelCredentialStatus;
import com.xiaoyuan.paperpuppybackendintl.repository.UserModelCredentialRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserModelCredentialRepository.StoredCredential;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelCredentialCipher;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModelCredentialServiceImpl implements ModelCredentialService {

    public static final List<String> PROVIDER_IDS = List.of("openai", "deepseek", "openrouter");
    private static final int MAX_API_KEY_CHARS = 4096;

    private final UserModelCredentialRepository repository;
    private final ModelCredentialCipher cipher;

    @Override
    public List<ModelCredentialStatus> listStatus(Long userId) {
        requireUser(userId);
        Map<String, StoredCredential> saved = repository.list(userId).stream()
                .collect(Collectors.toMap(StoredCredential::providerId, Function.identity()));
        return PROVIDER_IDS.stream()
                .map(providerId -> status(providerId, saved.get(providerId)))
                .toList();
    }

    @Override
    @Transactional
    public ModelCredentialStatus upsert(Long userId, String providerId, String apiKey) {
        requireUser(userId);
        requireProvider(providerId);
        if (apiKey == null || apiKey.isBlank() || apiKey.length() > MAX_API_KEY_CHARS
                || apiKey.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的模型 API Key");
        }
        String normalized = apiKey.strip();
        ModelCredentialCipher.Encrypted encrypted = cipher.encrypt(userId, providerId, normalized);
        String suffix = normalized.substring(Math.max(0, normalized.length() - 4));
        StoredCredential stored = new StoredCredential(userId, providerId,
                encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion(), suffix);
        repository.upsert(stored);
        return status(providerId, stored);
    }

    @Override
    @Transactional
    public boolean delete(Long userId, String providerId) {
        requireUser(userId);
        requireProvider(providerId);
        return repository.delete(userId, providerId);
    }

    @Override
    public boolean hasCredential(Long userId, String providerId) {
        requireUser(userId);
        requireProvider(providerId);
        return repository.find(userId, providerId).isPresent();
    }

    @Override
    public Optional<String> resolveApiKey(Long userId, String providerId) {
        requireUser(userId);
        requireProvider(providerId);
        return repository.find(userId, providerId)
                .map(stored -> cipher.decrypt(userId, providerId, stored.ciphertext(),
                        stored.nonce(), stored.keyVersion()));
    }

    private ModelCredentialStatus status(String providerId, StoredCredential stored) {
        return stored == null
                ? new ModelCredentialStatus(providerId, false, null)
                : new ModelCredentialStatus(providerId, true, "****" + stored.keySuffix());
    }

    private void requireProvider(String providerId) {
        if (!PROVIDER_IDS.contains(providerId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的模型提供商");
        }
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
    }
}
