package com.xiaoyuan.paperpuppybackendintl.model.dto.checkin;

import lombok.Data;

/**
 * 签到请求：允许前端“顺带”传递一些客户端信息（可选）。
 *
 * <p>说明：这些字段主要用于更新 user_devices / 风控 / 统计；业务签到日以服务端 + 用户偏好时区计算为准。</p>
 */
@Data
public class CheckinRequest {

    /**
     * 设备 Token（例如 APNS/FCM token）
     */
    private String deviceToken;

    /**
     * 设备类型：ios/android/web/mac...
     */
    private String deviceType;

    /**
     * 环境：production/sandbox（可选，默认 production）
     */
    private String environment;

    /**
     * IANA 时区名（建议）：例如 "Asia/Shanghai" / "America/Los_Angeles"
     *
     * <p>注意：后端不会信任前端传来的日期，只会用该时区在服务端计算“今天”。</p>
     */
    private String timeZone;

    /**
     * 用户偏好语言，如 "zh-CN"（可选）
     */
    private String locale;

    /**
     * 国家/地区二字码，如 "CN" / "US"（可选）
     */
    private String country;
}


