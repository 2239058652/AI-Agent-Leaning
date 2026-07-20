package com.assistant.ai;

import com.assistant.ai.mcp.McpClientService;
import com.assistant.ai.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具注册表测试 — 验证白名单和工具定义
 */
class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        McpClientService mockMcp = Mockito.mock(McpClientService.class);
        Mockito.when(mockMcp.listTools()).thenReturn(List.of());
        registry = new ToolRegistry(new ObjectMapper(), mockMcp);
        registry.init();
    }

    @Test
    void registeredToolsShouldBeAllowed() {
        assertTrue(registry.isAllowed("get_current_date"));
        assertTrue(registry.isAllowed("calculate"));
        assertTrue(registry.isAllowed("get_ip"));
        assertTrue(registry.isAllowed("query_orders"));
    }

    @Test
    void unknownToolShouldNotBeAllowed() {
        assertFalse(registry.isAllowed("delete_database"));
        assertFalse(registry.isAllowed(""));
        assertFalse(registry.isAllowed("drop_table"));
    }

    @Test
    void getToolShouldReturnDefinitionForRegisteredTool() {
        var tool = registry.getTool("calculate");
        assertTrue(tool.isPresent());
        assertEquals("calculate", tool.get().getName());
        assertEquals("计算数学表达式，支持加减乘除", tool.get().getDescription());
    }

    @Test
    void getToolShouldReturnEmptyForUnknownTool() {
        assertTrue(registry.getTool("unknown").isEmpty());
    }

    @Test
    void allToolsShouldBeIncludedInJson() {
        var toolsJson = registry.buildToolsJson();
        assertEquals(6, toolsJson.size());

        // 验证第一个工具的结构
        var first = toolsJson.get(0);
        assertEquals("function", first.get("type").asText());
        assertEquals("get_current_date", first.get("function").get("name").asText());
    }

    @Test
    void sensitiveToolShouldBeMarked() {
        // cancel_order 是敏感写操作
        var cancelTool = registry.getTool("cancel_order");
        assertTrue(cancelTool.isPresent());
        assertTrue(cancelTool.get().isSensitive(), "cancel_order 应该是敏感操作");

        // 其他工具不应该是敏感的
        var calculateTool = registry.getTool("calculate");
        assertTrue(calculateTool.isPresent());
        assertFalse(calculateTool.get().isSensitive(), "calculate 不应该是敏感操作");
    }
}
