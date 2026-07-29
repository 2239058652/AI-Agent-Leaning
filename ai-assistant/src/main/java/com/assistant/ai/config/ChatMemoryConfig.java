package com.assistant.ai.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆配置（练习版 - 阶段6 巩固期）
 * 目的：理解「记忆管道」是如何把库里的聊天记录接进模型的。
 * 核心：Advisor 把历史查/写到库，框架在调模型前后自动调用。
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)   // 真正读写库的实现
                .maxMessages(20)                               // 窗口策略
                .build();
    }
}
