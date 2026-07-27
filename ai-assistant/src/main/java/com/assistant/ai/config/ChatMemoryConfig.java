package com.assistant.ai.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话记忆配置
 * <p>
 * MessageWindowChatMemory：滑动窗口策略，超过 maxMessages 时淘汰最旧的消息
 * （SystemMessage 特殊：始终保留，且新的 SystemMessage 会替换旧的）。
 * <p>
 * 底层存储（6.5 Checkpoint）：注入 MybatisChatMemoryRepository，对话历史落 MySQL，
 * 重启不失忆。窗口策略和存储介质是两层，换存储不影响窗口逻辑。
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }
}
