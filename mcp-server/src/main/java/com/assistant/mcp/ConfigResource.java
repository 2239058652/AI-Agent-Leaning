package com.assistant.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 配置资源 — 读取本地 config.json，暴露为 MCP Resource
 * <p>
 * Resource 和 Tool 的区别：
 * - Tool = 模型可以"调用"的动作（有入参、有执行、有返回）
 * - Resource = 模型可以"读取"的上下文（只读，没有执行逻辑）
 * <p>
 * 比如：模型调用 Tool 查天气（执行一个动作），
 *      模型读取 Resource 看配置文件（获取上下文信息）。
 */
public class ConfigResource {

    private static final String RESOURCE_URI = "config://app-config";
    private static final String RESOURCE_NAME = "app-config";

    /**
     * 读取配置文件内容
     *
     * @return JSON 字符串
     */
    public static String read() {
        try (InputStream is = ConfigResource.class.getClassLoader().getResourceAsStream("config.json")) {
            if (is == null) {
                return "{\"error\": \"config.json not found\"}";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public static String getUri() {
        return RESOURCE_URI;
    }

    public static String getName() {
        return RESOURCE_NAME;
    }

    public static String getDescription() {
        return "应用配置信息，包含天气工具默认参数和支持的功能列表";
    }

    public static String getMimeType() {
        return "application/json";
    }
}
