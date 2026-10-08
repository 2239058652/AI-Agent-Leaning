package com.assistant.ai.tool;

import com.assistant.ai.audit.AuditContext;
import com.assistant.ai.audit.AuditEvent;
import com.assistant.ai.audit.AuditExecutionHolder;
import com.assistant.ai.audit.AuditRecorder;
import com.assistant.ai.audit.AuditTracer;
import com.assistant.ai.security.AgentAuthContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 工具代理 — 把本地工具注册为 Spring AI 的 FunctionCallback
 * <p>
 * 设计思路：
 * Spring AI 负责 Agent Loop（调模型 → 调工具 → 再调模型）
 * ToolService 负责工具执行（安全检查 → 参数校验 → 敏感操作确认）
 * 这个类是桥梁：让 Spring AI 调工具时走 ToolService
 */
@Component
@RequiredArgsConstructor
public class ToolCallbackProvider {

    private final ToolRegistry toolRegistry;
    private final ToolService toolService;
    private final ObjectMapper objectMapper;
    private final PendingConfirmationStore pendingConfirmationStore;
    private final AuditRecorder auditRecorder;
    private final AuditTracer auditTracer;

    /**
     * 获取所有工具的 ToolCallback 列表
     * ChatClient 注册这些 callback 后，模型就能调用对应的工具
     */
    public List<ToolCallback> getToolCallbacks() {
        return toolRegistry.getAllTools().stream()
                .map(this::createCallback)
                .toList();
    }

    /**
     * 为单个工具创建 ToolCallback
     * <p>
     * 模型看到的：一个有名称、描述、参数 schema 的函数
     * 实际执行的：ToolService.execute()（含白名单、参数校验、敏感检查）
     */
    private ToolCallback createCallback(ToolDefinition toolDef) {
        // 工具执行逻辑：接收参数 Map，转成 JSON 字符串后委托给 ToolService
        BiFunction<Map<String, Object>, ToolContext, String> toolFunction = (args, ctx) -> {
            String argsJson;
            String executionId = (String) ctx.getContext().get("executionId");
            String conversationId = (String) ctx.getContext().get("conversationId");
            AgentAuthContext authContext =
                    (AgentAuthContext) ctx.getContext().get("authContext");
            AuditContext execution = AuditExecutionHolder.get(executionId);
            String spanType = toolDef.getSource() == ToolDefinition.ToolSource.MCP ? "mcp" : "tool";
            // 一个工具调用只有一个 span：编号在这里分配一次，本次调用的所有事件共用
            Integer spanIndex = executionId == null
                    ? null : AuditContext.allocateSpanIndex(executionId, spanType);
            Span span = auditTracer.start(spanType, execution, toolDef.getName());
            try {
                // Spring AI 传过来的是 Map，需要转回 JSON 字符串给 ToolService
                argsJson = objectMapper.writeValueAsString(args);
            } catch (Exception e) {
                auditRecorder.record(AuditEvent.builder()
                        .executionId(executionId)
                        .userId(authContext == null ? null : authContext.userId())
                        .eventType("tool_failed")
                        .spanType(spanType)
                        .spanIndex(spanIndex)
                        .toolName(toolDef.getName())
                        .sensitive(toolDef.isSensitive())
                        .success(false)
                        .reason("参数序列化失败")
                        .build());
                auditTracer.fail(span, "参数序列化失败");
                return "参数序列化失败: " + e.getMessage();
            }
            auditRecorder.record(AuditEvent.builder()
                    .executionId(executionId)
                    .userId(authContext == null ? null : authContext.userId())
                    .conversationId(conversationId)
                    .eventType("tool_called")
                    .spanType(spanType)
                    .spanIndex(spanIndex)
                    .toolName(toolDef.getName())
                    .argsDigest(argsJson)
                    .sensitive(toolDef.isSensitive())
                    .build());
            ToolResult result;
            try (Scope ignored = auditTracer.open(span)) {
                result = toolService.execute(toolDef.getName(), argsJson, authContext);
            }
            boolean failed = !result.isNeedsConfirmation()
                    && result.getResult() != null
                    && result.getResult().startsWith("错误");
            auditRecorder.record(AuditEvent.builder()
                    .executionId(executionId)
                    .userId(authContext == null ? null : authContext.userId())
                    .conversationId(conversationId)
                    .eventType(result.isNeedsConfirmation() ? "confirmation_required"
                            : failed ? "tool_failed" : "tool_succeeded")
                    .spanType(spanType)
                    .spanIndex(spanIndex)
                    .toolName(toolDef.getName())
                    .sensitive(toolDef.isSensitive() || result.isNeedsConfirmation())
                    .success(result.isNeedsConfirmation() ? null : !failed)
                    .reason(failed ? result.getResult() : null)
                    .build());
            if (failed) {
                auditTracer.fail(span, result.getResult());
            } else {
                auditTracer.succeed(span);
            }

            if (result.isNeedsConfirmation()) {
                SseEmitter emitter = (SseEmitter) ctx.getContext().get("emitter");

                if (emitter == null || conversationId == null || conversationId.isBlank()) {
                    return "此操作为敏感操作，当前调用方式不支持确认流程，已拒绝执行。";
                }

                String confirmationId = pendingConfirmationStore.create(
                        result.getToolName(),
                        result.getArgsJson(),
                        conversationId,
                        authContext,
                        executionId
                );

                try {
                    ObjectNode confirmEvent = objectMapper.createObjectNode();
                    confirmEvent.put("confirmationId", confirmationId);
                    confirmEvent.put("message", "操作需要确认：取消订单" + argsJson);

                    emitter.send(SseEmitter.event()
                            .name("confirmation_required")
                            .data(objectMapper.writeValueAsString(confirmEvent)));
                } catch (Exception e) {
                    pendingConfirmationStore.discard(confirmationId);
                    return "通知前端失败：" + e.getMessage();
                }

                return "操作尚未执行。系统已在界面上弹出确认框，请告知用户在弹窗中点击确认或取消，"
                        + "不要让用户用文字回复确认，也不要声称操作已完成。";
            }
            return result.getResult();
        };

        // 从 ToolDefinition 获取 JSON Schema
        String schema = toolDef.getParameters() != null
                ? toolDef.getParameters().toString()
                : "{\"type\":\"object\",\"properties\":{}}";

        return FunctionToolCallback.<Map<String, Object>, String>builder(toolDef.getName(), toolFunction)
                .description(toolDef.getDescription())
                .inputType(Map.class)
                .inputSchema(schema)
                .build();
    }
}
