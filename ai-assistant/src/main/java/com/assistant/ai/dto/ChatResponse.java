package com.assistant.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 聊天响应 — 返回给前端的结果
 */
@Data
@AllArgsConstructor
public class ChatResponse {
    /** 是否成功 */
    private boolean success;

    /** 模型回复内容 */
    private String content;

    /** 错误信息（失败时） */
    private String error;

    /** 输入 token 数 */
    private int promptTokens;

    /** 输出 token 数 */
    private int completionTokens;

    public static ChatResponse ok(String content, int promptTokens, int completionTokens) {
        return new ChatResponse(true, content, null, promptTokens, completionTokens);
    }

    public static ChatResponse fail(String error) {
        return new ChatResponse(false, null, error, 0, 0);
    }
}
