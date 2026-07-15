package com.assistant.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP Server Spring Boot 启动类
 * <p>
 * 用 HTTP 传输替代 stdio，支持远程连接。
 * 启动后监听 http://localhost:8090/mcp
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
