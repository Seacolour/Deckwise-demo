package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.repository.FileTextFragmentRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.FileTextFragments.FILE_TEXT_FRAGMENTS;

@Repository
@Primary
@RequiredArgsConstructor
public class FileTextFragmentRepositoryJooqImpl implements FileTextFragmentRepository {

    private final DSLContext dsl;

    @Override
    public int countByFileId(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return 0;
        }
        Integer count = dsl.selectCount()
                .from(FILE_TEXT_FRAGMENTS)
                .where(FILE_TEXT_FRAGMENTS.FILE_ID.eq(fileId)
                        .and(FILE_TEXT_FRAGMENTS.IS_DELETED.eq(false)))
                .fetchOne(0, Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<String> listFragmentContents(Long fileId, int limit) {
        return listFragmentContents(fileId, 0, limit);
    }

    @Override
    public List<String> listFragmentContents(Long fileId, int startIndexInclusive, int limit) {
        if (fileId == null || fileId <= 0) {
            return List.of();
        }
        int startIdx = Math.max(startIndexInclusive, 0);
        int lim = limit <= 0 ? 0 : Math.min(limit, 200);
        if (lim == 0) {
            return List.of();
        }
        return dsl.select(FILE_TEXT_FRAGMENTS.FRAGMENT_CONTENT)
                .from(FILE_TEXT_FRAGMENTS)
                .where(FILE_TEXT_FRAGMENTS.FILE_ID.eq(fileId)
                        .and(FILE_TEXT_FRAGMENTS.IS_DELETED.eq(false))
                        .and(FILE_TEXT_FRAGMENTS.FRAGMENT_INDEX.ge(startIdx)))
                .orderBy(FILE_TEXT_FRAGMENTS.FRAGMENT_INDEX.asc())
                .limit(lim)
                .fetch(FILE_TEXT_FRAGMENTS.FRAGMENT_CONTENT);
    }

    @Override
    public void upsertFragments(Long fileId, List<String> fragments, String messageType) {
        if (fileId == null || fileId <= 0) {
            return;
        }
        if (fragments == null || fragments.isEmpty()) {
            return;
        }
        int total = fragments.size();
        List<Query> queries = new ArrayList<>(total);

        for (int i = 0; i < fragments.size(); i++) {
            String content = fragments.get(i);
            if (content == null) {
                content = "";
            }
            int idx = i;
            Query q = dsl.insertInto(FILE_TEXT_FRAGMENTS)
                    .set(FILE_TEXT_FRAGMENTS.FILE_ID, fileId)
                    .set(FILE_TEXT_FRAGMENTS.FRAGMENT_INDEX, idx)
                    .set(FILE_TEXT_FRAGMENTS.FRAGMENT_CONTENT, content)
                    .set(FILE_TEXT_FRAGMENTS.RAW_CONTENT, (org.jooq.JSONB) null)
                    .set(FILE_TEXT_FRAGMENTS.MESSAGE_TYPE, messageType)
                    .set(FILE_TEXT_FRAGMENTS.TOTAL_FRAGMENTS, total)
                    .set(FILE_TEXT_FRAGMENTS.IS_DELETED, false)
                    .onConflict(FILE_TEXT_FRAGMENTS.FILE_ID, FILE_TEXT_FRAGMENTS.FRAGMENT_INDEX)
                    .doUpdate()
                    .set(FILE_TEXT_FRAGMENTS.FRAGMENT_CONTENT, content)
                    .set(FILE_TEXT_FRAGMENTS.MESSAGE_TYPE, messageType)
                    .set(FILE_TEXT_FRAGMENTS.TOTAL_FRAGMENTS, total)
                    .set(FILE_TEXT_FRAGMENTS.IS_DELETED, false);
            queries.add(q);
        }

        dsl.batch(queries).execute();
    }
}


