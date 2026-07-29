package com.assistant.ai.memory;

import com.assistant.ai.entity.ChatMemoryEntry;
import com.assistant.ai.mapper.ChatMemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * ChatMemoryRepository 的 MyBatis 实现（练习版 - 阶段6 巩固期）
 * <p>
 * 目的：理解「真正查库」是如何发生的。
 * 关键：这是框架 SPI 的实现点，上层（窗口策略、Advisor）不关心底下是 MySQL 还是 Redis。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MybatisChatMemoryRepository implements ChatMemoryRepository {

    private final ChatMemoryMapper mapper;

    @Override
    public List<String> findConversationIds() {
        return mapper.findConversationIds();
    }

    @Override
    public List<Message> findByConversationId(@NonNull String conversationId) {
        // 真正查库的入口
        return mapper.findByConversationId(conversationId).stream()
                .map(this::toMessage)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 接口约定：用传入的 messages 整体替换该会话已有的全部消息 — 不是追加！
     * 这是「整体替换」语义，窗口淘汰后旧消息会被删掉。
     */
    @Override
    @Transactional
    public void saveAll(@NonNull String conversationId, List<Message> messages) {
        // 先删后插 + 事务，保证原子性
        mapper.deleteByConversationId(conversationId);
        for (int i = 0; i < messages.size(); i++) {
            mapper.insert(toEntry(conversationId, i, messages.get(i)));
        }
    }

    @Override
    public void deleteByConversationId(@NonNull String conversationId) {
        mapper.deleteByConversationId(conversationId);
    }

    // ========================================================================
    // Spring AI Message <-> 数据库行 的互转
    // ========================================================================

    private ChatMemoryEntry toEntry(String conversationId, int index, Message message) {
        ChatMemoryEntry entry = new ChatMemoryEntry();
        entry.setConversationId(conversationId);
        entry.setMessageIndex(index);
        entry.setMessageType(message.getMessageType().name());
        entry.setContent(message.getText());
        return entry;
    }

    private Message toMessage(ChatMemoryEntry entry) {
        return switch (entry.getMessageType()) {
            case "USER" -> new UserMessage(entry.getContent());
            case "ASSISTANT" -> new AssistantMessage(entry.getContent());
            case "SYSTEM" -> new SystemMessage(entry.getContent());
            default -> {
                log.warn("跳过无法还原的消息类型: {}", entry.getMessageType());
                yield null;
            }
        };
    }
}
