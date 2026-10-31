package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.Session;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SessionRepository {
    Optional<Session> findById(Long id);

    List<Session> listByProject(Long projectId, Boolean archived);

    List<Session> listByProject(Long projectId, Boolean archived, int offset, int limit, String sortField, String sortOrder);

    int countByProject(Long projectId, Boolean archived);

    Session save(Session session);

    String findTitleById(Long id);

    /**
     * 仅当当前 title 属于 expectedTitles 时才更新为 newTitle（用于自动改标题，避免覆盖用户手动修改）。
     */
    boolean updateTitleIfIn(Long sessionId, Collection<String> expectedTitles, String newTitle);

    boolean updateTitle(Long sessionId, String newTitle);

    boolean softDelete(Long id);
}


