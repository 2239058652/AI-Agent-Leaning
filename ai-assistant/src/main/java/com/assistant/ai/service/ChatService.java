package com.assistant.ai.service;

import com.assistant.ai.config.LlmProperties;
import com.assistant.ai.dto.ChatRequest;
import com.assistant.ai.dto.ChatResponse;
import com.assistant.ai.tool.ToolService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(120))
            .build();

    // ========================================================================
    // 非流式聊天
    // ========================================================================

    public ChatResponse chat(ChatRequest request) {
        try {
            String requestBody = buildRequestBody(request, false, true);
            log.info("LLM 非流式请求: model={}", llmProperties.getModel());

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(llmProperties.getBaseUrl() + "/openai/v1/chat/completions"))
                    .header("Authorization", "Bearer " + llmProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("LLM 请求失败: HTTP {} - {}", response.statusCode(), response.body());
                return ChatResponse.fail("LLM API 错误: HTTP " + response.statusCode());
            }

            return parseResponse(response.body());

        } catch (Exception e) {
            log.error("LLM 请求异常", e);
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
        executor.execute(() -> {
            try {
                // 构建对话历史
                List<ObjectNode> messages = buildMessageList(request);

                // Agent Loop — 最多循环 10 次
                for (int round = 0; round < 10; round++) {
                    log.info("Agent Loop 第 {} 轮", round + 1);

                    // 构建请求体
                    String requestBody = buildRequestBodyFromMessages(messages, true, useTools);

                    HttpRequest httpRequest = HttpRequest.newBuilder()
                            .uri(URI.create(llmProperties.getBaseUrl() + "/openai/v1/chat/completions"))
                            .header("Authorization", "Bearer " + llmProperties.getApiKey())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .build();

                    HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

                    if (response.statusCode() != 200) {
                        String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                        log.error("LLM 请求失败: HTTP {} - {}", response.statusCode(), errorBody);
                        emitter.send(SseEmitter.event().name("error").data("HTTP " + response.statusCode()));
                        emitter.complete();
                        return;
                    }

                    // 读取流式响应，同时收集完整内容
                    StringBuilder fullContent = new StringBuilder();
                    List<ToolCallInfo> toolCalls = new ArrayList<>();
                    String finishReason = readStreamResponse(response.body(), emitter, fullContent, toolCalls);

                    // 把 assistant 的回复加入对话历史
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

                    // 判断是否需要调用工具
                    if ("tool_calls".equals(finishReason) && !toolCalls.isEmpty()) {
                        // 执行每个工具，把结果加入对话历史
                        for (ToolCallInfo tc : toolCalls) {
                            String result = toolService.execute(tc.name, tc.arguments);
                            log.info("工具 {} 结果: {}", tc.name, result);

                            ObjectNode toolMsg = objectMapper.createObjectNode();
                            toolMsg.put("role", "tool");
                            toolMsg.put("tool_call_id", tc.id);
                            toolMsg.put("content", result);
                            messages.add(toolMsg);
                        }
                        // 继续下一轮，让模型根据工具结果生成回复
                        continue;
                    }

                    // finish_reason 是 stop，模型给了最终回复，结束
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    emitter.complete();
                    log.info("Agent Loop 完成，共 {} 轮", round + 1);
                    return;
                }

                // 循环次数用完
                emitter.send(SseEmitter.event().name("error").data("Agent Loop 超过最大轮次"));
                emitter.complete();

            } catch (Exception e) {
                log.error("Agent Loop 异常", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (Exception ignored) {}
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
     */
    private ArrayNode buildToolsArray() throws Exception {
        String toolsJson = """
                [
                  {
                    "type": "function",
                    "function": {
                      "name": "get_weather",
                      "description": "查询指定城市的当前天气情况",
                      "parameters": {
                        "type": "object",
                        "properties": {
                          "location": {
                            "type": "string",
                            "description": "城市名称，如'北京'、'上海'"
                          }
                        },
                        "required": ["location"]
                      }
                    }
                  },
                  {
                    "type": "function",
                    "function": {
                      "name": "get_current_date",
                      "description": "获取当前日期和星期几",
                      "parameters": {
                        "type": "object",
                        "properties": {},
                        "required": []
                      }
                    }
                  },
                  {
                    "type": "function",
                    "function": {
                      "name": "calculate",
                      "description": "计算数学表达式，支持加减乘除",
                      "parameters": {
                        "type": "object",
                        "properties": {
                          "expression": {
                            "type": "string",
                            "description": "数学表达式，如 '2+3*4'"
                          }
                        },
                        "required": ["expression"]
                      }
                    }
                  }
                ]
                """;
        return (ArrayNode) objectMapper.readTree(toolsJson);
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
