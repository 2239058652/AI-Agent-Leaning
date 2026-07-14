package com.assistant.ai;

import com.assistant.ai.tool.ToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具执行测试 — 验证 ToolService 的各个工具能正常工作
 * <p>
 * 这是纯单元测试，不启动 Spring 容器。
 * ToolService 依赖 ObjectMapper，手动 new 一个就行。
 */
class ToolServiceTest {

    private ToolService toolService;

    @BeforeEach
    void setUp() {
        toolService = new ToolService(new ObjectMapper());
    }

    @Test
    void getCurrentDateShouldReturnTodayString() {
        String result = toolService.execute("get_current_date", "{}");
        assertTrue(result.contains("2026"), "应该包含年份");
        assertTrue(result.contains("星期"), "应该包含星期几");
    }

    @Test
    void calculateShouldReturn5ForTwoPlusThree() {
        String result = toolService.execute("calculate", "{\"expression\":\"2+3\"}");
        assertTrue(result.contains("5"), "应该包含计算结果5");
    }

    @Test
    void calculateShouldReturnErrorForDivisionByZero() {
        String result = toolService.execute("calculate", "{\"expression\":\"10/0\"}");
        assertTrue(result.contains("错误") || result.contains("除数"), "应该提示除数错误");
    }

    @Test
    void unknownToolShouldReturnError() {
        String result = toolService.execute("unknown_tool", "{}");
        assertTrue(result.contains("错误") || result.contains("未知"), "应该提示未知工具");
    }
}
