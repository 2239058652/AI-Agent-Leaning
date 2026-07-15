package com.assistant.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

/**
 * MCP Server Spring Boot 启动类
 * <p>
 * 用 HTTP 传输替代 stdio，支持远程连接。
 * 启动后监听 http://localhost:8090/mcp
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(McpServerApplication.class, args);
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        System.out.println("""

                ╔═════════════════════════════════════════════════════════════╗
                ║                                                             ║
                ║   🔌 MCP Server Started Successfully!                        ║
                ║                                                             ║
                ║   🌐 Endpoint:  http://localhost:%d/mcp                     ║
                ║   🔧 Tool:      get_weather                                 ║
                ║                                                             ║
                ╚═════════════════════════════════════════════════════════════╝
                """.formatted(port));
    }
}
