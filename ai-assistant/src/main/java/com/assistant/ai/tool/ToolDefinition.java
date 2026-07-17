package com.assistant.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

/**
 * 工具定义 — 描述一个工具的元信息
 * <p>
 * 每个工具在注册时提供这些信息，Registry 统一管理。
 * ChatService 从中生成发给模型的 tools JSON 数组。
 */
@Data
@Builder
public class ToolDefinition {

    /** 工具名称，模型通过这个名字调用工具 */
    private String name;

    /** 工具描述，模型根据这个决定什么时候调用 */
    private String description;

    /** 参数的 JSON Schema，告诉模型需要传哪些参数 */
    private JsonNode parameters;

    /**
     * 是否敏感操作。
     * 当前4个工具都是只读的，全部为 false。
     * 未来加写操作时（如删除数据、修改配置），设为 true，
     * 后端会拒绝直接执行，要求用户确认后再执行。
     */
    @Builder.Default
    private boolean sensitive = false;

    /**
     * 工具来源 — 决定由谁执行
     * LOCAL: 本地 Java 代码执行
     * MCP:   通过 MCP 协议调用远程服务
     */
    @Builder.Default
    private ToolSource source = ToolSource.LOCAL;

    /**
     * MCP Server 名称（仅 source=MCP 时有值）
     * 预留字段，未来支持多个 MCP Server 时用来区分来源
     */
    private String mcpServer;

    /**
     * 工具来源枚举
     */
    public enum ToolSource {
        /** 本地工具 — 在本进程内执行 */
        LOCAL,
        /** MCP 工具 — 通过 MCP 协议调用远程服务 */
        MCP
    }
}
