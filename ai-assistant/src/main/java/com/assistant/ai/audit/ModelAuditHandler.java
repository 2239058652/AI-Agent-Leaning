package com.assistant.ai.audit;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.opentelemetry.api.trace.Span;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * 每次真正发出的模型请求记一层。
 * <p>
 * 智谱的工具循环在模型内部：工具返回后会再次调用 stream，因此每次调用都会经过这里。
 */
@Component
public class ModelAuditHandler implements ObservationHandler<ChatModelObservationContext> {

    private final AuditRecorder auditRecorder;
    private final AuditTracer auditTracer;

    public ModelAuditHandler(AuditRecorder auditRecorder, AuditTracer auditTracer) {
        this.auditRecorder = auditRecorder;
        this.auditTracer = auditTracer;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ChatModelObservationContext;
    }

    @Override
    public void onStop(ChatModelObservationContext context) {
        record(context, context.getError() == null, null);
    }

    @Override
    public void onError(ChatModelObservationContext context) {
        Throwable error = context.getError();
        record(context, false, error == null ? "模型调用失败" : error.getClass().getSimpleName());
    }

    private void record(ChatModelObservationContext context, boolean success, String failure) {
        AuditContext auditContext = execution(context);
        if (auditContext == null) {
            return;
        }
        Prompt prompt = context.getRequest();
        String model = prompt.getOptions() == null ? null : prompt.getOptions().getModel();
        // 每次模型请求是一个 span：编号在这里分配，事件直接带走
        Integer spanIndex = auditContext.nextSpanIndex("model");
        auditRecorder.record(AuditEvent.builder()
                .executionId(auditContext.executionId())
                .userId(auditContext.userId())
                .conversationId(auditContext.conversationId())
                .eventType(success ? "model_completed" : "model_failed")
                .spanType("model")
                .spanIndex(spanIndex)
                .success(success)
                .reason(failure == null ? model : failure)
                .build());
        Span span = auditTracer.start("model", auditContext, null);
        if (success) {
            auditTracer.succeed(span);
        } else {
            auditTracer.fail(span, failure);
        }
    }

    private AuditContext execution(ChatModelObservationContext context) {
        Prompt prompt = context.getRequest();
        if (!(prompt.getOptions() instanceof org.springframework.ai.model.tool.ToolCallingChatOptions options)
                || options.getToolContext() == null) {
            return null;
        }
        Object executionId = options.getToolContext().get("executionId");
        return executionId instanceof String id ? AuditExecutionHolder.get(id) : null;
    }
}
