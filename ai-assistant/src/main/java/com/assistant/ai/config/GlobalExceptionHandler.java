package com.assistant.ai.config;

import com.assistant.ai.dto.ApiResponse;
import com.assistant.ai.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 — 所有 Controller 抛出的异常都走这里
 * <p>
 * 为什么需要这个？
 * 1. 没有它，参数校验失败会返回 Spring 默认的 400 错误格式（白屏+堆栈），前端没法解析
 * 2. 没有它，未捕获异常会返回 500 + 完整堆栈，泄露内部实现细节
 * 3. 有了它，所有错误都走统一的 ApiResponse 格式，前端只需要处理一种结构
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数校验失败 — @Valid 触发的异常
     * 例如：message 为空、超长
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return ApiResponse.fail("VALIDATION_ERROR", message);
    }

    /**
     * 表单绑定失败 — @ModelAttribute + @Valid 触发的异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBind(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("表单绑定失败: {}", message);
        return ApiResponse.fail("VALIDATION_ERROR", message);
    }

    /**
     * 业务异常 — Service 层主动抛出的
     * 例如：LLM 调用失败、工具不存在、权限不足
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException ex) {
        log.warn("业务异常 [{}]: {}", ex.getCode(), ex.getMessage());
        return ApiResponse.fail(ex.getCode(), ex.getMessage());
    }

    /**
     * 兜底 — 未预料到的异常
     * 返回 500，但不暴露堆栈给前端
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnknown(Exception ex) {
        log.error("未知异常", ex);
        return ApiResponse.fail("INTERNAL_ERROR", "服务内部错误，请稍后重试");
    }
}
