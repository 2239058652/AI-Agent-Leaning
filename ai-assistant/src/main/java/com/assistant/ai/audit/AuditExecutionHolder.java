package com.assistant.ai.audit;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 进行中的 Agent 执行。模型回调不保证和请求在同一线程，所以按 executionId 保存。
 */
public final class AuditExecutionHolder {

    private static final ConcurrentHashMap<String, AuditContext> EXECUTIONS = new ConcurrentHashMap<>();

    private AuditExecutionHolder() {
    }

    public static void put(AuditContext context) {
        EXECUTIONS.put(context.executionId(), context);
    }

    public static AuditContext get(String executionId) {
        return executionId == null ? null : EXECUTIONS.get(executionId);
    }

    public static void remove(String executionId) {
        if (executionId != null) {
            EXECUTIONS.remove(executionId);
        }
    }
}
