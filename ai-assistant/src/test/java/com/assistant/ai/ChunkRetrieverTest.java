package com.assistant.ai;

import com.assistant.ai.knowledge.Chunk;
import com.assistant.ai.knowledge.ChunkRetriever;
import com.assistant.ai.knowledge.ChunkRetriever.ScoredChunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkRetrieverTest {

    private static Chunk chunk(String content, float... embedding) {
        Chunk chunk = new Chunk();
        chunk.setContent(content);
        chunk.setEmbedding(embedding);
        return chunk;
    }

    @Test
    void returnsClosestChunksFirst() {
        Chunk cancel = chunk("取消订单", 1f, 0f);
        Chunk weather = chunk("天气", 0f, 1f);
        Chunk confirm = chunk("确认", 0.6f, 0.8f);

        List<ScoredChunk> hits = new ChunkRetriever(new QueryOnlyModel())
                .search("取消", List.of(weather, confirm, cancel), 2);

        assertEquals(2, hits.size());
        assertSame(cancel, hits.get(0).chunk());
        assertEquals(1.0, hits.get(0).score(), 1e-6);
        assertSame(confirm, hits.get(1).chunk());
        assertEquals(0.6, hits.get(1).score(), 1e-6);
        assertNull(weather.getId());
    }

    @Test
    void differentDimensionIsRejected() {
        Chunk broken = chunk("坏块", 1f, 0f, 0f);
        assertThrows(IllegalArgumentException.class,
                () -> new ChunkRetriever(new QueryOnlyModel())
                        .search("取消", List.of(broken), 1));
    }

    static final class QueryOnlyModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            String text = request.getInstructions().get(0);
            if (!"取消".equals(text)) {
                throw new IllegalStateException("测试只嵌入问题「取消」");
            }
            return new EmbeddingResponse(List.of(new Embedding(new float[]{1f, 0f}, 0)));
        }

        @Override
        public float[] embed(Document document) {
            return embed(document.getText());
        }
    }
}