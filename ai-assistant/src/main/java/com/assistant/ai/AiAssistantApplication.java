package com.assistant.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

@SpringBootApplication
public class AiAssistantApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(AiAssistantApplication.class, args);
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        System.out.println("""

                ╔═════════════════════════════════════════════════════════════╗
                ║                                                             ║
                ║   🤖 AI Assistant Started Successfully!                      ║
                ║                                                             ║
                ║   🌐 Chat:      http://localhost:%d/api/chat                ║
                ║   📡 Stream:    http://localhost:%d/api/chat/stream          ║
                ║   🔧 Tool:      http://localhost:%d/api/chat/tool-stream     ║
                ║                                                             ║
                ╚═════════════════════════════════════════════════════════════╝
                """.formatted(port, port, port));
    }
}
