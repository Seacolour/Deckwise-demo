package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import org.jooq.Result;
import org.jooq.UpdatableRecord;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * jOOQ 持久化通用基类：
 * - 统一 Record -> Entity 映射
 *
 * @param <R> jOOQ 生成的 Record 类型
 * @param <D> 业务实体类型
 */
public abstract class BaseJooqRepository<R extends UpdatableRecord<R>, D> {

    protected D toDomain(R record) {
        return record == null ? null : map(record);
    }

    protected Optional<D> toOptional(R record) {
        return Optional.ofNullable(toDomain(record));
    }

    protected List<D> toList(Result<R> result) {
        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }
        return result.map(this::map);
    }

    protected abstract D map(R record);
}


