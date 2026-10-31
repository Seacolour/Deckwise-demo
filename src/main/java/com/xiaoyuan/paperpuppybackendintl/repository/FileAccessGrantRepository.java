package com.xiaoyuan.paperpuppybackendintl.repository;

import java.util.Collection;

/**
 * 文件授权：用户只有在"通过服务端上传/登记"后，才允许在对话上下文中引用该文件分片。
 * <p>
 * 仅支持 project 维度永久授权。
 */
public interface FileAccessGrantRepository {

    /**
     * 创建/更新用户-文件关联（project 维度永久授权）。
     */
    void upsertLink(Long userId, Long fileId, Long projectId);

    /**
     * 批量创建/更新 project 维度永久授权。
     * <p>
     * 用于 onboarding / 批量添加文件到项目等场景，减少 DB 往返。
     * </p>
     */
    void upsertProjectLinks(Long userId, Long projectId, Collection<Long> fileIds);

    /**
     * 项目删除/归档清理时撤销该项目下的文件授权。
     */
    int softDeleteByProjectId(Long userId, Long projectId);

    /**
     * 鉴权：校验用户是否拥有该文件在指定项目下的有效授权。
     */
    boolean hasValidLink(Long userId, Long fileId, Long projectId);
}
