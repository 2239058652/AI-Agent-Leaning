package com.assistant.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * 聊天请求 — 前端发过来的消息
 */
@Data
public class ChatRequest {
    /** 用户消息 */
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
