package com.assistant.ai.tool;

import com.assistant.ai.security.AgentAuthContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PendingConfirmationStore {

    private final Map<String, PendingConfirmation> pending = new ConcurrentHashMap<>();

    /**
     * 服务端保存可信的原始操作，并只返回随机 ID
     */
    public String create(String toolName, String argsJson, String conversationId, AgentAuthContext authContext) {
        String confirmationId = UUID.randomUUID().toString();
        pending.put(
                confirmationId,
                new PendingConfirmation(toolName, argsJson, conversationId, authContext)
        );
        return confirmationId;
    }

    /**
     * 读取后立即删除，实现一次性消费
     */
    public PendingConfirmation consume(String confirmationId, String userId) {
        final PendingConfirmation[] result = new PendingConfirmation[1];

        pending.computeIfPresent(confirmationId, (id, pendingConfirmation) -> {
            if (pendingConfirmation.authContext().userId().equals(userId)) {
                result[0] = pendingConfirmation;
                return null;
            }
            return pendingConfirmation;
        });

        return result[0];
    }

    public void discard(String confirmationId) {
        pending.remove(confirmationId);
    }

    public record PendingConfirmation(
            String toolName,
            String argsJson,
            String conversationId,
            AgentAuthContext authContext
    ) {
    }
}
