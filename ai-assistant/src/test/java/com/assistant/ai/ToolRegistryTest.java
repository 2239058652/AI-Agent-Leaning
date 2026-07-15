package com.assistant.ai;

import com.assistant.ai.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具注册表测试 — 验证白名单和工具定义
 */
class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry(new ObjectMapper());
        registry.init();
    }

    @Test
    void registeredToolsShouldBeAllowed() {
        assertTrue(registry.isAllowed("get_current_date"));
        assertTrue(registry.isAllowed("calculate"));
        assertTrue(registry.isAllowed("get_ip"));
        assertTrue(registry.isAllowed("get_weather_by_city"));
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
        assertEquals(4, toolsJson.size());

        // 验证第一个工具的结构
        var first = toolsJson.get(0);
        assertEquals("function", first.get("type").asText());
        assertEquals("get_current_date", first.get("function").get("name").asText());
    }

    @Test
    void sensitiveToolShouldBeMarked() {
        // 当前所有工具都不是敏感的
        for (var tool : registry.getAllTools()) {
            assertFalse(tool.isSensitive(), "工具 " + tool.getName() + " 不应该是敏感的");
        }
    }
}
