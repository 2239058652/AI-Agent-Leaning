package com.assistant.ai.knowledge;

import java.util.List;

public class KnowledgeImporter {

    private final TextChunker chunker;
    private final ChunkEmbedder embedder;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;

    public KnowledgeImporter(TextChunker chunker,
                             ChunkEmbedder embedder,
                             KnowledgeDocumentRepository documents,
                             KnowledgeChunkRepository chunks) {
        this.chunker = chunker;
        this.embedder = embedder;
        this.documents = documents;
        this.chunks = chunks;
    }

    public void importDocument(Document document, int maxLength, int overlap) {
        List<Chunk> pieces = chunker.splitByHeadingThenWindow(document, maxLength, overlap);
        embedder.embed(pieces);
        documents.save(document);
        for (Chunk piece : pieces) {
            piece.setDocumentId(document.getId());
            chunks.save(piece);
        }
    }
}