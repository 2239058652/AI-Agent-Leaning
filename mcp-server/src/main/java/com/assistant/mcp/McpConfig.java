package com.assistant.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.List;

/**
 * MCP Server 配置
 * <p>
 * 把 MCP Server 注册为 Spring Bean，通过 HTTP Servlet 对外提供服务。
 * <p>
 * stdio vs HTTP 的区别：
 * - stdio：Server 是一个子进程，Client 通过 stdin/stdout 通信。只能本地。
 * - HTTP：Server 是一个 Web 服务，Client 通过 HTTP 请求通信。可以远程。
 */
@Configuration
public class McpConfig {

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransportProvider() {
        var jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());

        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint("/mcp")       // MCP 端点路径
                .keepAliveInterval(java.time.Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public io.modelcontextprotocol.server.McpSyncServer mcpServer(
            HttpServletStreamableServerTransportProvider transportProvider) {

        // 参数 schema
        var inputSchema = new io.modelcontextprotocol.spec.McpSchema.JsonSchema(
                "object",
                Map.of(
                        "city", Map.of("type", "string", "description", "城市名称"),
                        "days", Map.of("type", "integer", "description", "未来几天")
                ),
                List.of("city"),
                false, null, null
        );

        // 工具定义
        var weatherTool = new io.modelcontextprotocol.spec.McpSchema.Tool(
                "get_weather",
                "get_weather",
                "查询指定城市未来几天的天气情况",
                inputSchema,
                null, null, null
        );

        return McpServer.sync(transportProvider)
                .serverInfo("weather-mcp-server", "1.0.0")
                .capabilities(io.modelcontextprotocol.spec.McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tool(weatherTool, (exchange, args) -> {
                    // 手动构建 JSON，避免编码问题
                    StringBuilder sb = new StringBuilder("{");
                    boolean first = true;
                    for (var entry : args.entrySet()) {
                        if (!first) sb.append(",");
                        sb.append("\"").append(entry.getKey()).append("\":");
                        if (entry.getValue() instanceof String s) {
                            sb.append("\"").append(s).append("\"");
                        } else {
                            sb.append(entry.getValue());
                        }
                        first = false;
                    }
                    sb.append("}");

                    String result = WeatherTool.execute(sb.toString());
                    return new io.modelcontextprotocol.spec.McpSchema.CallToolResult(
                            List.of(new io.modelcontextprotocol.spec.McpSchema.TextContent(result)),
                            false
                    );
                })
                .build();
    }

    /**
     * 注册 MCP Servlet
     * 把 HttpServletStreamableServerTransportProvider 注册到 /mcp 路径
     */
    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transportProvider) {
        return new ServletRegistrationBean<>(transportProvider, "/mcp");
    }
}
