package com.assistant.ai.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KnowledgeAccess {

    public List<Chunk> visibleChunks(String userId, List<Chunk> chunks, Map<Long, Document> documents) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<Chunk> visible = new ArrayList<>();
        for (Chunk chunk : chunks) {
            if (chunk.getDocumentId() == null) {
                continue;
            }
            Document doc = documents.get(chunk.getDocumentId());
            if (doc != null && userId.equals(doc.getOwnerUserId())) {
                visible.add(chunk);
            }
        }
        return visible;
    }
}