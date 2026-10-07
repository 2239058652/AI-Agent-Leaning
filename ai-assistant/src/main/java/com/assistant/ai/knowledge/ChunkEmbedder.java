package com.assistant.ai.knowledge;

import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

public class ChunkEmbedder {

    private final EmbeddingModel embeddingModel;

    public ChunkEmbedder(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<Chunk> embed(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<String> texts = chunks.stream().map(Chunk::getContent).toList();
        List<float[]> vectors = embeddingModel.embed(texts);
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("向量条数必须与块数相同");
        }
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbedding(vectors.get(i));
        }
        return chunks;
    }
}