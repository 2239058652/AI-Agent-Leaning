package com.assistant.ai.controller;

import com.assistant.ai.dto.ChatRequest;
import com.assistant.ai.dto.ChatResponse;
import com.assistant.ai.security.AgentAuthContext;
import com.assistant.ai.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 非流式聊天 — 一次性返回完整回复
     */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, Authentication authentication) {
        return chatService.chat(request, authentication.getName());
    }

    /**
     * 流式聊天 — 不带工具，纯文本对话
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request, Authentication authentication) {
        SseEmitter emitter = new SseEmitter(120_000L);
        chatService.chatStream(request, emitter, authentication.getName());
        return emitter;
    }

    /**
     * 流式聊天 — 带工具 Agent Loop
     * 模型可以调用本地工具（天气、日期、计算器），执行后把结果告诉模型，模型再生成回复。
     */
    @PostMapping(value = "/chat/tool-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatToolStream(@RequestBody ChatRequest request, Authentication authentication) {
        SseEmitter emitter = new SseEmitter(300_000L); // 工具调用可能需要更久
        AgentAuthContext authContext = new AgentAuthContext(
                authentication.getName(),
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toUnmodifiableSet())
        );
        chatService.chatStreamWithTools(request, emitter, authContext);
        return emitter;
    }

    /**
     * 执行已确认的敏感操作
     * <p>
     * 当 Agent Loop 遇到敏感操作时会暂停，前端弹窗让用户确认，
     * 用户确认后调用这个接口执行。
     */
    @PostMapping("/chat/execute-confirmed")
    public ChatResponse executeConfirmed(@Valid @RequestBody ConfirmRequest request, Authentication authentication) {
        return chatService.executeConfirmed(request.confirmationId(), authentication.getName());
    }

    /**
     * 删除对话
     *
     *
     */
    @DeleteMapping("/conversations/{id}")
    public ChatResponse deleteById(@PathVariable("id") String id, Authentication authentication) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("conversationId 不能为空");
        return chatService.deleteByConversationId(id, authentication.getName());
    }

    /**
     * 取消
     */
    @PostMapping("/chat/cancel-confirmation")
    public void cancelConfirmation(@Valid @RequestBody ConfirmRequest request, Authentication authentication) {
        chatService.cancelConfirmation(request.confirmationId(), authentication.getName());
    }

    public record ConfirmRequest(
            @NotBlank(message = "confirmationId 不能为空")
            String confirmationId
    ) {
    }
}
