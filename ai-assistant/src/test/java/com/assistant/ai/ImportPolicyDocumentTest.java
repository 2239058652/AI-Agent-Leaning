package com.assistant.ai;

import com.assistant.ai.knowledge.*;
import com.assistant.ai.mapper.KnowledgeChunkMapper;
import com.assistant.ai.mapper.KnowledgeDocumentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ImportPolicyDocumentTest.MapperConfig.class)
@EnabledIfEnvironmentVariable(named = "API_MODEL_KEY", matches = ".+")
class ImportPolicyDocumentTest {

    @Autowired
    private KnowledgeDocumentMapper documentMapper;
    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Test
    @Commit
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void importPolicyForAdmin() throws Exception {
        URI uri = Objects.requireNonNull(getClass().getClassLoader()
                        .getResource("knowledge/assistant-policy.md"))
                .toURI();
        Document document = new MarkdownDocumentLoader().load(Path.of(uri));
        document.setOwnerUserId("admin");

        KnowledgeDocumentRepository documents = new KnowledgeDocumentRepository(documentMapper);
        KnowledgeChunkRepository chunks = new KnowledgeChunkRepository(chunkMapper, new ObjectMapper());
        new KnowledgeImporter(
                new TextChunker(),
                new ChunkEmbedder(new MapperConfig().embeddingModel()),
                documents,
                chunks
        ).importDocument(document, 500, 20);

        assertFalse(chunks.listOwned("admin").isEmpty());
    }

    @Configuration
    @MapperScan("com.assistant.ai.mapper")
    static class MapperConfig {
        @Bean
        org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(
                javax.sql.DataSource dataSource) throws Exception {
            org.mybatis.spring.SqlSessionFactoryBean factory =
                    new org.mybatis.spring.SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                    .getResources("classpath:mapper/*.xml"));
            org.apache.ibatis.session.Configuration configuration =
                    new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }

        @Bean
        EmbeddingModel embeddingModel() {
            ZhiPuAiApi api = ZhiPuAiApi.builder()
                    .apiKey(System.getenv("API_MODEL_KEY"))
                    .build();
            return new ZhiPuAiEmbeddingModel(api, MetadataMode.NONE);
        }
    }
}