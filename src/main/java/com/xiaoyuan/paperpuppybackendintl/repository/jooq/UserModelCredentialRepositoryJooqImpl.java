package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.repository.UserModelCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record6;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static org.jooq.impl.SQLDataType.BIGINT;
import static org.jooq.impl.SQLDataType.INTEGER;
import static org.jooq.impl.SQLDataType.VARBINARY;
import static org.jooq.impl.SQLDataType.VARCHAR;

@Repository
@RequiredArgsConstructor
public class UserModelCredentialRepositoryJooqImpl implements UserModelCredentialRepository {

    private static final Table<?> CREDENTIALS = DSL.table(DSL.name("backend_data", "user_model_credentials"));
    private static final Field<Long> USER_ID = DSL.field(DSL.name("user_id"), BIGINT);
    private static final Field<String> PROVIDER_ID = DSL.field(DSL.name("provider_id"), VARCHAR);
    private static final Field<byte[]> CIPHERTEXT = DSL.field(DSL.name("ciphertext"), VARBINARY);
    private static final Field<byte[]> NONCE = DSL.field(DSL.name("nonce"), VARBINARY);
    private static final Field<Integer> KEY_VERSION = DSL.field(DSL.name("key_version"), INTEGER);
    private static final Field<String> KEY_SUFFIX = DSL.field(DSL.name("key_suffix"), VARCHAR);

    private final DSLContext dsl;

    @Override
    public Optional<StoredCredential> find(Long userId, String providerId) {
        return dsl.select(USER_ID, PROVIDER_ID, CIPHERTEXT, NONCE, KEY_VERSION, KEY_SUFFIX)
                .from(CREDENTIALS)
                .where(USER_ID.eq(userId).and(PROVIDER_ID.eq(providerId)))
                .fetchOptional(this::map);
    }

    @Override
    public List<StoredCredential> list(Long userId) {
        return dsl.select(USER_ID, PROVIDER_ID, CIPHERTEXT, NONCE, KEY_VERSION, KEY_SUFFIX)
                .from(CREDENTIALS)
                .where(USER_ID.eq(userId))
                .fetch(this::map);
    }

    @Override
    public void upsert(StoredCredential credential) {
        dsl.insertInto(CREDENTIALS)
                .set(USER_ID, credential.userId())
                .set(PROVIDER_ID, credential.providerId())
                .set(CIPHERTEXT, credential.ciphertext())
                .set(NONCE, credential.nonce())
                .set(KEY_VERSION, credential.keyVersion())
                .set(KEY_SUFFIX, credential.keySuffix())
                .onConflict(USER_ID, PROVIDER_ID)
                .doUpdate()
                .set(CIPHERTEXT, credential.ciphertext())
                .set(NONCE, credential.nonce())
                .set(KEY_VERSION, credential.keyVersion())
                .set(KEY_SUFFIX, credential.keySuffix())
                .execute();
    }

    @Override
    public boolean delete(Long userId, String providerId) {
        return dsl.deleteFrom(CREDENTIALS)
                .where(USER_ID.eq(userId).and(PROVIDER_ID.eq(providerId)))
                .execute() > 0;
    }

    private StoredCredential map(Record6<Long, String, byte[], byte[], Integer, String> row) {
        return new StoredCredential(row.value1(), row.value2(), row.value3(), row.value4(),
                row.value5(), row.value6());
    }
}
