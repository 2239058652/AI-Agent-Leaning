package com.assistant.ai.controller;

import com.assistant.ai.dto.ChatRequest;
import com.assistant.ai.dto.ChatResponse;
import com.assistant.ai.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 非流式聊天 — 一次性返回完整回复
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return chatService.chat(request);
    }

    /**
     * 流式聊天 — 不带工具，纯文本对话
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        chatService.chatStream(request, emitter);
        return emitter;
    }

    /**
     * 流式聊天 — 带工具 Agent Loop
     *
     * 模型可以调用本地工具（天气、日期、计算器），执行后把结果告诉模型，模型再生成回复。
     */
    @PostMapping(value = "/chat/tool-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatToolStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L); // 工具调用可能需要更久
        chatService.chatStreamWithTools(request, emitter);
        return emitter;
    }
}
