package com.assistant.ai.service;

import com.assistant.ai.config.LlmProperties;
import com.assistant.ai.dto.ChatRequest;
import com.assistant.ai.dto.ChatResponse;
import com.assistant.ai.security.AgentAuthContext;
import com.assistant.ai.tool.PendingConfirmationStore;
import com.assistant.ai.tool.ToolCallbackProvider;
import com.assistant.ai.tool.ToolResult;
import com.assistant.ai.tool.ToolService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.zhipuai.ZhiPuAiAssistantMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 聊天服务 — 对接 Spring AI，处理模型调用和 Agent Loop
 * <p>
 * 三个公开接口：
 * - chat(): 非流式，一次性返回完整回复
 * - chatStream(): 流式，逐字输出
 * - chatStreamWithTools(): 流式 + 工具调用（Agent Loop）
 * <p>
 * 工具调用通过 ToolCallbackProvider 注册给 Spring AI，
 * Spring AI 自动处理"调工具 → 拿结果 → 再调模型"的循环。
 * ToolService 负责安全检查（白名单、参数校验、敏感操作确认）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final LlmProperties llmProperties;
    private final ToolService toolService;
    private final ChatClient.Builder chatClientBuilder;
    private final ToolCallbackProvider toolCallbackProvider;
    private final ChatMemory chatMemory;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private final PendingConfirmationStore pendingConfirmationStore;

    // ========================================================================
    // 执行已确认的敏感操作
    // ========================================================================

    /**
     * 用户在前端确认后，调用此方法执行。
     * 不走 Agent Loop，直接执行工具并返回结果。
     */
    public ChatResponse executeConfirmed(String confirmationId, String userId) {
        PendingConfirmationStore.PendingConfirmation pending =
                pendingConfirmationStore.consume(confirmationId, userId);

        if (pending == null) {
            throw new IllegalArgumentException("确认请求不存在、已使用或无权操作");
        }

        ToolResult result = toolService.executeConfirmed(
                pending.toolName(),
                pending.argsJson(),
                pending.authContext()
        );

        try {
            chatMemory.add(
                    pending.conversationId(),
                    new AssistantMessage(result.getResult())
            );
        } catch (Exception e) {
            log.error("确认结果写入记忆失败", e);
        }

        return ChatResponse.ok(result.getResult(), 0, 0);
    }

    // ========================================================================
    // 非流式聊天
    // ========================================================================

    public ChatResponse chat(ChatRequest request, String userId) {
        try {
            log.info("LLM 非流式请求(Spring AI): model={}", llmProperties.getModel());

            ChatClient chatClient = chatClientBuilder.build();
            var promptSpec = chatClient.prompt();
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
                promptSpec = promptSpec.system(request.getSystemPrompt());
            }
            promptSpec = promptSpec.user(request.getMessage());
            // 1.1.8：会话 ID 改从请求上下文传入（键见 ChatMemory.CONVERSATION_ID），
            // Advisor 本身不再持有会话 ID；param() 把值放进 Advisor 可见的上下文
            promptSpec = promptSpec.advisors(spec -> spec
                    .advisors(memoryAdvisor())
                    .param(ChatMemory.CONVERSATION_ID, resolveConversationId(request.getConversationId(), userId)));

            String content = promptSpec.call().content();
            log.info("LLM 响应(Spring AI): content长度={}", content == null ? "null" : content.length());

            if (content == null) {
                return ChatResponse.fail("模型返回空内容");
            }
            return ChatResponse.ok(content, 0, 0);

        } catch (Exception e) {
            log.error("LLM 请求异常(Spring AI)", e);
            return ChatResponse.fail("请求异常: " + e.getMessage());
        }
    }

    // ========================================================================
    // 流式聊天（不带工具）
    // ========================================================================

    public void chatStream(ChatRequest request, SseEmitter emitter, String userId) {
        executor.execute(() -> {
            try {
                log.info("流式聊天(Spring AI): {}", request.getMessage());

                ChatClient chatClient = chatClientBuilder.build();
                var promptSpec = chatClient.prompt();
                if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
                    promptSpec = promptSpec.system(request.getSystemPrompt());
                }
                promptSpec = promptSpec.user(request.getMessage());
                // 1.1.8：会话 ID 改从请求上下文传入（键见 ChatMemory.CONVERSATION_ID）
                promptSpec = promptSpec.advisors(spec -> spec
                        .advisors(memoryAdvisor())
                        .param(ChatMemory.CONVERSATION_ID, resolveConversationId(request.getConversationId(), userId)));

                // 用 chatResponse() 而不是 content()：
                // 每个流式块同时携带「可见文本（getText）」和「思考内容（智谱扩展字段）」，
                // 由 forwardChatResponseEvents 拆成 chunk / reasoning 两个 SSE 事件分别转发
                // 注：全限定名，避免与项目 DTO ChatResponse 冲突
                reactor.core.publisher.Flux<org.springframework.ai.chat.model.ChatResponse> flux = promptSpec.stream().chatResponse();

                flux.subscribe(
                        response -> forwardChatResponseEvents(response, emitter),
                        error -> {
                            log.error("流式聊天(Spring AI)异常", error);
                            emitter.completeWithError(error);
                        },
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                emitter.complete();
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        }
                );

            } catch (Exception e) {
                log.error("流式聊天(Spring AI)启动异常", e);
                emitter.completeWithError(e);
            }
        });
    }

    // ========================================================================
    // 流式聊天（带工具 Agent Loop）
    // ========================================================================

    public void chatStreamWithTools(ChatRequest request, SseEmitter emitter, AgentAuthContext authContext) {
        executor.execute(() -> {
            try {
                log.info("带工具流式聊天(Spring AI): {}", request.getMessage());

                var toolCallbacks = toolCallbackProvider.getToolCallbacks();
                log.info("注册 {} 个工具给 Spring AI", toolCallbacks.size());

                ChatClient chatClient = chatClientBuilder.build();
                var promptSpec = chatClient.prompt();
                if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
                    promptSpec = promptSpec.system(request.getSystemPrompt());
                }
                promptSpec = promptSpec.user(request.getMessage());

                // 会话 ID 只解析一次：Advisor 上下文 与 工具上下文（确认弹窗）共用
                String conversationId = resolveConversationId(request.getConversationId(), authContext.userId());

                // 通过 ToolContext 把 emitter 传给工具回调（带外数据通道）
                // Spring AI 不会把 toolContext 发给模型，只在本地工具执行时可见
                reactor.core.publisher.Flux<org.springframework.ai.chat.model.ChatResponse> flux = promptSpec
                        .toolCallbacks(toolCallbacks)
                        .toolContext(java.util.Map.of(
                                        "emitter", emitter,
                                        "conversationId", conversationId,
                                        "authContext", authContext
                                )
                        )
                        // 1.1.8：会话 ID 改从请求上下文传入（键见 ChatMemory.CONVERSATION_ID）
                        .advisors(spec -> spec
                                .advisors(memoryAdvisor())
                                .param(ChatMemory.CONVERSATION_ID, conversationId))
                        .stream()
                        // 同上：chatResponse() 才能同时拿到文本与思考内容
                        .chatResponse();

                flux.subscribe(
                        response -> forwardChatResponseEvents(response, emitter),
                        error -> {
                            log.error("带工具流式聊天(Spring AI)异常", error);
                            emitter.completeWithError(error);
                        },
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                emitter.complete();
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        }
                );

            } catch (Exception e) {
                log.error("带工具流式聊天(Spring AI)启动异常", e);
                emitter.completeWithError(e);
            }
        });
    }

    // ========================================================================
    // 流式 SSE 转发
    // ========================================================================

    /**
     * 把一个流式 ChatResponse 块拆成 SSE 事件发给前端。
     * <p>
     * 输入：模型的某一个流式块（可能只有文本、只有思考、两者都有或都没有）。
     * 输出：0~2 个 SSE 事件——可见文本 → event: chunk；思考内容 → event: reasoning。
     * <p>
     * 厂商隔离点：思考内容只有智谱消息（ZhiPuAiAssistantMessage）才有；
     * 将来换厂商只改这一处（或返回 null 即关闭思考显示），其余代码不感知。
     */
    private void forwardChatResponseEvents(org.springframework.ai.chat.model.ChatResponse response, SseEmitter emitter) {
        var result = response.getResult();
        if (result == null) {
            return;
        }
        var output = result.getOutput();
        String text = output.getText();
        String reasoning = (output instanceof ZhiPuAiAssistantMessage zm) ? zm.getReasoningContent() : null;
        try {
            if (text != null && !text.isEmpty()) {
                emitter.send(SseEmitter.event().name("chunk").data(text));
            }
            if (reasoning != null && !reasoning.isEmpty()) {
                emitter.send(SseEmitter.event().name("reasoning").data(reasoning));
            }
        } catch (Exception e) {
            log.error("发送 SSE 事件失败", e);
        }
    }

    // ========================================================================
    // 对话记忆
    // ========================================================================

    /**
     * 构建对话记忆 Advisor：请求前把该会话的历史拼进 prompt，响应后把新消息存回去。
     * <p>
     * 注意：1.1.8 起 Builder 删除了 conversationId()，会话 ID 改由请求上下文的
     * {@link ChatMemory#CONVERSATION_ID} 键提供（缺失会断言报错），
     * 所以调用处必须与 advisors(spec -> spec.param(...)) 成对使用。
     */
    private MessageChatMemoryAdvisor memoryAdvisor() {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    /**
     * 决定本次请求归属哪个会话
     */
    private String resolveConversationId(String conversationId, String userId) {
        if (conversationId != null && !conversationId.isBlank()) {
            return userId + ":" + conversationId;
        }
        // 强制要求会话 ID：宁可报错，不要暧昧 不报名字就不接待——强制调用方必须传 ID
        throw new IllegalArgumentException("conversationId 不能为空");
    }

    /**
     * 取消
     *
     */
    public void cancelConfirmation(String confirmationId, String userId) {
        PendingConfirmationStore.PendingConfirmation pending =
                pendingConfirmationStore.consume(confirmationId, userId);

        if (pending == null) {
            throw new IllegalArgumentException("确认请求不存在、已使用或无权操作");
        }
    }

    public ChatResponse deleteByConversationId(@NotNull(message = "ID不能为空") String id, String userId) {
        chatMemory.clear(resolveConversationId(id, userId));
        return ChatResponse.ok("会话已清空", 0, 0);
    }
}
