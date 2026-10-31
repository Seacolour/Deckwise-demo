package com.xiaoyuan.paperpuppybackendintl.model.dto.checkin;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前签到状态
 */
@Data
public class CheckinStatusDTO implements Serializable {
    private int streakCount;
    private int longestStreak;
    private String lastCheckinDate; // yyyy-MM-dd or null
}


