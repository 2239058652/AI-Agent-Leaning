package com.assistant.ai.audit;

import com.assistant.ai.mapper.AuditEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 审计写入与查询。
 * <p>
 * 写入失败只记日志，不打断正在进行的 Agent 执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRecorder {

    static final int DIGEST_LIMIT = 200;

    private final AuditEventMapper mapper;

    public void record(AuditEvent event) {
        if (event.getExecutionId() == null || event.getExecutionId().isBlank()) {
            throw new IllegalArgumentException("executionId 不能为空");
        }
        if (event.getEventType() == null || event.getEventType().isBlank()) {
            throw new IllegalArgumentException("eventType 不能为空");
        }
        event.setArgsDigest(digest(event.getArgsDigest()));
        try {
            mapper.insert(event);
        } catch (RuntimeException e) {
            log.error("审计写入失败 executionId={} event={}",
                    event.getExecutionId(), event.getEventType(), e);
        }
    }

    public List<AuditEvent> execution(String executionId, String userId) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("executionId 不能为空");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        return mapper.findByExecutionId(executionId, userId);
    }

    public List<AuditEvent> sensitiveOperations(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        return mapper.findSensitiveByUserId(userId);
    }

    static String digest(String argsJson) {
        if (argsJson == null) {
            return null;
        }
        String compact = argsJson.replaceAll("\\s+", " ").trim();
        if (compact.length() <= DIGEST_LIMIT) {
            return compact;
        }
        return compact.substring(0, DIGEST_LIMIT);
    }
}
