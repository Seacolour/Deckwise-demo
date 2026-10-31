package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.PdfDerivedMediaAssetCacheEntry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PdfDerivedMediaAssetCacheRepository {

    List<PdfDerivedMediaAssetCacheEntry> findAvailableByOwnerHashAndSignature(Long ownerUserId,
                                                                              String sourceContentHash,
                                                                              String extractorSignature,
                                                                              int limit);

    Optional<PdfDerivedMediaAssetCacheEntry> insertAvailable(PdfDerivedMediaAssetCacheEntry entry);

    int linkAssetsToProject(Long projectId,
                            Long sourceFileId,
                            Long createdBy,
                            Collection<Long> cacheAssetIds);

    int softDeleteProjectLinksByProjectId(Long projectId);
}
