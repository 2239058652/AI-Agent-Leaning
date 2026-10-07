package com.assistant.ai.knowledge;

import com.assistant.ai.knowledge.ChunkRetriever.ScoredChunk;
import com.assistant.ai.knowledge.RagPromptAssembler.RagPrompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KnowledgeQuery {

    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final ChunkRetriever retriever;
    private final RagPromptAssembler assembler;

    public KnowledgeQuery(KnowledgeDocumentRepository documents,
                          KnowledgeChunkRepository chunks,
                          ChunkRetriever retriever,
                          RagPromptAssembler assembler) {
        this.documents = documents;
        this.chunks = chunks;
        this.retriever = retriever;
        this.assembler = assembler;
    }

    public RagPrompt ask(String userId, String question, int topK) {
        Map<Long, Document> ownedDocuments = new HashMap<>();
        for (Document document : documents.listOwned(userId)) {
            ownedDocuments.put(document.getId(), document);
        }
        List<Chunk> ownedChunks = chunks.listOwned(userId);
        List<ScoredChunk> hits = retriever.search(question, ownedChunks, topK);
        return assembler.assemble(question, hits, ownedDocuments);
    }
}