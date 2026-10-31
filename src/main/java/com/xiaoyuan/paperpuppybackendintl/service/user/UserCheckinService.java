package com.xiaoyuan.paperpuppybackendintl.service.user;

import com.xiaoyuan.paperpuppybackendintl.model.dto.checkin.CheckinRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.checkin.CheckinResultDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.checkin.CheckinStatusDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserCheckinLog;

import java.util.List;

public interface UserCheckinService {

    CheckinResultDTO checkin(Long userId, String platform, String appVersion, String ip, String userAgent, CheckinRequest body);

    CheckinStatusDTO getStatus(Long userId);

    List<UserCheckinLog> listLogs(Long userId, int days);
}


