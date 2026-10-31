package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.FileDerivedMediaAsset;

import java.util.Collection;
import java.util.List;

public interface FileDerivedMediaAssetRepository {

    List<FileDerivedMediaAsset> findAvailableByProjectAndSourceFileIds(Long projectId,
                                                                       Collection<Long> sourceFileIds,
                                                                       int limit);

    int countAvailableByProjectAndSourceFileId(Long projectId, Long sourceFileId);
}
