package com.assistant.ai;

import com.assistant.ai.mcp.McpClientService;
import com.assistant.ai.service.OrderService;
import com.assistant.ai.tool.ToolRegistry;
import com.assistant.ai.tool.ToolResult;
import com.assistant.ai.tool.ToolService;
import com.assistant.ai.tool.ToolValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具执行测试 — 验证 ToolService 的各个工具能正常工作
 * <p>
 * 这是纯单元测试，不启动 Spring 容器。
 * ToolService 的依赖通过 Mock 提供，不连数据库、不连 MCP Server。
 */
class ToolServiceTest {

    private ToolService toolService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Mock McpClientService — 不连 MCP Server
        McpClientService mockMcp = Mockito.mock(McpClientService.class);
        Mockito.when(mockMcp.listTools()).thenReturn(List.of());

        // Mock OrderService — 不连数据库
        OrderService mockOrderService = Mockito.mock(OrderService.class);

        ToolRegistry registry = new ToolRegistry(objectMapper, mockMcp);
        registry.init();
        ToolValidator validator = new ToolValidator();

        toolService = new ToolService(objectMapper, registry, validator, mockMcp, mockOrderService);
    }

    @Test
    void getCurrentDateShouldReturnTodayString() {
        ToolResult result = toolService.execute("get_current_date", "{}");
        assertFalse(result.isNeedsConfirmation());
        assertTrue(result.getResult().contains("2026"), "应该包含年份");
        assertTrue(result.getResult().contains("星期"), "应该包含星期几");
    }

    @Test
    void calculateShouldReturn5ForTwoPlusThree() {
        ToolResult result = toolService.execute("calculate", "{\"expression\":\"2+3\"}");
        assertTrue(result.getResult().contains("5"), "应该包含计算结果5");
    }

    @Test
    void calculateShouldReturnErrorForDivisionByZero() {
        ToolResult result = toolService.execute("calculate", "{\"expression\":\"10/0\"}");
        assertTrue(result.getResult().contains("错误") || result.getResult().contains("除数"), "应该提示除数错误");
    }

    @Test
    void unknownToolShouldReturnError() {
        ToolResult result = toolService.execute("unknown_tool", "{}");
        assertTrue(result.getResult().contains("错误") || result.getResult().contains("未知"), "应该提示未知工具");
    }

    @Test
    void sensitiveToolShouldRequireConfirmation() {
        ToolResult result = toolService.execute("cancel_order", "{\"order_no\":\"ORD001\"}");
        assertTrue(result.isNeedsConfirmation(), "cancel_order 应该返回需要确认");
        assertEquals("cancel_order", result.getToolName());
    }
}
