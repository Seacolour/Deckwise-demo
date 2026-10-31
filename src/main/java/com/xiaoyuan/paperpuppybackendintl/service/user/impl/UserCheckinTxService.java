package com.xiaoyuan.paperpuppybackendintl.service.user.impl;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.checkin.CheckinRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.checkin.CheckinResultDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.checkin.CheckinStatusDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.User;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserCheckinLog;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserCheckinStatus;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.*;

/**
 * 事务版签到逻辑（单独的 Spring Bean），用于避免 {@code @Transactional} 自调用导致事务不生效。
 */
@Service
@RequiredArgsConstructor
public class UserCheckinTxService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DSLContext dsl;
    private final UserRepository userRepository;

    @Transactional
    public CheckinResultDTO checkinTransactional(Long userId,
                                                 String platform,
                                                 String appVersion,
                                                 String ip,
                                                 String userAgent,
                                                 CheckinRequest body) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法用户ID");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在"));

        // 0) 尝试从客户端上报信息“首次绑定/补全”用户偏好（时区/语言/地区）
        //    注意：不接受客户端直接传日期；只接受时区作为“业务日计算语义”的候选来源。
        user = applyUserPreferencesFromRequest(user, body);

        ZoneId zone = resolveUserZone(user, body);
        LocalDate bizDate = LocalDate.now(zone);
        String bizDateStr = bizDate.format(DATE_FMT);

        // 1) 兼容“前端每日打开调用一次”的需求：顺带更新 user_devices（不影响签到主流程）
        upsertUserDeviceBestEffort(userId, body);

        // 2) 今日是否已签到（DB 强一致：唯一约束 + exists）
        boolean already = dsl.fetchExists(
                DSL.selectOne()
                        .from(USER_CHECKIN_LOG)
                        .where(UCL_USER_ID.eq(userId))
                        .and(UCL_CHECKIN_DATE.eq(bizDate))
                        .and(UCL_IS_DELETED.eq(false))
        );

        if (already) {
            UserCheckinStatus status = loadStatus(userId);
            CheckinResultDTO vo = new CheckinResultDTO();
            vo.setCheckedIn(true);
            vo.setRewardPoints(0);
            vo.setStreakAfter(status != null && status.getStreakCount() != null ? status.getStreakCount() : 0);
            vo.setLongestStreak(status != null && status.getLongestStreak() != null ? status.getLongestStreak() : 0);
            vo.setDate(bizDateStr);
            return vo;
        }

        // 3) 读取状态（行级锁，避免极端并发下 streak 回退）
        Record s = dsl.select(UCS_USER_ID, UCS_STREAK_COUNT, UCS_LONGEST_STREAK, UCS_LAST_CHECKIN_DATE)
                .from(USER_CHECKIN_STATUS)
                .where(UCS_USER_ID.eq(userId))
                .and(UCS_IS_DELETED.eq(false))
                .forUpdate()
                .fetchOne();

        LocalDate lastDate = s == null ? null : s.get(UCS_LAST_CHECKIN_DATE);
        Integer prevStreak = s == null ? 0 : s.get(UCS_STREAK_COUNT);
        Integer prevLongest = s == null ? 0 : s.get(UCS_LONGEST_STREAK);

        int newStreak;
        if (lastDate == null) {
            newStreak = 1;
        } else if (lastDate.equals(bizDate)) {
            // 理论上不会到这里（前面 already 已挡住），但做保护
            newStreak = prevStreak != null && prevStreak > 0 ? prevStreak : 1;
        } else if (lastDate.plusDays(1).equals(bizDate)) {
            newStreak = (prevStreak == null ? 0 : prevStreak) + 1;
        } else {
            newStreak = 1;
        }

        int longest = Math.max(prevLongest == null ? 0 : prevLongest, newStreak);

        // 4) 奖励策略（先落表 & 返回；后续可对接“积分/额度/订阅”模块真正发放）
        int reward = (newStreak >= 7) ? 1000 : 500;

        // 5) upsert 状态
        dsl.insertInto(USER_CHECKIN_STATUS)
                .set(UCS_USER_ID, userId)
                .set(UCS_STREAK_COUNT, newStreak)
                .set(UCS_LONGEST_STREAK, longest)
                .set(UCS_LAST_CHECKIN_DATE, bizDate)
                .set(UCS_IS_DELETED, false)
                .onConflict(UCS_USER_ID)
                .doUpdate()
                .set(UCS_STREAK_COUNT, DSL.excluded(UCS_STREAK_COUNT))
                .set(UCS_LONGEST_STREAK, DSL.excluded(UCS_LONGEST_STREAK))
                .set(UCS_LAST_CHECKIN_DATE, DSL.excluded(UCS_LAST_CHECKIN_DATE))
                .set(UCS_IS_DELETED, false)
                .execute();

        // 6) 插入日志：依赖 UNIQUE(user_id, checkin_date) 保证幂等
        //    若极端并发冲突，则按“已签”返回（reward=0），避免抛错影响体验。
        int inserted = dsl.insertInto(USER_CHECKIN_LOG)
                .set(UCL_USER_ID, userId)
                .set(UCL_CHECKIN_DATE, bizDate)
                .set(UCL_PLATFORM, emptyToNull(platform))
                .set(UCL_APP_VERSION, emptyToNull(appVersion))
                .set(UCL_DEVICE_TYPE, body != null ? emptyToNull(body.getDeviceType()) : null)
                .set(UCL_DEVICE_TOKEN, body != null ? emptyToNull(body.getDeviceToken()) : null)
                .set(UCL_REWARD_POINTS, reward)
                .set(UCL_STREAK_AFTER, newStreak)
                .set(UCL_IP_ADDRESS, emptyToNull(ip))
                .set(UCL_USER_AGENT, emptyToNull(userAgent))
                .set(UCL_IS_DELETED, false)
                .onConflict(UCL_USER_ID, UCL_CHECKIN_DATE)
                .doNothing()
                .execute();

        if (inserted == 0) {
            // 冲突：其他线程已写入日志
            UserCheckinStatus status = loadStatus(userId);
            CheckinResultDTO vo = new CheckinResultDTO();
            vo.setCheckedIn(true);
            vo.setRewardPoints(0);
            vo.setStreakAfter(status != null && status.getStreakCount() != null ? status.getStreakCount() : 0);
            vo.setLongestStreak(status != null && status.getLongestStreak() != null ? status.getLongestStreak() : 0);
            vo.setDate(bizDateStr);
            return vo;
        }

        CheckinResultDTO vo = new CheckinResultDTO();
        vo.setCheckedIn(true);
        vo.setRewardPoints(reward);
        vo.setStreakAfter(newStreak);
        vo.setLongestStreak(longest);
        vo.setDate(bizDateStr);
        return vo;
    }

    public CheckinStatusDTO getStatus(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法用户ID");
        }
        UserCheckinStatus s = loadStatus(userId);
        CheckinStatusDTO vo = new CheckinStatusDTO();
        vo.setStreakCount(s == null || s.getStreakCount() == null ? 0 : s.getStreakCount());
        vo.setLongestStreak(s == null || s.getLongestStreak() == null ? 0 : s.getLongestStreak());
        vo.setLastCheckinDate(s == null || s.getLastCheckinDate() == null ? null : s.getLastCheckinDate().format(DATE_FMT));
        return vo;
    }

    public List<UserCheckinLog> listLogs(Long userId, int days) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法用户ID");
        }
        int safeDays = Math.min(Math.max(days, 1), 31);

        // 业务时区：用用户偏好时区来计算“最近 N 天”的起点
        User user = userRepository.findById(userId).orElse(null);
        ZoneId zone = resolveUserZone(user, null);

        LocalDate from = LocalDate.now(zone).minusDays(safeDays - 1L);

        return dsl.select(UCL_ID, UCL_USER_ID, UCL_CHECKIN_DATE, UCL_PLATFORM, UCL_APP_VERSION,
                        UCL_DEVICE_TYPE, UCL_DEVICE_TOKEN, UCL_REWARD_POINTS, UCL_STREAK_AFTER,
                        UCL_IP_ADDRESS, UCL_USER_AGENT, UCL_CREATED_AT, UCL_IS_DELETED)
                .from(USER_CHECKIN_LOG)
                .where(UCL_USER_ID.eq(userId))
                .and(UCL_CHECKIN_DATE.ge(from))
                .and(UCL_IS_DELETED.eq(false))
                .orderBy(UCL_CHECKIN_DATE.desc())
                .fetch(this::mapLog);
    }

    private UserCheckinStatus loadStatus(Long userId) {
        Record r = dsl.select(UCS_USER_ID, UCS_STREAK_COUNT, UCS_LONGEST_STREAK, UCS_LAST_CHECKIN_DATE,
                        UCS_CREATED_AT, UCS_UPDATED_AT, UCS_IS_DELETED)
                .from(USER_CHECKIN_STATUS)
                .where(UCS_USER_ID.eq(userId))
                .and(UCS_IS_DELETED.eq(false))
                .fetchOne();
        if (r == null) {
            return null;
        }
        UserCheckinStatus s = new UserCheckinStatus();
        s.setUserId(r.get(UCS_USER_ID));
        s.setStreakCount(r.get(UCS_STREAK_COUNT));
        s.setLongestStreak(r.get(UCS_LONGEST_STREAK));
        s.setLastCheckinDate(r.get(UCS_LAST_CHECKIN_DATE));
        s.setCreatedAt(r.get(UCS_CREATED_AT));
        s.setUpdatedAt(r.get(UCS_UPDATED_AT));
        s.setDeleted(r.get(UCS_IS_DELETED));
        return s;
    }

    private UserCheckinLog mapLog(Record r) {
        UserCheckinLog log = new UserCheckinLog();
        log.setId(r.get(UCL_ID));
        log.setUserId(r.get(UCL_USER_ID));
        log.setCheckinDate(r.get(UCL_CHECKIN_DATE));
        log.setPlatform(r.get(UCL_PLATFORM));
        log.setAppVersion(r.get(UCL_APP_VERSION));
        log.setDeviceType(r.get(UCL_DEVICE_TYPE));
        log.setDeviceToken(r.get(UCL_DEVICE_TOKEN));
        log.setRewardPoints(r.get(UCL_REWARD_POINTS));
        log.setStreakAfter(r.get(UCL_STREAK_AFTER));
        log.setIpAddress(r.get(UCL_IP_ADDRESS));
        log.setUserAgent(r.get(UCL_USER_AGENT));
        log.setCreatedAt(r.get(UCL_CREATED_AT));
        log.setDeleted(r.get(UCL_IS_DELETED));
        return log;
    }

    private void upsertUserDeviceBestEffort(Long userId, CheckinRequest body) {
        if (body == null) {
            return;
        }
        String token = body.getDeviceToken();
        String type = body.getDeviceType();
        if (!StringUtils.hasText(token) || !StringUtils.hasText(type)) {
            return;
        }
        String env = StringUtils.hasText(body.getEnvironment()) ? body.getEnvironment() : "production";

        // UNIQUE(device_token, environment) 约束下做 upsert；last_active_at 用 now()
        try {
            dsl.insertInto(USER_DEVICES)
                    .set(UD_USER_ID, userId)
                    .set(UD_DEVICE_TOKEN, token)
                    .set(UD_DEVICE_TYPE, type)
                    .set(UD_ENVIRONMENT, env)
                    .set(UD_LAST_ACTIVE_AT, DSL.currentOffsetDateTime())
                    .onConflict(UD_DEVICE_TOKEN, UD_ENVIRONMENT)
                    .doUpdate()
                    .set(UD_USER_ID, DSL.excluded(UD_USER_ID))
                    .set(UD_DEVICE_TYPE, DSL.excluded(UD_DEVICE_TYPE))
                    .set(UD_LAST_ACTIVE_AT, DSL.currentOffsetDateTime())
                    .execute();
        } catch (Exception ignored) {
            // 设备信息更新失败不影响签到流程（例如该表未初始化/字段差异）
        }
    }

    private ZoneId resolveUserZone(User user, CheckinRequest body) {
        // 1) DB 已绑定的 preferred_time_zone 优先
        if (user != null && StringUtils.hasText(user.getPreferredTimeZone())) {
            try {
                return ZoneId.of(user.getPreferredTimeZone());
            } catch (Exception ignored) {
                // fallthrough
            }
        }
        // 2) 兼容：首次签到时还未绑定，允许用本次上报的 timeZone 计算（并在上面尝试写入 DB）
        if (body != null && StringUtils.hasText(body.getTimeZone())) {
            try {
                return ZoneId.of(body.getTimeZone());
            } catch (Exception ignored) {
                // fallthrough
            }
        }
        return ZoneId.of("UTC");
    }

    private static String emptyToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    /**
     * 将客户端上报的时区/语言/国家作为“候选”，只做校验后用于首次绑定或缺失补全。
     *
     * <p>安全原则：
     * - 不接受客户端上报日期
     * - 不在每次请求都允许随意改时区（避免刷奖励/制造边界问题）
     * - 若要支持“变更时区”，建议后续做单独接口 + 冷却时间/风控</p>
     */
    private User applyUserPreferencesFromRequest(User user, CheckinRequest body) {
        if (user == null || body == null) {
            return user;
        }

        boolean changed = false;

        // 1) timeZone：仅在 DB 为空时写入（首次绑定）
        if (!StringUtils.hasText(user.getPreferredTimeZone()) && StringUtils.hasText(body.getTimeZone())) {
            String tz = body.getTimeZone().trim();
            if (isValidZoneId(tz)) {
                user.setPreferredTimeZone(tz);
                changed = true;
            }
        }

        // 2) locale：仅在 DB 为空时写入
        if (!StringUtils.hasText(user.getPreferredLocale()) && StringUtils.hasText(body.getLocale())) {
            String locale = body.getLocale().trim();
            // 这里不强校验 BCP47，避免误伤；后续如需可加白名单/标准化
            if (!locale.isEmpty() && locale.length() <= 32) {
                user.setPreferredLocale(locale);
                changed = true;
            }
        }

        // 3) country：仅在 DB 为空时写入（二字码）
        if (!StringUtils.hasText(user.getLastKnownCountry()) && StringUtils.hasText(body.getCountry())) {
            String country = body.getCountry().trim().toUpperCase();
            if (country.matches("^[A-Z]{2}$")) {
                user.setLastKnownCountry(country);
                changed = true;
            }
        }

        if (changed) {
            // 复用 UserRepository.save：id 非空即 update；不会影响签到主流程
            try {
                return userRepository.save(user);
            } catch (Exception ignored) {
                return user;
            }
        }
        return user;
    }

    private boolean isValidZoneId(String tz) {
        try {
            ZoneId.of(tz);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}


