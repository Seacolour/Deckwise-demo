package com.xiaoyuan.paperpuppybackendintl.infrastructure.redis;

/**
 * @author Seacolour
 * @date 2025/12/16 14:11
 * @description:
 */


public final class RedisKey {

    private static final String PREFIX = "pp:intl:";

    public static String loginCode(String email) {
        return PREFIX + "login:code:" + email;
    }

    public static String tokenBlacklist(String jti) {
        return PREFIX + "token:blacklist:" + jti;
    }
    public static String lock(String name) {
        return PREFIX + "lock:" + name;
    }

    public static String rateLimit(String name) {
        return PREFIX + "rate-limit:" + name;
    }

    public static String chatAbort(Long userId, Long sessionId) {
        return PREFIX + "chat:abort:" + userId + ":" + sessionId;
    }

    public static String chatLock(Long userId, Long sessionId) {
        return PREFIX + "chat:lock:" + userId + ":" + sessionId;
    }

    public static String pendingProjectFile(String fileRef) {
        return PREFIX + "file:pending:" + fileRef;
    }

    public static String pendingProjectFileCleanup() {
        return PREFIX + "file:pending:cleanup";
    }

    public static String derivedMediaExtractionResult(String kind,
                                                      Long projectId,
                                                      Long sourceFileId,
                                                      String signatureHash) {
        return PREFIX + "derived-media:extraction:" + kind + ":" + projectId + ":" + sourceFileId + ":" + signatureHash;
    }

    public static String giftCardDefinition(String code) {
        return PREFIX + "gift-card:def:" + code;
    }

    public static String giftCardRedeemed(String code) {
        return PREFIX + "gift-card:redeemed:" + code;
    }

    public static String giftCardRedeemLock(String code) {
        return PREFIX + "gift-card:redeem:lock:" + code;
    }

    private RedisKey() {
    }
}
