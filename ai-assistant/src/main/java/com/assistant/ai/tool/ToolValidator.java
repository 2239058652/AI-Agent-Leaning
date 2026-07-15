package com.assistant.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具参数校验器 — 执行前校验参数是否合法
 * <p>
 * 校验内容：
 * 1. 必填参数是否存在
 * 2. 参数类型是否匹配（string/integer/number）
 * <p>
 * 为什么不在 ToolService 里校验？
 * ToolService 只负责执行，校验逻辑拆出来后，未来可以：
 * - 在 Agent Loop 里提前校验，不合法的参数直接告诉模型重试
 * - 在敏感操作确认流程里复用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolValidator {

    /**
     * 校验工具参数
     *
     * @param tool     工具定义
     * @param argsJson 参数 JSON 字符串
     * @return 校验结果
     */
    public ValidationResult validate(ToolDefinition tool, String argsJson) {
        List<String> errors = new ArrayList<>();

        JsonNode schema = tool.getParameters();
        if (schema == null) {
            return ValidationResult.ok();
        }

        JsonNode args;
        try {
            args = new ObjectMapper().readTree(argsJson);
        } catch (Exception e) {
            return ValidationResult.fail("参数不是合法的 JSON: " + e.getMessage());
        }

        // 校验必填参数
        JsonNode required = schema.path("required");
        if (required.isArray()) {
            for (JsonNode field : required) {
                String fieldName = field.asText();
                if (!args.has(fieldName) || args.path(fieldName).isNull()) {
                    errors.add("缺少必填参数: " + fieldName);
                }
            }
        }

        // 校验参数类型
        JsonNode properties = schema.path("properties");
        if (properties.isObject()) {
            var fieldIt = properties.fields();
            while (fieldIt.hasNext()) {
                var entry = fieldIt.next();
                String fieldName = entry.getKey();
                JsonNode fieldSchema = entry.getValue();
                JsonNode value = args.path(fieldName);

                if (value.isMissingNode() || value.isNull()) {
                    continue; // 非必填参数缺失是允许的
                }

                String expectedType = fieldSchema.path("type").asText("");
                String actualType = getJsonNodeType(value);

                if (!expectedType.isEmpty() && !expectedType.equals(actualType)) {
                    errors.add("参数 " + fieldName + " 类型错误: 期望 " + expectedType + "，实际 " + actualType);
                }
            }
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }

    private String getJsonNodeType(JsonNode node) {
        if (node.isTextual()) return "string";
        if (node.isInt() || node.isLong()) return "integer";
        if (node.isDouble() || node.isFloat() || node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isArray()) return "array";
        if (node.isObject()) return "object";
        return "unknown";
    }

    /**
     * 校验结果
     */
    public record ValidationResult(boolean success, List<String> errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult fail(String error) {
            return new ValidationResult(false, List.of(error));
        }

        public static ValidationResult fail(List<String> errors) {
            return new ValidationResult(false, List.copyOf(errors));
        }
    }
}
