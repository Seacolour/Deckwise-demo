package com.xiaoyuan.paperpuppybackendintl.repository;

import java.util.List;

public interface FileTextFragmentRepository {

    int countByFileId(Long fileId);

    /**
     * 按 fragment_index 升序读取前 N 个分片
     */
    List<String> listFragmentContents(Long fileId, int limit);

    /**
     * 按 fragment_index 升序读取窗口分片（startIndex 为 0-based，包含起点）。
     */
    List<String> listFragmentContents(Long fileId, int startIndexInclusive, int limit);

    void upsertFragments(Long fileId, List<String> fragments, String messageType);
}


