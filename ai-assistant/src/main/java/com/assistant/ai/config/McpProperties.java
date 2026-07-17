package com.assistant.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MCP 客户端配置
 * <p>
 * 对应 application.yaml 中的 mcp.server.* 配置项
 */
@Data
@Component
@ConfigurationProperties(prefix = "mcp.server")
public class McpProperties {

    /**
     * MCP Server 地址
     */
    private String url = "http://localhost:3190/mcp";

    /**
     * 是否启用 MCP 客户端
     */
    private boolean enabled = true;
}
