package com.xiaoyuan.paperpuppybackendintl.exception;

import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.utils.NetUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger accessLogger = LoggerFactory.getLogger("com.xiaoyuan.paperpuppybackendintl.accesslog");
    /**
     * “客户端错误请求”的专用 logger：是否输出由 logging.level 控制（支持 YAML/环境变量/运行时调整）。
     * 建议：prod 默认 OFF，dev/test 可设为 INFO/DEBUG，必要时线上临时打开。
     */
    private static final Logger badRequestLogger = LoggerFactory.getLogger("com.xiaoyuan.paperpuppybackendintl.badrequest");

    private final Environment environment;

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        // 业务异常通常是“可预期”的失败（权限/参数/状态），生产环境不打印堆栈，避免刷屏影响排障。
        // 非 prod 环境保留堆栈，便于本地快速定位问题。
        if (isProdLike()) {
            logBusinessExceptionNoStack(e);
        } else {
            log.error("BusinessException", e);
        }
        writeStructuredError("BusinessException", e, e.getCode());
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        writeStructuredError("RuntimeException", e, ErrorCode.SYSTEM_ERROR.getCode());
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> handleValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        writeStructuredError("MethodArgumentNotValidException", ex, ErrorCode.PARAMS_ERROR.getCode());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, msg);
    }

    /**
     * 典型“客户端请求错误”（4xx 场景）统一处理：
     * - 开发/测试环境输出详细请求信息，便于定位前端误调
     * - prod 完全静默，避免恶意畸形请求制造 ERROR 堆栈和 api_error 噪声
     * - dev/test 保留详细请求信息与结构化 api_error，便于定位前端误调
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public BaseResponse<?> handleClientBadRequest(Exception ex) {
        if (!isProdLike()) {
            logBadRequestIfNonProd(ex.getClass().getSimpleName(), ex.getMessage());
            writeStructuredError(ex.getClass().getSimpleName(), ex, ErrorCode.PARAMS_ERROR.getCode());
        }
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求参数错误");
    }

    /**
     * Accept 与接口 produces 不兼容时不能再返回 JSON，否则会在异常处理阶段再次触发内容协商失败。
     * 直接返回无响应体的 406，避免攻击流量制造二次 WARN 堆栈。
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        if (!isProdLike()) {
            logBadRequestIfNonProd(ex.getClass().getSimpleName(), ex.getMessage());
            writeStructuredError(ex.getClass().getSimpleName(), ex, ErrorCode.PARAMS_ERROR.getCode());
        }
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    /**
     * 请求方法不支持（常见于前端路径/方法配置错误，或旧版本误调）
     * 目标：记录清晰的请求信息，但不刷堆栈。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public BaseResponse<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String path = null;
        String method = ex.getMethod();
        String ip = null;
        String userAgent = null;
        String referer = null;
        String origin = null;
        String supported = ex.getSupportedHttpMethods() == null ? null : ex.getSupportedHttpMethods().toString();

        if (attrs != null) {
            var req = attrs.getRequest();
            path = req.getRequestURI();
            String qs = req.getQueryString();
            if (qs != null && !qs.isBlank()) {
                path = path + "?" + qs;
            }
            ip = NetUtils.getIpAddress(req);
            userAgent = req.getHeader("User-Agent");
            referer = req.getHeader("Referer");
            origin = req.getHeader("Origin");
        }

        // 不把 ex 作为 logger 参数传入，避免输出堆栈；是否输出由 badRequestLogger 的级别控制
        badRequestLogger.info("HttpRequestMethodNotSupported: {} {} (supported: {}), ip={}, origin={}, referer={}, ua={}",
                method, path, supported, ip, origin, referer, userAgent);
        writeStructuredError("HttpRequestMethodNotSupportedException", ex, ErrorCode.PARAMS_ERROR.getCode());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求方法不支持，请检查接口路径与请求方式");
    }

    /**
     * 客户端主动断开连接（SSE/流式场景下用户取消、页面跳转等）：这是正常行为，降级为 WARN，不打堆栈。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void asyncRequestNotUsableHandler(AsyncRequestNotUsableException e) {
        log.warn("[AsyncRequest] client disconnected: {}", e.getMessage());
        // 连接已断开，无法写响应，直接返回
    }

    /**
     * 兼容“前端误调旧路径 / 静态资源误命中”的场景：返回干净的 404 JSON，避免刷堆栈。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public BaseResponse<?> handleNoResourceFound(NoResourceFoundException ex) {
        // 这里属于“请求路径不匹配当前项目接口”，不当成系统异常
        logBadRequestIfNonProd("NoResourceFoundException", ex.getMessage());
        writeStructuredError("NoResourceFoundException", ex, ErrorCode.NOT_FOUND_ERROR.getCode());
        return ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "请求路径不匹配当前项目接口,已提前抛出(请检查接口是否存在以及接口参数是否正确)");
    }

    /**
     * 兜底：处理所有未被上面捕获的异常（避免返回 Spring 默认错误页/栈信息）
     */
    @ExceptionHandler(Exception.class)
    public BaseResponse<?> exceptionHandler(Exception e) {
        log.error("Exception", e);
        writeStructuredError("Exception", e, ErrorCode.SYSTEM_ERROR.getCode());
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    private void writeStructuredError(String type, Exception e, int code) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String uri = null;
        String method = null;
        String ip = null;
        if (attrs != null) {
            var req = attrs.getRequest();
            uri = req.getRequestURI();
            method = req.getMethod();
            ip = NetUtils.getIpAddress(req);
        }
        accessLogger.info("api_error",
                StructuredArguments.keyValue("eventName", "api_error"),
                StructuredArguments.keyValue("errorType", type),
                StructuredArguments.keyValue("status", code),
                StructuredArguments.keyValue("path", uri),
                StructuredArguments.keyValue("method", method),
                StructuredArguments.keyValue("ip", ip),
                StructuredArguments.keyValue("traceId", MDC.get("traceId")),
                StructuredArguments.keyValue("message", e.getMessage()),
                StructuredArguments.keyValue("log_type", "access")
        );
    }

    private void logBadRequestIfNonProd(String errorType, String errorMessage) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String path = null;
        String method = null;
        String ip = null;
        String userAgent = null;
        String referer = null;
        String origin = null;
        if (attrs != null) {
            var req = attrs.getRequest();
            method = req.getMethod();
            path = req.getRequestURI();
            String qs = req.getQueryString();
            if (qs != null && !qs.isBlank()) {
                path = path + "?" + qs;
            }
            ip = NetUtils.getIpAddress(req);
            userAgent = req.getHeader("User-Agent");
            referer = req.getHeader("Referer");
            origin = req.getHeader("Origin");
        }
        badRequestLogger.info("BadRequest: type={}, {} {}, ip={}, origin={}, referer={}, ua={}, msg={}",
                errorType, method, path, ip, origin, referer, userAgent, errorMessage);
    }

    private boolean isProdLike() {
        return environment != null && environment.acceptsProfiles(Profiles.of("prod", "production"));
    }

    private void logBusinessExceptionNoStack(BusinessException e) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String path = null;
        String method = null;
        String ip = null;
        if (attrs != null) {
            var req = attrs.getRequest();
            method = req.getMethod();
            path = req.getRequestURI();
            String qs = req.getQueryString();
            if (qs != null && !qs.isBlank()) {
                path = path + "?" + qs;
            }
            ip = NetUtils.getIpAddress(req);
        }
        // 使用 badRequestLogger，便于按环境/配置一键静默；且不传 Throwable 避免打印堆栈
        badRequestLogger.warn("BusinessException: code={}, {} {}, ip={}, traceId={}, msg={}",
                e.getCode(), method, path, ip, MDC.get("traceId"), e.getMessage());
    }
}
