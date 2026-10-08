package com.assistant.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

@SpringBootApplication
public class AuthorizationServerApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(AuthorizationServerApplication.class, args);
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        System.out.printf("""
                
                ╔═════════════════════════════════════════════════════════════╗
                ║                                                             ║
                ║   🔐 Authorization Server Started Successfully!              ║
                ║                                                             ║
                ║   🌐 Login:     http://localhost:%d/login                  ║
                ║   🎫 Token:     http://localhost:%d/oauth2/token           ║
                ║                                                             ║
                ╚═════════════════════════════════════════════════════════════╝
                %n""", port, port);
    }
}