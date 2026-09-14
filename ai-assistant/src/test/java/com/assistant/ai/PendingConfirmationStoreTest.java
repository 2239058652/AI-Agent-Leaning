package com.assistant.ai;

import com.assistant.ai.security.AgentAuthContext;
import com.assistant.ai.tool.PendingConfirmationStore;
import com.assistant.ai.tool.PendingConfirmationStore.PendingConfirmation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PendingConfirmationStoreTest {

    private final AgentAuthContext user1 =
            new AgentAuthContext("user1", Set.of("ROLE_USER"), "test-token");
    private final AgentAuthContext user2 =
            new AgentAuthContext("user2", Set.of("ROLE_USER"), "test-token");

    private PendingConfirmationStore store;

    @BeforeEach
    void setUp() {
        store = new PendingConfirmationStore();
    }

    @Test
    void ownerCanConsumeOnce() {
        String id = store.create("cancel_order", "{\"order_no\":\"ORD001\"}", "conv-1", user1);

        PendingConfirmation first = store.consume(id, user1.userId());
        assertNotNull(first);
        assertEquals("cancel_order", first.toolName());
        assertEquals("user1", first.authContext().userId());

        PendingConfirmation second = store.consume(id, user1.userId());
        assertNull(second, "确认只能消费一次");
    }

    @Test
    void otherUserCannotConsumeOrDelete() {
        String id = store.create("cancel_order", "{\"order_no\":\"ORD001\"}", "conv-1", user1);

        assertNull(store.consume(id, user2.userId()), "别人不能拿这个确认 ID 执行");

        PendingConfirmation owner = store.consume(id, user1.userId());
        assertNotNull(owner, "别人偷用不掉，真正的主人仍能消费");
        assertEquals("user1", owner.authContext().userId());
    }
}