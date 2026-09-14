package com.assistant.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeatherRoleAuthTest {

    @Test
    void userWithoutAdminGetsErrorResult() {
        CallToolResult result = McpConfig.denyWeatherIfNotAdmin(
                List.of("SCOPE_mcp.weather", "ROLE_USER"));

        assertNotNull(result);
        assertEquals(Boolean.TRUE, result.isError());
    }

    @Test
    void adminIsNotRejectedByRoleCheck() {
        CallToolResult result = McpConfig.denyWeatherIfNotAdmin(
                List.of("SCOPE_mcp.weather", "ROLE_ADMIN"));

        assertNull(result);
    }
}