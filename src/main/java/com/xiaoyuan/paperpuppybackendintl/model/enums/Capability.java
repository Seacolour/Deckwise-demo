package com.xiaoyuan.paperpuppybackendintl.model.enums;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * Stable product capabilities exposed to clients.
 */
public enum Capability {

    SMART_MODEL_ACCESS("models.smart.use");

    private final String value;

    Capability(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Capability require(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "capability 不能为空");
        }
        String normalized = rawValue.trim();
        return Arrays.stream(values())
                .filter(capability -> capability.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的 capability"));
    }
}
