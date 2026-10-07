package com.assistant.ai.mapper;

import lombok.Data;

@Data
public class KnowledgeChunkRow {
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Integer startOffset;
    private Integer endOffset;
    private String embeddingJson;
}