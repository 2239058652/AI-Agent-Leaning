package com.assistant.ai;

import com.assistant.ai.knowledge.Chunk;
import com.assistant.ai.knowledge.KnowledgeChunkRepository;
import com.assistant.ai.mapper.KnowledgeChunkMapper;
import com.assistant.ai.mapper.KnowledgeChunkRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeChunkRepositoryTest {

    @Test
    void missingDocumentIdDoesNotInsert() {
        KnowledgeChunkMapper mapper = Mockito.mock(KnowledgeChunkMapper.class);
        KnowledgeChunkRepository repository = new KnowledgeChunkRepository(mapper, new ObjectMapper());
        Chunk chunk = new Chunk();
        chunk.setEmbedding(new float[]{1f, 0f});

        assertThrows(IllegalArgumentException.class, () -> repository.save(chunk));
        Mockito.verifyNoInteractions(mapper);
    }

    @Test
    void listOwnedReadsVectorsForThatUser() throws Exception {
        KnowledgeChunkMapper mapper = Mockito.mock(KnowledgeChunkMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        KnowledgeChunkRow row = new KnowledgeChunkRow();
        row.setId(5L);
        row.setDocumentId(1L);
        row.setChunkIndex(0);
        row.setContent("只能取消自己的订单");
        row.setStartOffset(0);
        row.setEndOffset(9);
        row.setEmbeddingJson(objectMapper.writeValueAsString(new float[]{1f, 0f}));
        when(mapper.findByOwner("user1")).thenReturn(List.of(row));

        List<Chunk> found = new KnowledgeChunkRepository(mapper, objectMapper).listOwned("user1");

        assertEquals(1, found.size());
        assertArrayEquals(new float[]{1f, 0f}, found.get(0).getEmbedding());
        verify(mapper).findByOwner("user1");
    }
}