package com.xiaoyuan.paperpuppybackendintl.model.dto.checkin;

import lombok.Data;

import java.io.Serializable;

/**
 * 签到结果
 */
@Data
public class CheckinResultDTO implements Serializable {
    private boolean checkedIn;
    private int rewardPoints;
    private int streakAfter;
    private int longestStreak;
    private String date; // yyyy-MM-dd
}


