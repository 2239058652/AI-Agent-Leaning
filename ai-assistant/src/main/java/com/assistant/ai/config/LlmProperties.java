package com.assistant.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM API 配置 — 从 application.yaml 读取
 *
 * 配置前缀是 llm，对应 yaml 里的 llm.xxx
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    /** API Key */
    private String apiKey = "";

    /** API 基础地址 */
    private String baseUrl = "https://api.longcat.chat";

    /** 模型名 */
    private String model = "LongCat-2.0";

    /** 请求超时（秒） */
    private int timeout = 60;
}
