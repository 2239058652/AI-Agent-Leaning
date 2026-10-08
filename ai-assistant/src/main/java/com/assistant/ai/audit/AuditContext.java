package com.assistant.ai.audit;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一次请求里的审计身份，并负责分配 span 编号。
 * <p>
 * 身份部分供模型线程回调时读取；编号按 (executionId, spanType) 计数，
 * 在 span 起点分配一次，该 span 的所有事件共用。
 */
public record AuditContext(
        String executionId,
        String userId,
        String conversationId,
        Context traceContext,
        Span executionSpan
) {

    public AuditContext(String executionId, String userId, String conversationId) {
        this(executionId, userId, conversationId, Context.current(), null);
    }

    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicInteger>> INDEXES =
            new ConcurrentHashMap<>();

    /**
     * 在该层级分配一个新的 span 编号。
     * <p>
     * 只在 span 起点调用一次；这个 span 产生的所有事件共用同一个编号。
     */
    public int nextSpanIndex(String spanType) {
        return nextIndex(executionId, spanType);
    }

    /**
     * 分配 span 编号。执行还在进行中就用它的上下文，执行已经结束（确认操作）时
     * 仍按同一个 executionId 继续编号。
     */
    public static int allocateSpanIndex(String executionId, String spanType) {
        AuditContext context = AuditExecutionHolder.get(executionId);
        return context != null
                ? context.nextSpanIndex(spanType)
                : nextIndex(executionId, spanType);
    }

    private static int nextIndex(String executionId, String spanType) {
        return INDEXES.computeIfAbsent(executionId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(spanType, ignored -> new AtomicInteger())
                .incrementAndGet();
    }
}
