package com.xiaoyuan.paperpuppybackendintl.service.model;

import com.xiaoyuan.paperpuppybackendintl.model.dto.user.ModelCredentialStatus;

import java.util.List;
import java.util.Optional;

public interface ModelCredentialService {

    List<ModelCredentialStatus> listStatus(Long userId);

    ModelCredentialStatus upsert(Long userId, String providerId, String apiKey);

    boolean delete(Long userId, String providerId);

    boolean hasCredential(Long userId, String providerId);

    /** For trusted server-side routing only; never return this value from an HTTP endpoint. */
    Optional<String> resolveApiKey(Long userId, String providerId);
}
