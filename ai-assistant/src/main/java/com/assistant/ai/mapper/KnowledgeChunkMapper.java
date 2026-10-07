package com.assistant.ai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper {

    int insert(@Param("documentId") Long documentId,
               @Param("chunkIndex") Integer chunkIndex,
               @Param("content") String content,
               @Param("startOffset") Integer startOffset,
               @Param("endOffset") Integer endOffset,
               @Param("embeddingJson") String embeddingJson);

    List<KnowledgeChunkRow> findByOwner(@Param("ownerUserId") String ownerUserId);
}