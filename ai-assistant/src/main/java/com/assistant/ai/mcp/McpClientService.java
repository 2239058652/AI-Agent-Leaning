package com.assistant.ai.mcp;

import com.assistant.ai.config.McpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
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
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private McpSyncClient client;
    private volatile AccessToken accessToken;
    /**
     * -- GETTER --
     * MCP 客户端是否可用
     */
    @Getter
    private boolean available = false;

    public McpClientService(McpProperties mcpProperties, ObjectMapper objectMapper) {
        this.mcpProperties = mcpProperties;
        this.objectMapper = objectMapper;
    }


    private AccessToken requestAccessToken() {
        try {
            String credentials = mcpProperties.getClientId()
                    + ":" + mcpProperties.getClientSecret();

            String basicToken = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            String form = "grant_type=client_credentials"
                    + "&scope=" + URLEncoder.encode(
                    mcpProperties.getScope(),
                    StandardCharsets.UTF_8
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mcpProperties.getTokenUrl()))
                    .header("Authorization", "Basic " + basicToken)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "获取 MCP Token 失败: HTTP "
                                + response.statusCode()
                                + " - " + response.body()
                );
            }

            JsonNode body = objectMapper.readTree(response.body());

            String value = body.path("access_token").asText();
            long expiresIn = body.path("expires_in").asLong();

            if (value.isBlank()) {
                throw new IllegalStateException("Token 响应中缺少 access_token");
            }
            if (expiresIn <= 0) {
                throw new IllegalStateException("Token 响应中缺少有效的 expires_in");
            }

            long expiresAt = System.currentTimeMillis() + expiresIn * 1000L;
            return new AccessToken(value, expiresAt);
        } catch (Exception e) {
            throw new IllegalStateException("获取 MCP Token 失败", e);
        }
    }

    private AccessToken getValidAccessToken() {
        AccessToken current = accessToken;

        if (current == null || current.isExpired()) {
            current = requestAccessToken();
            accessToken = current;
            log.info("MCP Token 已刷新");
        }

        return current;
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
            this.accessToken = requestAccessToken();
            log.info("Authorization Server Token 获取成功");
            log.info("正在连接 MCP Server: {}", mcpProperties.getUrl());

            // 第1步：创建 HTTP 传输层
            var transport = HttpClientStreamableHttpTransport
                    .builder(mcpProperties.getUrl())
                    .httpRequestCustomizer((requestBuilder, method, uri, body, context) ->
                            requestBuilder.header("Authorization", "Bearer " + getValidAccessToken().value())
                    )
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

    private record AccessToken(String value, long expiresAt) {
        boolean isExpired() {
            // 提前 30 秒刷新，避免请求刚发出就过期
            return System.currentTimeMillis() >= expiresAt - 30_000L;
        }
    }
}
