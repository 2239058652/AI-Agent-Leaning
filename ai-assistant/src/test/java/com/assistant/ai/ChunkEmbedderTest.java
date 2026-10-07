package com.assistant.ai;

import com.assistant.ai.knowledge.Chunk;
import com.assistant.ai.knowledge.ChunkEmbedder;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkEmbedderTest {

    @Test
    void embedsEachChunkInOrder() {
        Chunk a = new Chunk();
        a.setContent("ab");
        Chunk b = new Chunk();
        b.setContent("abcd");

        List<Chunk> result = new ChunkEmbedder(new FakeEmbeddingModel())
                .embed(List.of(a, b));

        assertSame(a, result.get(0));
        assertArrayEquals(new float[]{2f, 0f, 0f, 0f}, a.getEmbedding());
        assertArrayEquals(new float[]{4f, 0f, 0f, 0f}, b.getEmbedding());
        assertEquals(4, a.getEmbedding().length);
        assertEquals(4, b.getEmbedding().length);
    }

    @Test
    void emptyListDoesNotCallModel() {
        List<Chunk> result = new ChunkEmbedder(new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                fail("空列表不应调用模型");
                return null;
            }

            @Override
            public float[] embed(Document document) {
                fail("空列表不应调用模型");
                return new float[0];
            }
        }).embed(List.of());

        assertTrue(result.isEmpty());
    }

    static final class FakeEmbeddingModel implements EmbeddingModel {
        private static float[] vectorFor(String text) {
            return new float[]{text.length(), 0f, 0f, 0f};
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<String> texts = request.getInstructions();
            List<Embedding> results = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                results.add(new Embedding(vectorFor(texts.get(i)), i));
            }
            return new EmbeddingResponse(results);
        }

        @Override
        public float[] embed(Document document) {
            return embed(document.getText());
        }
    }
}