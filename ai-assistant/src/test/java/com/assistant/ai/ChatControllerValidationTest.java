package com.assistant.ai;

import com.assistant.ai.controller.ChatController;
import com.assistant.ai.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 参数校验测试 — 验证 @NotBlank @Size 注解是否生效
 * <p>
 * 为什么用 @WebMvcTest 而不是 @SpringBootTest？
 * @WebMvcTest 只启动 Web 层，不加载 Service/Repository，启动快、测试隔离性好。
 * 这里只需要验证 Controller 层的参数校验，不需要真正的 LLM 调用。
 */
@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReturn400WhenMessageIsEmpty() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn400WhenMessageIsNull() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturn400WhenMessageExceeds10000Chars() throws Exception {
        String longMessage = "a".repeat(10001);
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.assistant.ai.dto.ChatRequest() {{
                                    setMessage(longMessage);
                                }})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
