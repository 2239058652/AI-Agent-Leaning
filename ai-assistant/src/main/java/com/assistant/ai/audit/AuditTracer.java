package com.assistant.ai.audit;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Component;

/**
 * 一次 Agent 执行对应一个 trace。执行、模型、工具和 MCP 各是一个 span。
 * <p>
 * 只交给本地日志导出器，不连接外部追踪系统。
 */
@Component
public class AuditTracer {

    private final Tracer tracer;

    public AuditTracer(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("ai-assistant");
    }

    public Span startExecution(String executionId, String userId, String conversationId) {
        return tracer.spanBuilder("execution")
                .setAttribute("execution.id", executionId)
                .setAttribute("user.id", userId)
                .setAttribute("conversation.id", conversationId)
                .startSpan();
    }

    public Span start(String spanName, AuditContext execution, String toolName) {
        Span parent = execution == null ? null : execution.executionSpan();
        var builder = tracer.spanBuilder(spanName);
        if (parent != null) {
            builder.setParent(Context.current().with(parent));
        }
        if (execution != null) {
            builder.setAttribute("execution.id", execution.executionId());
        }
        if (toolName != null) {
            builder.setAttribute("tool.name", toolName);
        }
        return builder.startSpan();
    }

    public Scope open(Span span) {
        return span.makeCurrent();
    }

    public void succeed(Span span) {
        if (span != null) {
            span.setStatus(StatusCode.OK);
            span.end();
        }
    }

    public void fail(Span span, String reason) {
        if (span != null) {
            span.setStatus(StatusCode.ERROR, reason == null ? "failed" : reason);
            span.end();
        }
    }
}
