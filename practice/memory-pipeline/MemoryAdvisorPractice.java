package com.assistant.ai.memory;

import com.assistant.ai.entity.ChatMemoryEntry;
import com.assistant.ai.mapper.ChatMemoryMapper;
import com.assistant.ai.service.ChatService;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

// 练习版：把 Advisor 挂钩到 Repository + Mapper（完整可改版）
@Component
public class MemoryAdvisorPractice {

    private final ChatMemory chatMemory;
    private final ChatMemoryMapper mapper;
    private final ChatService chatService;

    public MemoryAdvisorPractice(ChatMemory chatMemory, ChatMemoryMapper mapper, ChatService chatService) {
        this.chatMemory = chatMemory;
        this.mapper = mapper;
        this.chatService = chatService;
    }

    public MessageChatMemoryAdvisor memoryAdvisor(ChatRequest request) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .onBeforeCompletion(this::onBeforeCompletion)
                .onAfterCompletion(this::onAfterCompletion)
                .build();
    }

    private void onBeforeCompletion(Prompt prompt, ChatResponse response) {
        String conversationId = prompt.getOptions().get("conversationId");
        List<ChatMemoryEntry> history = mapper.findByConversationId(conversationId);

        // 练习点：把查到的数据塞进 Prompt
        // 这里需要把 history 注入到 PromptTemplate
        System.out.println("查库完成，记录数：" + history.size());

        // 示例：如果有订单历史，可以打印
        if (!history.isEmpty()) {
            System.out.println("有订单记录，准备拼 Prompt");
        }
    }

    private void onAfterCompletion(Prompt prompt, ChatResponse response) {
        // 练习：把新消息存回库
        System.out.println("新消息已存入库");
    }
}