package com.assistant.ai.audit;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一次 Agent 执行中的一个审计事件，对应 agent_audit_event 表的一行。
 */
@Data
@Builder
public class AuditEvent {

    private Long id;

    /** 同一次 Agent 执行的全部事件共用这个 ID */
    private String executionId;

    private String userId;

    private String conversationId;

    /**
     * execution_started、model_requested、tool_called、tool_succeeded、tool_failed、
     * confirmation_required、sensitive_executed、execution_completed、execution_failed
     */
    private String eventType;

    private String toolName;

    /** 工具入参摘要，不是完整参数 */
    private String argsDigest;

    /** execution、model、tool、mcp。同一次执行里用它区分层级 */
    private String spanType;

    /**
     * 该层级里第几个 span，从 1 开始。
     * <p>
     * 同一个 span 产生的所有事件（例如 tool_called 与 tool_succeeded）共用同一个编号；
     * 编号在 span 起点分配一次，不是按事件递增。
     */
    private Integer spanIndex;

    private Boolean sensitive;

    private Boolean success;

    private String reason;

    private LocalDateTime createdAt;
}
