package com.assistant.ai;

import com.assistant.ai.tool.ToolDefinition;
import com.assistant.ai.tool.ToolValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具参数校验测试 — 验证必填参数和类型检查
 */
class ToolValidatorTest {

    private ToolValidator validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        validator = new ToolValidator();
    }

    private ToolDefinition buildToolWithRequired(String requiredField, String type) {
        ObjectNode properties = objectMapper.createObjectNode();
        ObjectNode fieldSchema = objectMapper.createObjectNode();
        fieldSchema.put("type", type);
        properties.set(requiredField, fieldSchema);

        ObjectNode schema = objectMapper.createObjectNode();
        schema.set("properties", properties);
        schema.set("required", objectMapper.createArrayNode().add(requiredField));

        return ToolDefinition.builder()
                .name("test_tool")
                .description("test")
                .parameters(schema)
                .build();
    }

    @Test
    void missingRequiredFieldShouldFail() {
        ToolDefinition tool = buildToolWithRequired("expression", "string");
        var result = validator.validate(tool, "{}");
        assertFalse(result.success());
        assertTrue(result.errors().get(0).contains("expression"));
    }

    @Test
    void correctTypeShouldPass() {
        ToolDefinition tool = buildToolWithRequired("expression", "string");
        var result = validator.validate(tool, "{\"expression\":\"2+3\"}");
        assertTrue(result.success());
    }

    @Test
    void wrongTypeShouldFail() {
        ToolDefinition tool = buildToolWithRequired("expression", "string");
        var result = validator.validate(tool, "{\"expression\":123}");
        assertFalse(result.success());
        assertTrue(result.errors().get(0).contains("类型错误"));
    }

    @Test
    void invalidJsonShouldFail() {
        ToolDefinition tool = buildToolWithRequired("expression", "string");
        var result = validator.validate(tool, "not json");
        assertFalse(result.success());
        assertTrue(result.errors().get(0).contains("JSON"));
    }

    @Test
    void optionalFieldMissingShouldPass() {
        // 构建一个有可选参数的工具
        ObjectNode properties = objectMapper.createObjectNode();
        ObjectNode citySchema = objectMapper.createObjectNode();
        citySchema.put("type", "string");
        properties.set("city", citySchema);

        ObjectNode daysSchema = objectMapper.createObjectNode();
        daysSchema.put("type", "integer");
        properties.set("days", daysSchema);

        ObjectNode schema = objectMapper.createObjectNode();
        schema.set("properties", properties);
        schema.set("required", objectMapper.createArrayNode().add("city"));

        ToolDefinition tool = ToolDefinition.builder()
                .name("get_weather")
                .description("test")
                .parameters(schema)
                .build();

        // 只传必填的 city，不传可选的 days
        var result = validator.validate(tool, "{\"city\":\"北京\"}");
        assertTrue(result.success());
    }
}
