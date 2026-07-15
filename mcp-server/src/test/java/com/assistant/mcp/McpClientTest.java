package com.assistant.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 客户端测试 — 通过 HTTP 连接 MCP Server
 * <p>
 * @SpringBootTest 启动完整的 Spring Boot 应用（包括嵌入式 Tomcat），
 * 测试类通过 HTTP 连接 Server，和真实场景一样。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "server.port=0")  // 随机端口，避免冲突
class McpClientTest {

    private static final String MCP_ENDPOINT = "http://localhost:%d/mcp";

    /**
     * 创建并连接 MCP 客户端（HTTP 传输）
     */
    private McpSyncClient createAndConnectClient(int port) throws Exception {
        String url = String.format(MCP_ENDPOINT, port);

        var jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        var transport = HttpClientStreamableHttpTransport.builder(url)
                .jsonMapper(jsonMapper)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(15))
                .build();

        client.initialize();
        return client;
    }

    /**
     * 测试1：连接 Server，验证能获取工具列表
     */
    @Test
    void shouldDiscoverTools(@org.springframework.beans.factory.annotation.Autowired org.springframework.web.context.WebApplicationContext ctx) throws Exception {
        int port = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) ctx)
                .getWebServer().getPort();

        var client = createAndConnectClient(port);

        var tools = client.listTools();
        assertNotNull(tools);
        assertFalse(tools.tools().isEmpty(), "应该至少有1个工具");

        boolean hasWeatherTool = tools.tools().stream()
                .anyMatch(t -> "get_weather".equals(t.name()));
        assertTrue(hasWeatherTool, "应该包含 get_weather 工具");

        System.out.println("✅ 发现工具: " + tools.tools().stream()
                .map(McpSchema.Tool::name).toList());

        client.closeGracefully();
    }

    /**
     * 测试2：调用 get_weather 工具
     */
    @Test
    void shouldCallWeatherTool(@org.springframework.beans.factory.annotation.Autowired org.springframework.web.context.WebApplicationContext ctx) throws Exception {
        int port = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) ctx)
                .getWebServer().getPort();

        var client = createAndConnectClient(port);

        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "get_weather", Map.of("city", "北京", "days", 1));
        var result = client.callTool(request);

        assertNotNull(result);
        assertFalse(result.content().isEmpty(), "应该有返回内容");

        String weatherResult = ((McpSchema.TextContent) result.content().get(0)).text();
        assertNotNull(weatherResult);
        assertFalse(weatherResult.isBlank());

        System.out.println("✅ 天气查询结果: " + weatherResult);

        client.closeGracefully();
    }
}
