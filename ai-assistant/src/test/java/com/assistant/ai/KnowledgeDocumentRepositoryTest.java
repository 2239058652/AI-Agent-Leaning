package com.assistant.ai;

import com.assistant.ai.knowledge.Document;
import com.assistant.ai.knowledge.KnowledgeDocumentRepository;
import com.assistant.ai.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDocumentRepositoryTest {

    @Test
    void blankOwnerDoesNotInsert() {
        KnowledgeDocumentMapper mapper = Mockito.mock(KnowledgeDocumentMapper.class);
        KnowledgeDocumentRepository repository = new KnowledgeDocumentRepository(mapper);
        Document document = new Document();
        document.setOwnerUserId("  ");

        assertThrows(IllegalArgumentException.class, () -> repository.save(document));
        Mockito.verifyNoInteractions(mapper);
    }

    @Test
    void listOwnedQueriesByUserId() {
        KnowledgeDocumentMapper mapper = Mockito.mock(KnowledgeDocumentMapper.class);
        Document mine = new Document();
        mine.setId(1L);
        mine.setOwnerUserId("user1");
        when(mapper.findByOwner("user1")).thenReturn(List.of(mine));

        List<Document> found = new KnowledgeDocumentRepository(mapper).listOwned("user1");

        assertEquals(1, found.size());
        assertEquals(1L, found.get(0).getId());
        verify(mapper).findByOwner("user1");
    }
}