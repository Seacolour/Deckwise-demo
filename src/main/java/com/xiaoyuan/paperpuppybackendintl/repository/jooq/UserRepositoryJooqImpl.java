package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.records.UsersRecord;
import com.xiaoyuan.paperpuppybackendintl.model.entity.User;
import com.xiaoyuan.paperpuppybackendintl.model.enums.UserRole;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.Users.USERS;

/**
 * 基于 jOOQ（使用代码生成的 meta 类）的 User 仓储实现
 */
@Repository
@Primary
@RequiredArgsConstructor
public class UserRepositoryJooqImpl extends BaseJooqRepository<UsersRecord, User> implements UserRepository {

    private final DSLContext dsl;

    @Override
    public Optional<User> findById(Long id) {
        return dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id))
                .fetchOptional(this::map);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return dsl.selectFrom(USERS)
                .where(USERS.EMAIL.eq(email))
                .fetchOptional(this::map);
    }

    @Override
    public List<User> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        // 去重并过滤 null
        List<Long> clean = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (clean.isEmpty()) {
            return Collections.emptyList();
        }
        return dsl.selectFrom(USERS)
                .where(USERS.ID.in(clean))
                .fetch(this::map);
    }

    @Override
    public User save(User user) {
        // 兜底确保角色非空，避免违反数据库 NOT NULL 约束
        if (user.getRole() == null) {
            user.setRole(UserRole.USER);
        }

        if (user.getId() == null) {
            // insert
            UsersRecord record = dsl.insertInto(USERS)
                    .set(USERS.EMAIL, user.getEmail())
                    .set(USERS.EMAIL_VERIFIED, user.getEmailVerified())
                    .set(USERS.NAME, user.getName())
                    .set(USERS.SIGN_UP_PROVIDER, user.getSignUpProvider())
                    .set(USERS.PREFERRED_LOCALE, user.getPreferredLocale())
                    .set(USERS.PREFERRED_TIME_ZONE, user.getPreferredTimeZone())
                    .set(USERS.LAST_KNOWN_COUNTRY, user.getLastKnownCountry())
                    .set(USERS.ROLE, user.getRole() != null ? user.getRole().name().toLowerCase() : null)
                    .set(USERS.IS_ACTIVE, user.getActive())
                    .set(USERS.IS_DELETED, user.getDeleted())
                    .returning()
                    .fetchOne();
            return map(record);
        } else {
            // update
            dsl.update(USERS)
                    .set(USERS.EMAIL, user.getEmail())
                    .set(USERS.EMAIL_VERIFIED, user.getEmailVerified())
                    .set(USERS.NAME, user.getName())
                    .set(USERS.SIGN_UP_PROVIDER, user.getSignUpProvider())
                    .set(USERS.LAST_LOGIN_AT, user.getLastLoginAt())
                    .set(USERS.PREFERRED_LOCALE, user.getPreferredLocale())
                    .set(USERS.PREFERRED_TIME_ZONE, user.getPreferredTimeZone())
                    .set(USERS.LAST_KNOWN_COUNTRY, user.getLastKnownCountry())
                    .set(USERS.ROLE, user.getRole() != null ? user.getRole().name().toLowerCase() : null)
                    .set(USERS.IS_ACTIVE, user.getActive())
                    .set(USERS.IS_DELETED, user.getDeleted())
                    .where(USERS.ID.eq(user.getId()))
                    .execute();
            return user;
        }
    }

    @Override
    protected User map(UsersRecord record) {
        if (record == null) {
            return null;
        }
        User user = new User();
        user.setId(record.getId());
        user.setEmail(record.getEmail());
        user.setEmailVerified(record.getEmailVerified());
        user.setName(record.getName());
        user.setSignUpProvider(record.getSignUpProvider());
        user.setPreferredLocale(record.getPreferredLocale());
        user.setPreferredTimeZone(record.getPreferredTimeZone());
        user.setLastKnownCountry(record.getLastKnownCountry());
        user.setCreatedAt(record.getCreatedAt());
        user.setUpdatedAt(record.getUpdatedAt());
        user.setLastLoginAt(record.getLastLoginAt());
        user.setActive(record.getIsActive());
        user.setDeleted(record.getIsDeleted());
        // 将字符串 role 转换为领域枚举，大小写不敏感
        user.setRole(UserRole.fromString(record.getRole()));
        return user;
    }
}
