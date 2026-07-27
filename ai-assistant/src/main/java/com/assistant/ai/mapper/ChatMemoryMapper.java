package com.assistant.ai.mapper;

import com.assistant.ai.entity.ChatMemoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话记忆 Mapper — chat_memory 表的数据访问层
 */
@Mapper
public interface ChatMemoryMapper {

    /** 查询所有会话 ID（去重） */
    List<String> findConversationIds();

    /** 查询某会话的全部消息，按序号升序 */
    List<ChatMemoryEntry> findByConversationId(@Param("conversationId") String conversationId);

    /** 插入一条消息 */
    int insert(ChatMemoryEntry entry);

    /** 删除某会话的全部消息 */
    int deleteByConversationId(@Param("conversationId") String conversationId);
}
