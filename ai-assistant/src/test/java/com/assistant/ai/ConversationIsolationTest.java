package com.assistant.ai;

import com.assistant.ai.config.LlmProperties;
import com.assistant.ai.knowledge.KnowledgeQuery;
import com.assistant.ai.service.ChatService;
import com.assistant.ai.tool.PendingConfirmationStore;
import com.assistant.ai.tool.ToolCallbackProvider;
import com.assistant.ai.tool.ToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import static org.mockito.Mockito.verify;

class ConversationIsolationTest {

    private ChatMemory chatMemory;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatMemory = Mockito.mock(ChatMemory.class);
        chatService = new ChatService(
                Mockito.mock(LlmProperties.class),
                Mockito.mock(ToolService.class),
                Mockito.mock(ChatClient.Builder.class),
                Mockito.mock(ToolCallbackProvider.class),
                chatMemory,
                new PendingConfirmationStore(),
                Mockito.mock(KnowledgeQuery.class)
        );
    }

    @Test
    void deleteUsesUserPrefixedConversationId() {
        chatService.deleteByConversationId("same-id", "user1");
        verify(chatMemory).clear("user1:same-id");

        chatService.deleteByConversationId("same-id", "user2");
        verify(chatMemory).clear("user2:same-id");
    }
}