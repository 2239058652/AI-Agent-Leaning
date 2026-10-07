package com.assistant.ai.knowledge;

import com.assistant.ai.mapper.KnowledgeDocumentMapper;

import java.util.List;

public class KnowledgeDocumentRepository {

    private final KnowledgeDocumentMapper mapper;

    public KnowledgeDocumentRepository(KnowledgeDocumentMapper mapper) {
        this.mapper = mapper;
    }

    public void save(Document document) {
        if (document.getOwnerUserId() == null || document.getOwnerUserId().isBlank()) {
            throw new IllegalArgumentException("ownerUserId 不能为空");
        }
        mapper.insert(document);
    }

    public List<Document> listOwned(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        return mapper.findByOwner(userId);
    }
}