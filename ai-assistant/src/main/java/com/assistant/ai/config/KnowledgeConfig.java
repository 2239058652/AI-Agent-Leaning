package com.assistant.ai.config;

import com.assistant.ai.knowledge.*;
import com.assistant.ai.mapper.KnowledgeChunkMapper;
import com.assistant.ai.mapper.KnowledgeDocumentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeConfig {

    @Bean
    public TextChunker textChunker() {
        return new TextChunker();
    }

    @Bean
    public ChunkEmbedder chunkEmbedder(EmbeddingModel embeddingModel) {
        return new ChunkEmbedder(embeddingModel);
    }

    @Bean
    public KnowledgeDocumentRepository knowledgeDocumentRepository(KnowledgeDocumentMapper mapper) {
        return new KnowledgeDocumentRepository(mapper);
    }

    @Bean
    public KnowledgeChunkRepository knowledgeChunkRepository(
            KnowledgeChunkMapper mapper, ObjectMapper objectMapper) {
        return new KnowledgeChunkRepository(mapper, objectMapper);
    }

    @Bean
    public ChunkRetriever chunkRetriever(EmbeddingModel embeddingModel) {
        return new ChunkRetriever(embeddingModel);
    }

    @Bean
    public RagPromptAssembler ragPromptAssembler() {
        return new RagPromptAssembler();
    }

    @Bean
    public KnowledgeQuery knowledgeQuery(KnowledgeDocumentRepository documents,
                                         KnowledgeChunkRepository chunks,
                                         ChunkRetriever retriever,
                                         RagPromptAssembler assembler) {
        return new KnowledgeQuery(documents, chunks, retriever, assembler);
    }
}