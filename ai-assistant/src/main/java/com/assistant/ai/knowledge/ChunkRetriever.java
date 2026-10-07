package com.assistant.ai.knowledge;

import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ChunkRetriever {

    private final EmbeddingModel embeddingModel;

    public ChunkRetriever(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维数不一致");
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * (double) b[i];
            normA += a[i] * (double) a[i];
            normB += b[i] * (double) b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public List<ScoredChunk> search(String query, List<Chunk> chunks, int topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK 必须大于 0");
        }
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        float[] queryVector = embeddingModel.embed(query);
        List<ScoredChunk> scored = new ArrayList<>();
        for (Chunk chunk : chunks) {
            float[] embedding = chunk.getEmbedding();
            if (embedding == null) {
                throw new IllegalStateException("块还没有向量");
            }
            scored.add(new ScoredChunk(chunk, cosine(queryVector, embedding)));
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return scored.subList(0, Math.min(topK, scored.size()));
    }

    public record ScoredChunk(Chunk chunk, double score) {
    }
}
