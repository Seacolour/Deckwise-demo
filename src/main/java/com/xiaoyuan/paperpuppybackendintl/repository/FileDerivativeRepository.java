package com.xiaoyuan.paperpuppybackendintl.repository;

import org.jooq.JSONB;

public interface FileDerivativeRepository {

    boolean existsExtractedText(Long fileId);

    String getExtractedText(Long fileId);

    void upsertExtractedText(Long fileId, String contentText, JSONB metadata);
}


