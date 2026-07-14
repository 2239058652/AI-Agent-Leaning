package com.assistant.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 聊天请求 — 前端发过来的消息
 */
@Data
public class ChatRequest {
    /** 用户消息 */
    @NotBlank(message = "message 不能为空")
    @Size(max = 10000, message = "message 不能超过 10000 字符")
    private String message;

    /** 系统提示词（可选） */
    private String systemPrompt;

    /** 对话历史（可选，多轮对话时传入） */
    private List<Message> history;

    /**
     * 对话历史中的一条消息
     */
    @Data
    public static class Message {
        private String role;     // "user" 或 "assistant"
        private String content;
    }
}
