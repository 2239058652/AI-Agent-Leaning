package com.assistant.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一响应格式 — 所有接口返回这个结构
 * <p>
 * 成功时：{ "success": true, "data": ... }
 * 失败时：{ "success": false, "error": { "code": "...", "message": "..." } }
 */
@Data
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private ErrorBody error;

    @Data
    @AllArgsConstructor
    public static class ErrorBody {
        private String code;
        private String message;
    }

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.success = true;
        resp.data = data;
        return resp;
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.success = false;
        resp.error = new ErrorBody(code, message);
        return resp;
    }
}
