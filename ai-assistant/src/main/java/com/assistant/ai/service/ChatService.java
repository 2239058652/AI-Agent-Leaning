package com.assistant.ai.service;

import com.assistant.ai.config.LlmProperties;
import com.assistant.ai.dto.ChatRequest;
import com.assistant.ai.dto.ChatResponse;
import com.assistant.ai.tool.ToolRegistry;
import com.assistant.ai.tool.ToolResult;
import com.assistant.ai.tool.ToolService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final ToolService toolService;
    private final ToolRegistry toolRegistry;
    private final ChatClient.Builder chatClientBuilder;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(120))
            .build();

    // ========================================================================
    // 非流式聊天
    // ========================================================================

    /**
     * 执行已确认的敏感操作
     * <p>
     * 用户在前端确认后，调用此方法执行。
     * 不走 Agent Loop，直接执行工具并返回结果。
     */
    public ChatResponse executeConfirmed(String toolName, String argsJson) {
        try {
            log.info("执行已确认的敏感操作: {}({})", toolName, argsJson);
            ToolResult result = toolService.executeConfirmed(toolName, argsJson);
            return ChatResponse.ok(result.getResult(), 0, 0);
        } catch (Exception e) {
            log.error("执行已确认操作失败", e);
            return ChatResponse.fail("执行失败: " + e.getMessage());
        }
    }

    public ChatResponse chat(ChatRequest request) {
        try {
            log.info("LLM 非流式请求(Spring AI): model={}", llmProperties.getModel());

            // 构建 ChatClient
            ChatClient chatClient = chatClientBuilder.build();

            // 用 ChatClient 的 fluent API 构建请求
            // .system() 设置系统提示词，.user() 设置用户消息
            var promptSpec = chatClient.prompt();
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
                promptSpec = promptSpec.system(request.getSystemPrompt());
            }
            promptSpec = promptSpec.user(request.getMessage());

            // 调用模型，拿到回复
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

    public void chatStream(ChatRequest request, SseEmitter emitter) {
        chatStreamInternal(request, emitter, false);
    }

    // ========================================================================
    // 流式聊天（带工具 Agent Loop）
    // ========================================================================

    public void chatStreamWithTools(ChatRequest request, SseEmitter emitter) {
        chatStreamInternal(request, emitter, true);
    }

    private void chatStreamInternal(ChatRequest request, SseEmitter emitter, boolean useTools) {
        // ★ executor.execute() 的作用：在后台线程执行，主线程立即返回
        // 为什么？因为 LLM 响应可能要几十秒，如果阻塞主线程，Tomcat 就没法处理其他请求
        executor.execute(() -> {
            try {
                // ================================================================
                // 第1步：构建对话历史
                // 对话历史就是一个 List，里面放着所有消息（用户说了什么、模型说了什么、工具结果）
                // 模型需要看到完整的对话历史才能做决策
                // ================================================================
                List<ObjectNode> messages = buildMessageList(request);
                log.info("========== Agent Loop 开始 ==========");
                log.info("用户消息: {}", request.getMessage());
                log.info("对话历史包含 {} 条消息", messages.size());

                // ================================================================
                // 第2步：Agent Loop — 循环调用模型，直到模型给最终回复
                // 为什么是循环？因为模型可能要先调工具，拿到结果后再回复
                // 每次循环就是一次"发请求 → 拿响应 → 判断"的过程
                // ================================================================
                boolean waitingForConfirmation = false;
                for (int round = 0; round < 10; round++) {
                    log.info("");
                    log.info("========== 第 {} 轮 ==========", round + 1);

                    // ================================================================
                    // 第3步：构建 JSON 请求体
                    // 把对话历史 + 工具定义 转成 JSON，准备发给模型
                    // ================================================================
                    String requestBody = buildRequestBodyFromMessages(messages, true, useTools);
                    log.info("[请求] 发送 {} 条消息给模型，useTools={}", messages.size(), useTools);

                    // ================================================================
                    // 第4步：构建 HTTP 请求
                    // 就是用 Java 代码发一个 POST 请求，和你用 Postman 发请求一样
                    // ================================================================
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                            .uri(URI.create(llmProperties.getBaseUrl() + "/openai/v1/chat/completions"))
                            .header("Authorization", "Bearer " + llmProperties.getApiKey())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .build();

                    // ================================================================
                    // 第5步：发送请求，拿到响应
                    // BodyHandlers.ofInputStream() 表示用"流"的方式接收响应
                    // 不是等全部返回再给你，而是给你一个管道，你逐行读
                    // ================================================================
                    log.info("[请求] 正在发送 HTTP 请求...");
                    HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
                    log.info("[响应] HTTP 状态码: {}", response.statusCode());

                    if (response.statusCode() != 200) {
                        String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                        log.error("[错误] LLM 请求失败: {} - {}", response.statusCode(), errorBody);
                        emitter.send(SseEmitter.event().name("error").data("HTTP " + response.statusCode()));
                        emitter.complete();
                        return;
                    }

                    // ================================================================
                    // 第6步：读取流式响应
                    // 模型一边生成一边推数据过来，我们逐行读取
                    // 同时做两件事：
                    //   a. 把文本内容推给前端（用户能看到打字机效果）
                    //   b. 收集工具调用信息（如果有）
                    // ================================================================
                    StringBuilder fullContent = new StringBuilder();
                    List<ToolCallInfo> toolCalls = new ArrayList<>();
                    String finishReason = readStreamResponse(response.body(), emitter, fullContent, toolCalls);
                    log.info("[响应] finish_reason={}", finishReason);
                    log.info("[响应] 模型回复内容: {}", fullContent);
                    if (!toolCalls.isEmpty()) {
                        log.info("[响应] 模型要求调用 {} 个工具:", toolCalls.size());
                        for (ToolCallInfo tc : toolCalls) {
                            log.info("  → {}({})", tc.name, tc.arguments);
                        }
                    }

                    // ================================================================
                    // 第7步：把模型的回复加入对话历史
                    // 不管模型是给最终回复还是调工具，都要记录到对话历史里
                    // 这样下一轮模型才能看到上一轮发生了什么
                    // ================================================================
                    ObjectNode assistantMsg = objectMapper.createObjectNode();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", fullContent.toString());
                    if (!toolCalls.isEmpty()) {
                        ArrayNode toolCallsNode = assistantMsg.putArray("tool_calls");
                        for (ToolCallInfo tc : toolCalls) {
                            ObjectNode tcNode = toolCallsNode.addObject();
                            tcNode.put("id", tc.id);
                            tcNode.putObject("function")
                                    .put("name", tc.name)
                                    .put("arguments", tc.arguments);
                        }
                    }
                    messages.add(assistantMsg);
                    log.info("[历史] 对话历史现在有 {} 条消息", messages.size());

                    // ================================================================
                    // 第8步：判断模型要做什么
                    // finish_reason 有两种关键值：
                    //   "tool_calls" → 模型要调工具，不是给最终回复
                    //   "stop"       → 模型给了最终回复，结束循环
                    // ================================================================
                    if ("tool_calls".equals(finishReason) && !toolCalls.isEmpty()) {
                        // ---- 模型要调工具 ----
                        log.info("[决策] 模型要求调工具，开始执行...");

                        boolean needsBreak = false;
                        for (ToolCallInfo tc : toolCalls) {
                            // ★ 执行工具 — 这是本地 Java 代码，不是模型执行的
                            log.info("[执行] 调用工具: {}，参数: {}", tc.name, tc.arguments);

                            // 通知前端：工具调用开始
                            try {
                                ObjectNode toolCallEvent = objectMapper.createObjectNode();
                                toolCallEvent.put("name", tc.name);
                                toolCallEvent.put("arguments", tc.arguments);
                                emitter.send(SseEmitter.event().name("tool_call")
                                        .data(objectMapper.writeValueAsString(toolCallEvent)));
                            } catch (Exception ignored) {
                            }

                            ToolResult toolResult = toolService.execute(tc.name, tc.arguments);

                            // 敏感操作：通知前端需要确认，暂停 Agent Loop
                            if (toolResult.isNeedsConfirmation()) {
                                log.info("[确认] 工具 {} 需要用户确认，暂停 Agent Loop", tc.name);
                                ObjectNode confirmEvent = objectMapper.createObjectNode();
                                confirmEvent.put("toolName", toolResult.getToolName());
                                confirmEvent.put("argsJson", toolResult.getArgsJson());
                                confirmEvent.put("message", "操作需要确认：取消订单 " + toolResult.getArgsJson());
                                emitter.send(SseEmitter.event().name("confirmation_required")
                                        .data(objectMapper.writeValueAsString(confirmEvent)));
                                waitingForConfirmation = true;
                                needsBreak = true;
                                break;
                            }

                            String result = toolResult.getResult();
                            log.info("[执行] 工具返回: {}", result);

                            // 通知前端：工具执行结果
                            try {
                                ObjectNode toolResultEvent = objectMapper.createObjectNode();
                                toolResultEvent.put("name", tc.name);
                                toolResultEvent.put("result", result);
                                emitter.send(SseEmitter.event().name("tool_result")
                                        .data(objectMapper.writeValueAsString(toolResultEvent)));
                            } catch (Exception ignored) {
                            }

                            // ★ 把工具结果加入对话历史
                            // role 必须是 "tool"，tool_call_id 要和模型返回的一致
                            ObjectNode toolMsg = objectMapper.createObjectNode();
                            toolMsg.put("role", "tool");
                            toolMsg.put("tool_call_id", tc.id);
                            toolMsg.put("content", result);
                            messages.add(toolMsg);
                        }
                        if (needsBreak) break;

                        log.info("[历史] 对话历史现在有 {} 条消息（含工具结果）", messages.size());
                        log.info("[决策] 继续下一轮，把工具结果告诉模型...");
                        // ★ continue = 回到 for 循环顶部，执行第2轮
                        // 模型看到工具结果后，会决定是继续调工具还是给最终回复
                        continue;
                    }

                    // ---- 模型给了最终回复 ----
                    log.info("========== Agent Loop 结束 ==========");
                    log.info("模型最终回复: {}", fullContent);
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    emitter.complete();
                    // ★ return = 退出方法，不再循环
                    return;
                }

                // 循环10次还没结束，强制停止
                if (!waitingForConfirmation) {
                    log.warn("Agent Loop 超过最大轮次(10)，强制停止");
                    emitter.send(SseEmitter.event().name("error").data("Agent Loop 超过最大轮次"));
                    emitter.complete();
                } else {
                    log.info("Agent Loop 暂停，等待用户确认敏感操作");
                }

            } catch (Exception e) {
                log.error("Agent Loop 异常", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });
    }

    // ========================================================================
    // 请求构建
    // ========================================================================

    /**
     * 从 ChatRequest 构建消息列表
     */
    private List<ObjectNode> buildMessageList(ChatRequest request) {
        List<ObjectNode> messages = new ArrayList<>();

        // 系统提示词
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", request.getSystemPrompt());
            messages.add(systemMsg);
        }

        // 对话历史
        if (request.getHistory() != null) {
            for (ChatRequest.Message hist : request.getHistory()) {
                ObjectNode msg = objectMapper.createObjectNode();
                msg.put("role", hist.getRole());
                msg.put("content", hist.getContent());
                messages.add(msg);
            }
        }

        // 当前用户消息
        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", request.getMessage());
        messages.add(userMsg);

        return messages;
    }

    /**
     * 构建 JSON 请求体（非流式用）
     */
    private String buildRequestBody(ChatRequest request, boolean stream, boolean useTools) throws Exception {
        List<ObjectNode> messages = buildMessageList(request);
        return buildRequestBodyFromMessages(messages, stream, useTools);
    }

    /**
     * 从消息列表构建 JSON 请求体
     */
    private String buildRequestBodyFromMessages(List<ObjectNode> messages, boolean stream, boolean useTools) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", llmProperties.getModel());
        body.put("stream", stream);

        ArrayNode messagesArray = body.putArray("messages");
        for (ObjectNode msg : messages) {
            messagesArray.add(msg);
        }

        // 加入工具定义
        if (useTools) {
            body.set("tools", buildToolsArray());
        }

        return objectMapper.writeValueAsString(body);
    }

    /**
     * 构建工具定义数组 — 告诉模型有哪些工具可用
     * 现在从 ToolRegistry 统一获取，不再硬编码 JSON
     */
    private ArrayNode buildToolsArray() {
        return toolRegistry.buildToolsJson();
    }

    // ========================================================================
    // 响应解析
    // ========================================================================

    /**
     * 读取流式响应，同时推给前端并收集工具调用信息
     *
     * @return finish_reason
     */
    private String readStreamResponse(InputStream responseBody, SseEmitter emitter,
                                      StringBuilder fullContent, List<ToolCallInfo> toolCalls) throws Exception {
        String finishReason = "stop";

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseBody, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                if ("data: [DONE]".equals(line)) break;

                if (line.startsWith("data: ")) {
                    String json = line.substring(6);

                    JsonNode node = objectMapper.readTree(json);
                    JsonNode choice = node.path("choices").path(0);

                    // 提取文本内容
                    String content = choice.path("delta").path("content").asText("");
                    if (!content.isEmpty()) {
                        fullContent.append(content);
                        emitter.send(SseEmitter.event().name("chunk").data(content));
                    }

                    // 提取工具调用
                    JsonNode toolCallsNode = choice.path("delta").path("tool_calls");
                    if (toolCallsNode.isArray()) {
                        for (JsonNode tc : toolCallsNode) {
                            int index = tc.path("index").asInt(0);

                            // 确保 toolCalls 列表足够长
                            while (toolCalls.size() <= index) {
                                toolCalls.add(new ToolCallInfo());
                            }

                            ToolCallInfo info = toolCalls.get(index);
                            String id = tc.path("id").asText("");
                            if (!id.isEmpty()) info.id = id;

                            String name = tc.path("function").path("name").asText("");
                            if (!name.isEmpty()) info.name = name;

                            String args = tc.path("function").path("arguments").asText("");
                            if (!args.isEmpty()) info.arguments += args;
                        }
                    }

                    // 提取 finish_reason
                    String fr = choice.path("finish_reason").asText("");
                    if (!fr.isEmpty() && !"null".equals(fr)) {
                        finishReason = fr;
                    }
                }
            }
        }

        return finishReason;
    }

    private ChatResponse parseResponse(String responseBody) throws Exception {
        JsonNode json = objectMapper.readTree(responseBody);

        String content = json
                .path("choices").path(0)
                .path("message").path("content")
                .asText("");

        JsonNode usage = json.path("usage");
        int promptTokens = usage.path("prompt_tokens").asInt(0);
        int completionTokens = usage.path("completion_tokens").asInt(0);

        log.info("LLM 响应: content长度={}, tokens={}/{}", content.length(), promptTokens, completionTokens);

        return ChatResponse.ok(content, promptTokens, completionTokens);
    }

    // ========================================================================
    // 工具调用信息
    // ========================================================================

    /**
     * 工具调用信息 — 从流式响应中收集
     */
    private static class ToolCallInfo {
        String id = "";
        String name = "";
        String arguments = "";
    }
}
