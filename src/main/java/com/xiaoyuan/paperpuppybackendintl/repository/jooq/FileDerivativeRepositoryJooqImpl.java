package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.repository.FileDerivativeRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.FileDerivatives.FILE_DERIVATIVES;

@Repository
@Primary
@RequiredArgsConstructor
public class FileDerivativeRepositoryJooqImpl implements FileDerivativeRepository {

    private final DSLContext dsl;

    @Override
    public boolean existsExtractedText(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return false;
        }
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(FILE_DERIVATIVES)
                        .where(FILE_DERIVATIVES.FILE_ID.eq(fileId)
                                .and(FILE_DERIVATIVES.TYPE.eq("extracted_text"))
                                .and(FILE_DERIVATIVES.IS_DELETED.eq(false))
                                .and(FILE_DERIVATIVES.STATUS.eq("ready")))
        );
    }

    @Override
    public String getExtractedText(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return null;
        }
        return dsl.select(FILE_DERIVATIVES.CONTENT_TEXT)
                .from(FILE_DERIVATIVES)
                .where(FILE_DERIVATIVES.FILE_ID.eq(fileId)
                        .and(FILE_DERIVATIVES.TYPE.eq("extracted_text"))
                        .and(FILE_DERIVATIVES.IS_DELETED.eq(false))
                        .and(FILE_DERIVATIVES.STATUS.eq("ready")))
                .fetchOne(FILE_DERIVATIVES.CONTENT_TEXT);
    }

    @Override
    public void upsertExtractedText(Long fileId, String contentText, JSONB metadata) {
        if (fileId == null || fileId <= 0) {
            return;
        }
        if (contentText == null) {
            contentText = "";
        }

        dsl.insertInto(FILE_DERIVATIVES)
                .set(FILE_DERIVATIVES.FILE_ID, fileId)
                .set(FILE_DERIVATIVES.TYPE, "extracted_text")
                .set(FILE_DERIVATIVES.STATUS, "ready")
                .set(FILE_DERIVATIVES.STORAGE_TYPE, "r2")
                .set(FILE_DERIVATIVES.BUCKET, (String) null)
                .set(FILE_DERIVATIVES.OBJECT_KEY, (String) null)
                .set(FILE_DERIVATIVES.CONTENT_TEXT, contentText)
                .set(FILE_DERIVATIVES.METADATA, metadata)
                .set(FILE_DERIVATIVES.IS_DELETED, false)
                .onConflict(FILE_DERIVATIVES.FILE_ID, FILE_DERIVATIVES.TYPE)
                .doUpdate()
                .set(FILE_DERIVATIVES.STATUS, "ready")
                .set(FILE_DERIVATIVES.STORAGE_TYPE, "r2")
                .set(FILE_DERIVATIVES.CONTENT_TEXT, contentText)
                .set(FILE_DERIVATIVES.METADATA, metadata)
                .set(FILE_DERIVATIVES.IS_DELETED, false)
                .execute();
    }

}


