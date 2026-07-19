package com.assistant.ai.tool;

import com.assistant.ai.mcp.McpClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工具注册表 — 集中管理所有合法工具
 * <p>
 * 解决的问题：
 * 1. 工具定义散落在 ChatService（JSON字符串）和 ToolService（switch）里，改一个忘另一个
 * 2. 未知工具没有统一的拒绝机制
 * 3. 敏感操作没有标记，无法区分只读和写操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    private final ObjectMapper objectMapper;
    private final McpClientService mcpClientService;
    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        // ---- 注册所有合法工具 ----
        register(ToolDefinition.builder()
                .name("get_current_date")
                .description("获取当前日期和星期几")
                .parameters(parseSchema("""
                        {
                          "type": "object",
                          "properties": {},
                          "required": []
                        }
                        """))
                .build());

        register(ToolDefinition.builder()
                .name("calculate")
                .description("计算数学表达式，支持加减乘除")
                .parameters(parseSchema("""
                        {
                          "type": "object",
                          "properties": {
                            "expression": {
                              "type": "string",
                              "description": "数学表达式，如 '2+3*4'"
                            }
                          },
                          "required": ["expression"]
                        }
                        """))
                .build());

        register(ToolDefinition.builder()
                .name("get_ip")
                .description("获取本机IP地址")
                .parameters(parseSchema("""
                        {
                          "type": "object",
                          "properties": {},
                          "required": []
                        }
                        """))
                .build());

        // ---- 业务工具：订单管理 ----

        register(ToolDefinition.builder()
                .name("query_orders")
                .description("查询订单列表。可按状态筛选：PENDING(待支付)、PAID(已支付)、CANCELLED(已取消)。不传状态查全部。")
                .parameters(parseSchema("""
                        {
                          "type": "object",
                          "properties": {
                            "status": {
                              "type": "string",
                              "description": "订单状态筛选，可选值：PENDING、PAID、CANCELLED，不传则查全部"
                            }
                          },
                          "required": []
                        }
                        """))
                .build());

        register(ToolDefinition.builder()
                .name("analyze_orders")
                .description("统计今日订单数据，包括订单数、成交额、已支付订单数")
                .parameters(parseSchema("""
                        {
                          "type": "object",
                          "properties": {},
                          "required": []
                        }
                        """))
                .build());

        register(ToolDefinition.builder()
                .name("cancel_order")
                .description("取消订单。只有PENDING(待支付)状态的订单可以取消。取消前请确认用户意图。")
                .parameters(parseSchema("""
                        {
                          "type": "object",
                          "properties": {
                            "order_no": {
                              "type": "string",
                              "description": "订单号，如 ORD20260717002"
                            }
                          },
                          "required": ["order_no"]
                        }
                        """))
                .sensitive(true)
                .build());

        // 注册 MCP 工具（从远程 MCP Server 发现）
        registerMcpTools();

        log.info("工具注册表初始化完成，注册了 {} 个工具: {}", tools.size(), tools.keySet());
    }

    private void register(ToolDefinition definition) {
        tools.put(definition.getName(), definition);
    }

    /**
     * 获取工具定义
     */
    public Optional<ToolDefinition> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * 检查工具是否在白名单中
     */
    public boolean isAllowed(String name) {
        return tools.containsKey(name);
    }

    /**
     * 获取所有工具定义 — 用于生成模型的 tools JSON
     */
    public List<ToolDefinition> getAllTools() {
        return List.copyOf(tools.values());
    }

    /**
     * 生成发给模型的 tools JSON 数组
     * <p>
     * 格式：
     * [
     * {
     * "type": "function",
     * "function": {
     * "name": "xxx",
     * "description": "xxx",
     * "parameters": { ... }
     * }
     * }
     * ]
     */
    public ArrayNode buildToolsJson() {
        ArrayNode toolsArray = objectMapper.createArrayNode();

        for (ToolDefinition tool : tools.values()) {
            ObjectNode toolObj = objectMapper.createObjectNode();
            toolObj.put("type", "function");

            ObjectNode functionObj = objectMapper.createObjectNode();
            functionObj.put("name", tool.getName());
            functionObj.put("description", tool.getDescription());
            functionObj.set("parameters", tool.getParameters());

            toolObj.set("function", functionObj);
            toolsArray.add(toolObj);
        }

        return toolsArray;
    }

    /**
     * 从 MCP Server 注册远程工具
     *
     * 将 MCP 工具转为本地 ToolDefinition，这样模型看到的工具列表里
     * 既有本地工具也有远程工具，执行时由 ToolService 根据 source 字段路由。
     */
    private void registerMcpTools() {
        if (!mcpClientService.isAvailable()) {
            log.info("MCP 客户端不可用，跳过 MCP 工具注册");
            return;
        }

        List<McpSchema.Tool> mcpTools = mcpClientService.listTools();
        for (McpSchema.Tool mcpTool : mcpTools) {
            // 将 MCP 的 inputSchema 转为 Jackson JsonNode
            JsonNode parameters = objectMapper.valueToTree(mcpTool.inputSchema());

            ToolDefinition definition = ToolDefinition.builder()
                    .name(mcpTool.name())
                    .description(mcpTool.description() != null ? mcpTool.description() : "")
                    .parameters(parameters)
                    .source(ToolDefinition.ToolSource.MCP)
                    .mcpServer("mcp-server")
                    .build();

            register(definition);
            log.info("注册 MCP 工具: {}", mcpTool.name());
        }
    }

    private JsonNode parseSchema(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("工具参数 Schema 解析失败", e);
        }
    }
}
