package com.assistant.ai;

import com.assistant.ai.knowledge.Chunk;
import com.assistant.ai.knowledge.Document;
import com.assistant.ai.knowledge.KnowledgeChunkRepository;
import com.assistant.ai.knowledge.KnowledgeDocumentRepository;
import com.assistant.ai.mapper.KnowledgeChunkMapper;
import com.assistant.ai.mapper.KnowledgeDocumentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(KnowledgePersistenceTest.MapperConfig.class)
class KnowledgePersistenceTest {

    @Autowired
    private KnowledgeDocumentMapper documentMapper;
    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    private static Document doc(String owner, String title) {
        Document document = new Document();
        document.setTitle(title);
        document.setSourceName(title + ".md");
        document.setSourceType("markdown");
        document.setVersion("1");
        document.setContent(title);
        document.setOwnerUserId(owner);
        return document;
    }

    private static Chunk chunk(Long documentId, String content) {
        Chunk chunk = new Chunk();
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        chunk.setStartOffset(0);
        chunk.setEndOffset(content.length());
        chunk.setEmbedding(new float[]{1f, 0f});
        return chunk;
    }

    @Test
    void otherUsersChunkStaysInDatabase() {
        KnowledgeDocumentRepository documents = new KnowledgeDocumentRepository(documentMapper);
        KnowledgeChunkRepository chunks = new KnowledgeChunkRepository(chunkMapper, new ObjectMapper());

        Document mine = doc("user1", "取消规则");
        Document secret = doc("user2", "私人备注");
        documents.save(mine);
        documents.save(secret);
        chunks.save(chunk(mine.getId(), "只能取消自己的订单"));
        chunks.save(chunk(secret.getId(), "别人的私人备注"));

        List<Document> ownedDocs = documents.listOwned("user1");
        List<Chunk> ownedChunks = chunks.listOwned("user1");

        assertEquals(1, ownedDocs.size());
        assertEquals("取消规则", ownedDocs.get(0).getTitle());
        assertEquals(1, ownedChunks.size());
        assertEquals("只能取消自己的订单", ownedChunks.get(0).getContent());
        assertArrayEquals(new float[]{1f, 0f}, ownedChunks.get(0).getEmbedding());
        assertTrue(ownedChunks.stream().noneMatch(c -> c.getContent().contains("私人备注")));
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
    }
}