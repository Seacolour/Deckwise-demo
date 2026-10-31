package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.repository.UserPasswordCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static org.jooq.impl.SQLDataType.BIGINT;
import static org.jooq.impl.SQLDataType.CLOB;

@Repository
@RequiredArgsConstructor
public class UserPasswordCredentialRepositoryJooqImpl implements UserPasswordCredentialRepository {

    private static final Table<?> CREDENTIALS = DSL.table(DSL.name("backend_data", "user_password_credentials"));
    private static final Field<Long> USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    private static final Field<String> PASSWORD_HASH = DSL.field(DSL.name("password_hash"), CLOB.nullable(false));

    private final DSLContext dsl;

    @Override
    public Optional<String> findPasswordHash(Long userId) {
        return dsl.select(PASSWORD_HASH)
                .from(CREDENTIALS)
                .where(USER_ID.eq(userId))
                .fetchOptional(PASSWORD_HASH);
    }

    @Override
    public void upsertPasswordHash(Long userId, String passwordHash) {
        dsl.insertInto(CREDENTIALS)
                .set(USER_ID, userId)
                .set(PASSWORD_HASH, passwordHash)
                .onConflict(USER_ID)
                .doUpdate()
                .set(PASSWORD_HASH, passwordHash)
                .execute();
    }
}
