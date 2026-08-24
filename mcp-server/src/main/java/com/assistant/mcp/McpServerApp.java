package com.assistant.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 启动类
 * <p>
 * 通过 stdio（标准输入输出）与 MCP 客户端通信。
 * 启动方式：java -jar mcp-server.jar
 */
public class McpServerApp {

    public static McpSyncServer createServer() {
        // JSON 序列化器
        var jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());

        // stdio 传输层
        var transportProvider = new StdioServerTransportProvider(jsonMapper);

        // 参数 schema — 告诉模型需要传哪些参数
        var inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "city", Map.of("type", "string", "description", "城市名称"),
                        "days", Map.of("type", "integer", "description", "未来几天")
                ),
                List.of("city"),   // 必填参数
                false, null, null
        );

        // 工具定义
        var weatherTool = new McpSchema.Tool(
                "get_weather",          // name
                "get_weather",          // title
                "查询指定城市未来几天的天气情况", // description
                inputSchema,            // 参数 schema
                null,                   // outputSchema
                null,                   // annotations
                null                    // meta
        );

        // 构建 Server
        return McpServer.sync(transportProvider)
                .serverInfo("weather-mcp-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .toolCall(weatherTool, (exchange, request) -> {
                    // 手动构建 JSON，避免 Jackson 编码问题
                    StringBuilder sb = new StringBuilder("{");
                    boolean first = true;
                    for (var entry : request.arguments().entrySet()) {
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
                    return McpSchema.CallToolResult.builder()
                            .addTextContent(result)
                            .build();
                })
                .build();
    }

    public static void main(String[] args) {
        // 注意：不能向 stdout 输出任何非 JSON 内容
        // stdout 是 MCP 通信通道，Client 会解析这里的每一行
        createServer();
        // createServer() 内部会阻塞，不会执行到这里
    }
}
