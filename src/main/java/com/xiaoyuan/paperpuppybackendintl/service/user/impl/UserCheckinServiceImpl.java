package com.xiaoyuan.paperpuppybackendintl.service.user.impl;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisKey;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisLockClient;
import com.xiaoyuan.paperpuppybackendintl.model.dto.checkin.CheckinRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.checkin.CheckinResultDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.checkin.CheckinStatusDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserCheckinLog;
import com.xiaoyuan.paperpuppybackendintl.service.user.UserCheckinService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserCheckinServiceImpl implements UserCheckinService {
    private final RedisLockClient redisLockClient;
    private final UserCheckinTxService userCheckinTxService;

    @Override
    public CheckinResultDTO checkin(Long userId,
                                   String platform,
                                   String appVersion,
                                   String ip,
                                   String userAgent,
                                   CheckinRequest body) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法用户ID");
        }

        String lockKey = RedisKey.lock("checkin:" + userId);
        boolean locked = redisLockClient.tryLock(lockKey, 2, 12, TimeUnit.SECONDS);
        if (!locked) {
            throw new BusinessException(ErrorCode.LOCK_ACQUIRE_FAILED, "系统繁忙，请稍后再试");
        }

        try {
            // 关键：事务逻辑在独立 Bean（UserCheckinTxService）中执行，避免 @Transactional 自调用不生效
            return userCheckinTxService.checkinTransactional(userId, platform, appVersion, ip, userAgent, body);
        } finally {
            redisLockClient.unlock(lockKey);
        }
    }

    @Override
    public CheckinStatusDTO getStatus(Long userId) {
        return userCheckinTxService.getStatus(userId);
    }

    @Override
    public List<UserCheckinLog> listLogs(Long userId, int days) {
        return userCheckinTxService.listLogs(userId, days);
    }
}


