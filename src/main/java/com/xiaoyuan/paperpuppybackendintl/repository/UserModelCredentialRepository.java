package com.xiaoyuan.paperpuppybackendintl.repository;

import java.util.List;
import java.util.Optional;

/** Persistence is scoped by user ID; ciphertext is never part of a public DTO. */
public interface UserModelCredentialRepository {

    record StoredCredential(Long userId, String providerId, byte[] ciphertext, byte[] nonce,
                            int keyVersion, String keySuffix) {
    }

    Optional<StoredCredential> find(Long userId, String providerId);

    List<StoredCredential> list(Long userId);

    void upsert(StoredCredential credential);

    boolean delete(Long userId, String providerId);
}
