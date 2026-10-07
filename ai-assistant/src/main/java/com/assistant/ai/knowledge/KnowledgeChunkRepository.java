package com.assistant.ai.knowledge;

import com.assistant.ai.mapper.KnowledgeChunkMapper;
import com.assistant.ai.mapper.KnowledgeChunkRow;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeChunkRepository {

    private final KnowledgeChunkMapper mapper;
    private final ObjectMapper objectMapper;

    public KnowledgeChunkRepository(KnowledgeChunkMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void save(Chunk chunk) {
        if (chunk.getDocumentId() == null) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        if (chunk.getEmbedding() == null || chunk.getEmbedding().length == 0) {
            throw new IllegalArgumentException("embedding 不能为空");
        }
        try {
            mapper.insert(
                    chunk.getDocumentId(),
                    chunk.getChunkIndex(),
                    chunk.getContent(),
                    chunk.getStartOffset(),
                    chunk.getEndOffset(),
                    objectMapper.writeValueAsString(chunk.getEmbedding()));
        } catch (Exception e) {
            throw new IllegalStateException("向量无法写成 JSON", e);
        }
    }

    public List<Chunk> listOwned(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        List<Chunk> chunks = new ArrayList<>();
        for (KnowledgeChunkRow row : mapper.findByOwner(userId)) {
            chunks.add(toChunk(row));
        }
        return chunks;
    }

    private Chunk toChunk(KnowledgeChunkRow row) {
        try {
            Chunk chunk = new Chunk();
            chunk.setId(row.getId());
            chunk.setDocumentId(row.getDocumentId());
            chunk.setChunkIndex(row.getChunkIndex());
            chunk.setContent(row.getContent());
            chunk.setStartOffset(row.getStartOffset());
            chunk.setEndOffset(row.getEndOffset());
            chunk.setEmbedding(objectMapper.readValue(row.getEmbeddingJson(), float[].class));
            return chunk;
        } catch (Exception e) {
            throw new IllegalStateException("向量 JSON 无法读回", e);
        }
    }
}