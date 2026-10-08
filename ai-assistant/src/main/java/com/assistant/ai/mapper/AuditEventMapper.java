package com.assistant.ai.mapper;

import com.assistant.ai.audit.AuditEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuditEventMapper {

    int insert(AuditEvent event);

    /** 某用户一次执行的全部事件，按写入顺序 */
    List<AuditEvent> findByExecutionId(@Param("executionId") String executionId,
                                       @Param("userId") String userId);

    /** 某用户执行过的敏感操作 */
    List<AuditEvent> findSensitiveByUserId(@Param("userId") String userId);
}
