package com.assistant.ai.exception;

import lombok.Getter;

/**
 * 业务异常 — Service 层用来抛出明确的业务错误
 * <p>
 * 比起返回 null 或错误字符串，抛异常能让 GlobalExceptionHandler 统一处理，
 * 调用方不需要每个方法都写 if-else 判断返回值是否代表失败。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
