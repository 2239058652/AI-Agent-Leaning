package com.assistant.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

        register(ToolDefinition.builder()
                .name("get_weather_by_city")
                .description("查询指定城市未来几天的天气情况，支持输入天数查具体的未来几天的天气")
                .parameters(parseSchema("""
                        {
                          "type": "object",
                          "properties": {
                            "city": {
                              "type": "string",
                              "description": "城市名称，如'北京'、'上海'"
                            },
                            "days": {
                              "type": "integer",
                              "description": "未来几天，如1、2、3"
                            }
                          },
                          "required": ["city"]
                        }
                        """))
                .build());

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

    private JsonNode parseSchema(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("工具参数 Schema 解析失败", e);
        }
    }
}
