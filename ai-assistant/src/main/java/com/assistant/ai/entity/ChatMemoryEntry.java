package com.assistant.ai.entity;

import lombok.Data;

/**
 * 对话记忆的一条消息 — 对应 chat_memory 表
 */
@Data
public class ChatMemoryEntry {

    private Long id;

    /** 会话 ID */
    private String conversationId;

    /** 消息在会话中的序号（顺序就是对话顺序，不能乱） */
    private Integer messageIndex;

    /** 消息类型：USER / ASSISTANT / SYSTEM / TOOL */
    private String messageType;

    /** 消息文本内容 */
    private String content;
}
