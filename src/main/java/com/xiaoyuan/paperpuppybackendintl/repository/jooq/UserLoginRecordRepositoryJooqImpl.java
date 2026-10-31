package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.records.UserLoginLogRecord;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserLoginRecord;
import com.xiaoyuan.paperpuppybackendintl.model.enums.IdentityProvider;
import com.xiaoyuan.paperpuppybackendintl.repository.UserLoginRecordRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.UserLoginLog.USER_LOGIN_LOG;

/**
 * 基于 jOOQ（代码生成）的用户登录日志仓储实现
 */
@Repository
@Primary
@RequiredArgsConstructor
public class UserLoginRecordRepositoryJooqImpl implements UserLoginRecordRepository {

    private final DSLContext dsl;

    @Override
    public UserLoginRecord save(UserLoginRecord record) {
        if (record.getId() == null) {
            UserLoginLogRecord inserted = dsl.insertInto(USER_LOGIN_LOG)
                    .set(USER_LOGIN_LOG.USER_ID, record.getUserId())
                    .set(USER_LOGIN_LOG.LOGIN_TIME, record.getLoginTime())
                    .set(USER_LOGIN_LOG.IP_ADDRESS, record.getIpAddress())
                    .set(USER_LOGIN_LOG.USER_AGENT, record.getUserAgent())
                    .set(USER_LOGIN_LOG.PROVIDER, toJooqProvider(record.getProvider()))
                    .set(USER_LOGIN_LOG.SUCCESS, record.getSuccess())
                    .returning()
                    .fetchOne();
            if (inserted != null) {
                record.setId(inserted.getId());
            }
        } else {
            dsl.update(USER_LOGIN_LOG)
                    .set(USER_LOGIN_LOG.USER_ID, record.getUserId())
                    .set(USER_LOGIN_LOG.LOGIN_TIME, record.getLoginTime())
                    .set(USER_LOGIN_LOG.IP_ADDRESS, record.getIpAddress())
                    .set(USER_LOGIN_LOG.USER_AGENT, record.getUserAgent())
                    .set(USER_LOGIN_LOG.PROVIDER, toJooqProvider(record.getProvider()))
                    .set(USER_LOGIN_LOG.SUCCESS, record.getSuccess())
                    .where(USER_LOGIN_LOG.ID.eq(record.getId()))
                    .execute();
        }
        return record;
    }

    private com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.enums.IdentityProvider toJooqProvider(IdentityProvider provider) {
        if (provider == null) {
            return null;
        }
        return com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.enums.IdentityProvider
                .lookupLiteral(provider.toDatabaseValue());
    }
}


