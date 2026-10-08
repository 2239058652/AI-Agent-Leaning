-- 阶段9 审计表。在 ai_assistant 库手工执行一次，不要重复执行。
-- sensitive 是 MySQL 保留字，字段使用 sensitive_flag。
CREATE TABLE agent_audit_event
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    execution_id    VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NULL,
    conversation_id VARCHAR(128) NULL,
    event_type      VARCHAR(32)  NOT NULL,
    tool_name       VARCHAR(64)  NULL,
    args_digest     VARCHAR(200) NULL,
    sensitive_flag  TINYINT      NULL,
    success         TINYINT      NULL,
    reason          VARCHAR(255) NULL,
    span_type       VARCHAR(16)  NULL,
    span_index      INT          NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_execution (execution_id),
    KEY idx_audit_user_sensitive (user_id, sensitive_flag)
);

ALTER TABLE agent_audit_event
    ADD COLUMN span_type  VARCHAR(16) NULL AFTER reason,
    ADD COLUMN span_index INT         NULL AFTER span_type;
