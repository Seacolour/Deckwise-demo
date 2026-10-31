package com.xiaoyuan.paperpuppybackendintl.common;

/**
 * 自定义错误码
 */
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    PARAM_VALIDATION_FAILED(40010, "参数校验失败"),
    CAPTCHA_ERROR(40011, "验证码错误"),
    PHONE_ALREADY_BOUND(40012, "该手机号已绑定其它微信账号"),

    NOT_LOGIN_ERROR(40100, "用户未登录"),
    TOKEN_INVALID(40102, "Token已失效"),

    FORBIDDEN_ERROR(40300, "禁止访问"),
    NO_AUTH_ERROR(40301, "用户无权限访问"),
    NO_ADMIN_ERROR(40302, "当前用户角色权限不足"),

    NOT_FOUND_ERROR(40400, "请求数据不存在"),

    ACCOUNT_CONFLICT(40901, "账号已存在"),
    REVISION_CONFLICT(40903, "revision conflict"),

    INVALID_FILE_TYPE(41500, "文件类型不支持"),
    UPDATE_REQUIRED(42600, "当前版本过旧，请升级到最新版本后继续使用"),
    FILE_NOT_IN_SESSION(42601, "文件不在当前会话中"),

    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败"),
    SYSTEM_BUSY(50300, "系统繁忙，请稍后重试"),
    LOCK_ACQUIRE_FAILED(50301, "系统繁忙，请稍后再试"),
    MESSAGE_SEND_FAILED(50302, "消息发送失败"),

    EXTERNAL_SERVICE_ERROR(50400, "调用第三方服务失败");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

}
