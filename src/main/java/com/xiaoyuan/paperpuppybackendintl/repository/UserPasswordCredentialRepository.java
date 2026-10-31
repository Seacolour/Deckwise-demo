package com.xiaoyuan.paperpuppybackendintl.repository;

import java.util.Optional;

/** Stores only password hashes, separately from the public user profile. */
public interface UserPasswordCredentialRepository {

    Optional<String> findPasswordHash(Long userId);

    void upsertPasswordHash(Long userId, String passwordHash);
}
