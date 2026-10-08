package com.assistant.ai.audit;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 项目未引入 Actuator，Spring AI 默认拿到的是空注册表，模型调用不会产生回调。
 * 这里提供真正的注册表，并把模型审计挂上去。
 */
@Configuration
public class AuditObservationConfig {

    @Bean
    public ObservationRegistry observationRegistry(ModelAuditHandler handler) {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(handler);
        return registry;
    }
}
