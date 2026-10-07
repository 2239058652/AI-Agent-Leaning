package com.assistant.ai;

import com.assistant.ai.knowledge.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class KnowledgeImporterTest {

    @Test
    void savesDocumentBeforeChunksUseGeneratedId() {
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
        doAnswer(invocation -> {
            com.assistant.ai.knowledge.Document saved = invocation.getArgument(0);
            saved.setId(9L);
            return null;
        }).when(documents).save(any());

        com.assistant.ai.knowledge.Document document = new com.assistant.ai.knowledge.Document();
        document.setOwnerUserId("user1");
        document.setTitle("规则");
        document.setContent("# 规则\n\n导言\n\n## 取消\n\n只能取消自己的订单\n");

        new KnowledgeImporter(
                new TextChunker(),
                new ChunkEmbedder(new LengthEmbeddingModel()),
                documents,
                chunks
        ).importDocument(document, 500, 20);

        assertEquals(9L, document.getId());
        InOrder order = inOrder(documents, chunks);
        order.verify(documents).save(document);

        ArgumentCaptor<Chunk> captor = ArgumentCaptor.forClass(Chunk.class);
        order.verify(chunks, times(2)).save(captor.capture());
        List<Chunk> saved = captor.getAllValues();
        assertTrue(saved.get(0).getContent().startsWith("# 规则"));
        assertTrue(saved.get(1).getContent().startsWith("## 取消"));
        for (Chunk chunk : saved) {
            assertEquals(9L, chunk.getDocumentId());
            assertEquals(4, chunk.getEmbedding().length);
        }
    }

    static final class LengthEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> results = new ArrayList<>();
            List<String> texts = request.getInstructions();
            for (int i = 0; i < texts.size(); i++) {
                results.add(new Embedding(
                        new float[]{texts.get(i).length(), 0f, 0f, 0f}, i));
            }
            return new EmbeddingResponse(results);
        }

        @Override
        public float[] embed(org.springframework.ai.document.Document document) {
            return embed(document.getText());
        }
    }
}