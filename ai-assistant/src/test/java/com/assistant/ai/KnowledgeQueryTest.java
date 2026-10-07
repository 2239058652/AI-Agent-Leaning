package com.assistant.ai;

import com.assistant.ai.knowledge.*;
import com.assistant.ai.knowledge.RagPromptAssembler.RagPrompt;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeQueryTest {

    @Test
    void askReadsOnlyOwnedRows() {
        Document mine = new Document();
        mine.setId(1L);
        mine.setOwnerUserId("user1");
        mine.setTitle("取消规则");
        mine.setSourceName("mine.md");

        Chunk own = new Chunk();
        own.setDocumentId(1L);
        own.setContent("只能取消自己的订单");
        own.setStartOffset(0);
        own.setEndOffset(9);
        own.setEmbedding(new float[]{1f, 0f});

        KnowledgeDocumentRepository documents = Mockito.mock(KnowledgeDocumentRepository.class);
        KnowledgeChunkRepository chunks = Mockito.mock(KnowledgeChunkRepository.class);
        when(documents.listOwned("user1")).thenReturn(List.of(mine));
        when(chunks.listOwned("user1")).thenReturn(List.of(own));

        RagPrompt prompt = new KnowledgeQuery(
                documents,
                chunks,
                new ChunkRetriever(new FixedVectorModel()),
                new RagPromptAssembler()
        ).ask("user1", "能不能取消别人的订单？", 1);

        verify(documents).listOwned("user1");
        verify(chunks).listOwned("user1");
        assertTrue(prompt.userMessage().contains("只能取消自己的订单"));
        assertTrue(prompt.userMessage().contains("来源=mine.md"));
        assertFalse(prompt.userMessage().contains("别人的私人备注"));
    }

    static final class FixedVectorModel implements EmbeddingModel {
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