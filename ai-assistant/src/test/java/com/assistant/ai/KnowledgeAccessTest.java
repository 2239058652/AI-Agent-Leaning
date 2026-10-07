package com.assistant.ai;

import com.assistant.ai.knowledge.Chunk;
import com.assistant.ai.knowledge.ChunkRetriever;
import com.assistant.ai.knowledge.ChunkRetriever.ScoredChunk;
import com.assistant.ai.knowledge.Document;
import com.assistant.ai.knowledge.KnowledgeAccess;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeAccessTest {

    private final KnowledgeAccess access = new KnowledgeAccess();

    private static Document doc(Long id, String owner) {
        Document doc = new Document();
        doc.setId(id);
        doc.setOwnerUserId(owner);
        return doc;
    }

    private static Chunk chunk(Long documentId, String content) {
        Chunk chunk = new Chunk();
        chunk.setDocumentId(documentId);
        chunk.setContent(content);
        chunk.setEmbedding(new float[]{1f, 0f});
        return chunk;
    }

    @Test
    void filterBeforeSearchKeepsOwnChunk() {
        Document mine = doc(1L, "user1");
        Document secret = doc(2L, "user2");
        Chunk own = chunk(1L, "我的取消规则");
        Chunk hidden = chunk(2L, "别人的私人备注");
        Map<Long, Document> docs = Map.of(1L, mine, 2L, secret);

        List<ScoredChunk> unfiltered = new ChunkRetriever(new QueryOnlyModel())
                .search("取消", List.of(hidden, own), 1);
        assertSame(hidden, unfiltered.get(0).chunk(), "不过滤时 top1 是别人的文档");

        List<Chunk> visible = access.visibleChunks("user1", List.of(hidden, own), docs);
        List<ScoredChunk> filtered = new ChunkRetriever(new QueryOnlyModel())
                .search("取消", visible, 1);

        assertEquals(1, filtered.size());
        assertSame(own, filtered.get(0).chunk());
        assertFalse(visible.stream().anyMatch(c -> c.getContent().contains("私人备注")));
    }

    @Test
    void unknownDocumentIsRejected() {
        Chunk orphan = chunk(99L, "来历不明");
        List<Chunk> visible = access.visibleChunks("user1", List.of(orphan), Map.of());
        assertTrue(visible.isEmpty());
    }

    static final class QueryOnlyModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return new EmbeddingResponse(List.of(new Embedding(new float[]{1f, 0f}, 0)));
        }

        @Override
        public float[] embed(org.springframework.ai.document.Document document) {
            return embed(document.getText());
        }
    }
}