package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;

import java.util.List;
import java.util.Optional;

public interface ObjectStorageFileRepository {

    /**
     * 按用户 + source 查找一条可用文件。
     * <p>
     * 相同 source 在不同 storage_channel 下可能并存；需要区分 channel 的写入/复用路径应使用
     * {@link #findAvailableByOwnerSourceAndChannel(Long, String, String, String)}。
     * </p>
     */
    Optional<ObjectStorageFile> findAvailableByOwnerAndSource(Long ownerUserId, String sourceType, String sourceKey);

    /**
     * 按用户 + source + storage_channel 查找可用文件。
     * <p>
     * public/private 是存储边界；相同 source_key 在不同 channel 下不能互相复用。
     */
    default Optional<ObjectStorageFile> findAvailableByOwnerSourceAndChannel(Long ownerUserId,
                                                                             String sourceType,
                                                                             String sourceKey,
                                                                             String storageChannel) {
        String expectedChannel = normalizedStorageChannel(storageChannel);
        return findAvailableByOwnerAndSource(ownerUserId, sourceType, sourceKey)
                .filter(file -> expectedChannel.equals(normalizedStorageChannel(file.getStorageChannel())));
    }

    /**
     * 按用户 + source 查找一条文件，不限制状态。
     * <p>
     * 用于对象已被标记 stale/deleted 时的“自愈恢复”场景，避免唯一键冲突后无法重建记录。
     */
    Optional<ObjectStorageFile> findByOwnerAndSource(Long ownerUserId, String sourceType, String sourceKey);

    /**
     * 按用户 + source + storage_channel 查找文件，不限制状态。
     */
    default Optional<ObjectStorageFile> findByOwnerSourceAndChannel(Long ownerUserId,
                                                                   String sourceType,
                                                                   String sourceKey,
                                                                   String storageChannel) {
        String expectedChannel = normalizedStorageChannel(storageChannel);
        return findByOwnerAndSource(ownerUserId, sourceType, sourceKey)
                .filter(file -> expectedChannel.equals(normalizedStorageChannel(file.getStorageChannel())));
    }

    Optional<ObjectStorageFile> findById(Long id);

    List<ObjectStorageFile> listRecentUserUploadedFiles(Long ownerUserId, int limit);

    ObjectStorageFile insert(ObjectStorageFile file);

    /**
     * 将文件标记为 stale（用于“对象存储已被生命周期清理，但 DB 仍显示 available”的兜底）。
     *
     * @return 是否有行被更新
     */
    boolean markStale(Long fileId);

    /**
     * 回写 RAG 向量源标识（JRAG: vector_source_id）。
     * <p>
     * 注意：DB 列名沿用历史字段 source_id，类型为 TEXT。
     * </p>
     */
    boolean updateSourceId(Long fileId, String sourceId);

    /**
     * 更新文件记录（用于补充metadata等字段）
     */
    void update(ObjectStorageFile file);

    private static String normalizedStorageChannel(String value) {
        String normalized = StorageChannel.normalize(value);
        return StorageChannel.isKnown(normalized) ? normalized : StorageChannel.PUBLIC.value();
    }
}
