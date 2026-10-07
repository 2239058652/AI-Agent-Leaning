package com.assistant.ai.knowledge;

import lombok.Data;

@Data
public class Chunk {
    private Long id;

    private Long documentId;

    private Integer chunkIndex;

    private String content;

    private Integer startOffset;

    private Integer endOffset;

    private float[] embedding;
}
