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
 * ChatMemoryRepository 的 MyBatis 实现 — 对话记忆落 MySQL
 * <p>
 * 这是 Spring AI 的 SPI 扩展点：框架只认 ChatMemoryRepository 接口，
 * 底下是官方 JDBC、Redis 还是我们手写的 MyBatis，上层（窗口策略、Advisor）都不用改。
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
        return mapper.findByConversationId(conversationId).stream()
                .map(this::toMessage)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 接口约定（见 ChatMemoryRepository 的 javadoc）：
     * 用传入的 messages 整体替换该会话已有的全部消息 — 不是追加！
     */
    @Override
    @Transactional
    public void saveAll(@NonNull String conversationId, List<Message> messages) {
        // 实现"整体替换"语义
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

    /**
     * Message 转数据库行。messageType 存的是 MessageType 枚举名（USER/ASSISTANT/...）
     */
    private ChatMemoryEntry toEntry(String conversationId, int index, Message message) {
        ChatMemoryEntry entry = new ChatMemoryEntry();
        entry.setConversationId(conversationId);
        entry.setMessageIndex(index);
        entry.setMessageType(message.getMessageType().name());
        entry.setContent(message.getText());
        return entry;
    }

    /**
     * 数据库行还原成 Message。TOOL 类型只存了文本还原不出完整结构，跳过
     */
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
