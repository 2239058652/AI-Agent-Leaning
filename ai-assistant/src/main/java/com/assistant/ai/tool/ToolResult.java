package com.assistant.ai.tool;

import lombok.Builder;
import lombok.Data;

/**
 * 工具执行结果 — 区分正常返回和需要确认
 *
 * 为什么不用 String？
 * 因为敏感操作需要返回"需要确认"的状态，
 * 如果用 String，ChatService 无法区分是正常结果还是需要确认。
 */
@Data
@Builder
public class ToolResult {

    /** 是否需要用户确认（敏感操作） */
    @Builder.Default
    private boolean needsConfirmation = false;

    /** 执行结果文本（正常返回时有值） */
    private String result;

    /** 需要确认的工具名（needsConfirmation=true 时有值） */
    private String toolName;

    /** 需要确认的参数（needsConfirmation=true 时有值） */
    private String argsJson;

    /** 静态工厂：正常结果 */
    public static ToolResult success(String result) {
        return ToolResult.builder()
                .needsConfirmation(false)
                .result(result)
                .build();
    }

    /** 静态工厂：需要确认 */
    public static ToolResult confirmRequired(String toolName, String argsJson) {
        return ToolResult.builder()
                .needsConfirmation(true)
                .toolName(toolName)
                .argsJson(argsJson)
                .build();
    }
}
