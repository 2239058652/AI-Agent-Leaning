package com.assistant.ai.mcp;

import com.assistant.ai.config.McpProperties;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MCP 客户端服务 — 管理与 MCP Server 的连接
 * <p>
 * 职责：
 * 1. 启动时连接 MCP Server（握手）
 * 2. 提供 listTools() 获取远程工具列表（工具发现）
 * 3. 提供 callTool() 调用远程工具（工具调用）
 * 4. 关闭时清理连接
 * <p>
 * 传输层：HttpClientStreamableHttpTransport（HTTP 协议）
 * 对应 mcp-server 端的 HttpServletStreamableServerTransportProvider
 */
@Slf4j
@Service
public class McpClientService {

    private final McpProperties mcpProperties;
    private McpSyncClient client;
    /**
     * -- GETTER --
     * MCP 客户端是否可用
     */
    @Getter
    private boolean available = false;

    public McpClientService(McpProperties mcpProperties) {
        this.mcpProperties = mcpProperties;
    }

    /**
     * 启动时连接 MCP Server
     * <p>
     * 三步：
     * 1. 创建传输层（用什么协议通信）
     * 2. 创建客户端（用 MCP 协议对话）
     * 3. 握手（交换身份和能力信息）
     */
    @PostConstruct
    public void init() {
        if (!mcpProperties.isEnabled()) {
            log.info("MCP 客户端已禁用（mcp.server.enabled=false）");
            return;
        }

        try {
            log.info("正在连接 MCP Server: {}", mcpProperties.getUrl());

            // 第1步：创建 HTTP 传输层
            var transport = HttpClientStreamableHttpTransport
                    .builder(mcpProperties.getUrl())
                    .build();

            // 第2步：创建同步客户端
            client = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(30))
                    .build();

            // 第3步：协议握手 — 交换身份、版本、能力信息
            client.initialize();

            available = true;
            log.info("MCP Server 连接成功");

            // 握手完成后，列出远程工具
            List<Tool> tools = listTools();
            log.info("MCP Server 提供 {} 个工具: {}", tools.size(), tools.stream().map(Tool::name).toList());

        } catch (Exception e) {
            log.warn("MCP Server 连接失败，MCP 工具将不可用: {}", e.getMessage());
            available = false;
        }
    }

    /**
     * 获取 MCP Server 提供的工具列表
     */
    public List<Tool> listTools() {
        if (!available || client == null) {
            log.warn("MCP 客户端不可用，无法列出工具");
            return Collections.emptyList();
        }

        try {
            return client.listTools().tools();
        } catch (Exception e) {
            log.error("列出 MCP 工具失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 调用 MCP 工具
     *
     * @param toolName 工具名称
     * @param args     参数（key-value 形式）
     * @return 工具执行结果文本
     */
    public String callTool(String toolName, Map<String, Object> args) {
        if (!available || client == null) {
            return "错误: MCP 客户端不可用";
        }

        try {
            log.info("调用 MCP 工具: {}({})", toolName, args);

            CallToolResult result = client.callTool(new CallToolRequest(toolName, args));

            // MCP 工具返回的是 Content 列表，提取文本内容
            StringBuilder sb = new StringBuilder();
            for (Content content : result.content()) {
                if (content instanceof TextContent text) {
                    sb.append(text.text());
                }
            }

            String resultText = sb.toString();
            log.info("MCP 工具返回: {}", resultText);
            return resultText;

        } catch (Exception e) {
            log.error("调用 MCP 工具失败: {}", toolName, e);
            return "错误: MCP 工具调用失败 — " + e.getMessage();
        }
    }

    /**
     * 关闭时清理连接
     */
    @PreDestroy
    public void destroy() {
        if (client != null) {
            try {
                client.close();
                log.info("MCP 客户端连接已关闭");
            } catch (Exception e) {
                log.warn("关闭 MCP 客户端异常", e);
            }
        }
    }
}
