package com.assistant.ai;

import com.assistant.ai.audit.AuditContext;
import com.assistant.ai.audit.AuditEvent;
import com.assistant.ai.audit.AuditExecutionHolder;
import com.assistant.ai.audit.AuditRecorder;
import com.assistant.ai.mapper.AuditEventMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditRecorderTest {

    @Test
    void recordStoresArgsDigestAndKeepsExecutionId() {
        AuditEventMapper mapper = Mockito.mock(AuditEventMapper.class);
        AuditRecorder recorder = new AuditRecorder(mapper);

        recorder.record(AuditEvent.builder()
                .executionId("exec-1")
                .userId("user1")
                .eventType("tool_called")
                .toolName("query_orders")
                .argsDigest("{\"status\": \"PAID\"}")
                .build());

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mapper).insert(saved.capture());
        assertEquals("exec-1", saved.getValue().getExecutionId());
        assertEquals("{\"status\": \"PAID\"}", saved.getValue().getArgsDigest());
    }

    @Test
    void longArgsAreCutToDigest() {
        AuditEventMapper mapper = Mockito.mock(AuditEventMapper.class);
        AuditRecorder recorder = new AuditRecorder(mapper);

        recorder.record(AuditEvent.builder()
                .executionId("exec-1")
                .eventType("tool_called")
                .argsDigest("x".repeat(240))
                .build());

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mapper).insert(saved.capture());
        // 200 是 agent_audit_event.args_digest 的列宽，也是 AuditRecorder 的截断长度
        assertEquals(200, saved.getValue().getArgsDigest().length());
    }

    @Test
    void oneSpanKeepsOneIndexAcrossItsEvents() {
        AuditEventMapper mapper = Mockito.mock(AuditEventMapper.class);
        AuditRecorder recorder = new AuditRecorder(mapper);
        AuditExecutionHolder.put(new AuditContext("exec-1", "user1", "user1:c1"));
        try {
            // 一个工具 span：起点分配一次编号，tool_called 与 tool_succeeded 共用
            int toolIndex = AuditContext.allocateSpanIndex("exec-1", "tool");
            recorder.record(event("tool_called", "tool", toolIndex));
            recorder.record(event("tool_succeeded", "tool", toolIndex));

            // 两次模型请求 = 两个 model span，各占一个新编号
            recorder.record(event("model_completed", "model",
                    AuditContext.allocateSpanIndex("exec-1", "model")));
            recorder.record(event("model_completed", "model",
                    AuditContext.allocateSpanIndex("exec-1", "model")));
        } finally {
            AuditExecutionHolder.remove("exec-1");
        }

        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mapper, Mockito.times(4)).insert(saved.capture());
        assertEquals(1, saved.getAllValues().get(0).getSpanIndex());
        assertEquals(1, saved.getAllValues().get(1).getSpanIndex());
        assertEquals(1, saved.getAllValues().get(2).getSpanIndex());
        assertEquals(2, saved.getAllValues().get(3).getSpanIndex());
    }

    private AuditEvent event(String eventType, String spanType, Integer spanIndex) {
        return AuditEvent.builder()
                .executionId("exec-1")
                .eventType(eventType)
                .spanType(spanType)
                .spanIndex(spanIndex)
                .build();
    }

    @Test
    void queriesStayInsideTheUser() {
        AuditEventMapper mapper = Mockito.mock(AuditEventMapper.class);
        AuditEvent event = AuditEvent.builder().executionId("exec-1").userId("user1").build();
        when(mapper.findByExecutionId("exec-1", "user1")).thenReturn(List.of(event));
        when(mapper.findSensitiveByUserId("user1")).thenReturn(List.of(event));
        AuditRecorder recorder = new AuditRecorder(mapper);

        assertEquals(1, recorder.execution("exec-1", "user1").size());
        assertEquals(1, recorder.sensitiveOperations("user1").size());
        verify(mapper).findByExecutionId("exec-1", "user1");
        verify(mapper).findSensitiveByUserId("user1");
    }

    @Test
    void missingIdentityIsRejected() {
        AuditRecorder recorder = new AuditRecorder(Mockito.mock(AuditEventMapper.class));

        assertThrows(IllegalArgumentException.class, () -> recorder.execution("  ", "user1"));
        assertThrows(IllegalArgumentException.class, () -> recorder.sensitiveOperations(null));
    }
}
