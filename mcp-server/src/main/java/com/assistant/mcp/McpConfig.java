package com.assistant.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Map;

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
                .contextExtractor(request -> {
                    var principal = request.getUserPrincipal();
                    if (principal instanceof Authentication auth) {

                        return McpTransportContext.create(Map.of(
                                "userId", auth.getName(),
                                "roles", auth.getAuthorities().stream()
                                        .map(GrantedAuthority::getAuthority).toList()
                        ));
                    }

                    return McpTransportContext.EMPTY;
                })
                .build();
    }

    @Bean
    public McpSyncServer mcpServer(HttpServletStreamableServerTransportProvider transportProvider) {
        var objectMapper = new ObjectMapper();

        return McpServer.sync(transportProvider)
                .serverInfo("weather-mcp-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .toolCall(weatherTool(), (exchange, request) -> {

                    List<String> roles = exchange.transportContext().get("roles") instanceof List<?> list
                            ? list.stream().map(String::valueOf).toList()
                            : List.of();

                    if (!roles.contains("ROLE_ADMIN")) {
                        return CallToolResult.builder()
                                .isError(true)
                                .addTextContent("权限不足：get_weather 需要 ADMIN 角色")
                                .build();
                    }

                    String argsJson = toJson(objectMapper, request.arguments());
                    String result = WeatherTool.execute(argsJson);
                    return CallToolResult.builder()
                            .addTextContent(result)
                            .build();
                })
                .build();
    }

    /**
     * 天气工具定义
     */
    private McpSchema.Tool weatherTool() {
        var inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "city", Map.of("type", "string", "description", "城市名称"),
                        "days", Map.of("type", "integer", "description", "未来几天")
                ),
                List.of("city"),
                false, null, null
        );

        return new McpSchema.Tool(
                "get_weather",
                "get_weather",
                "查询指定城市未来几天的天气情况",
                inputSchema,
                null, null, null
        );
    }

    private String toJson(ObjectMapper objectMapper, Map<String, Object> args) {
        try {
            return objectMapper.writeValueAsString(args);
        } catch (JsonProcessingException e) {
            return "{}";
        }
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
