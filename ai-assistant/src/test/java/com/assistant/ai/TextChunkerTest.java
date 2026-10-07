package com.assistant.ai;

import com.assistant.ai.knowledge.Chunk;
import com.assistant.ai.knowledge.Document;
import com.assistant.ai.knowledge.MarkdownDocumentLoader;
import com.assistant.ai.knowledge.TextChunker;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    private static void assertChunk(
            Chunk chunk, int index,
            int start, int end, String content) {
        assertEquals((Long) 7L, chunk.getDocumentId());
        assertEquals(index, chunk.getChunkIndex());
        assertEquals(start, chunk.getStartOffset());
        assertEquals(end, chunk.getEndOffset());
        assertEquals(content, chunk.getContent());
        assertEquals(content, "ABCDEFGHIJ".substring(start, end));
    }

    @Test
    void splitWithOverlapKeepsOffsets() {
        Document document = new Document();
        document.setId(7L);
        document.setContent("ABCDEFGHIJ");

        List<Chunk> chunks = chunker.split(document, 4, 1);

        assertEquals(3, chunks.size());
        assertChunk(chunks.get(0), 0, 0, 4, "ABCD");
        assertChunk(chunks.get(1), 1, 3, 7, "DEFG");
        assertChunk(chunks.get(2), 2, 6, 10, "GHIJ");
    }

    @Test
    void shortContentStaysOneChunk() {
        Document document = new Document();
        document.setContent("hi");

        List<Chunk> chunks = chunker.split(document, 10, 2);

        assertEquals(1, chunks.size());
        assertEquals("hi", chunks.get(0).getContent());
        assertEquals(0, chunks.get(0).getStartOffset());
        assertEquals(2, chunks.get(0).getEndOffset());
    }

    @Test
    void overlapMustBeSmallerThanMaxLength() {
        Document document = new Document();
        document.setContent("abed");

        assertThrows(IllegalArgumentException.class,
                () -> chunker.split(document, 4, 4));
    }

    @Test
    void splitByHeadingKeepsSectionAndOffsets() throws Exception {
        URI uri = java.util.Objects.requireNonNull(getClass().getClassLoader()
                        .getResource("knowledge/assistant-policy.md"))
                .toURI();
        Document document = new MarkdownDocumentLoader().load(Path.of(uri));
        document.setId(1L);

        List<Chunk> chunks = chunker.splitByHeading(document);

        assertEquals(5, chunks.size());
        assertTrue(chunks.get(0).getContent().startsWith("# AI 助手业务与权限说明"));
        assertTrue(chunks.get(2).getContent().startsWith("## 取消订单"));
        assertTrue(chunks.get(3).getContent().contains("SCOPE_mcp.weather"));

        String body = document.getContent();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            assertEquals(1L, chunk.getDocumentId());
            assertEquals(i, chunk.getChunkIndex());
            assertEquals(
                    body.substring(chunk.getStartOffset(), chunk.getEndOffset()),
                    chunk.getContent());
        }
    }

    @Test
    void headingThenWindowShiftsOffsetsToOriginalDocument() {
        Document document = new Document();
        document.setId(2L);
        document.setContent("INTRO\n## SEC\nABCDEFGHIJ");

        List<Chunk> chunks = chunker.splitByHeadingThenWindow(document, 10, 1);

        assertEquals(3, chunks.size());
        assertEquals("INTRO\n", chunks.get(0).getContent());
        assertEquals(0, chunks.get(0).getStartOffset());
        assertEquals(6, chunks.get(0).getEndOffset());

        assertEquals("## SEC\nABC", chunks.get(1).getContent());
        assertEquals(6, chunks.get(1).getStartOffset());
        assertEquals(16, chunks.get(1).getEndOffset());

        assertEquals("CDEFGHIJ", chunks.get(2).getContent());
        assertEquals(15, chunks.get(2).getStartOffset());
        assertEquals(23, chunks.get(2).getEndOffset());

        String body = document.getContent();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            assertEquals(2L, chunk.getDocumentId());
            assertEquals(i, chunk.getChunkIndex());
            assertEquals(
                    body.substring(chunk.getStartOffset(), chunk.getEndOffset()),
                    chunk.getContent());
            assertTrue(chunk.getContent().length() <= 10);
        }
    }

    @Test
    void policyHeadingThenWindowNeverExceedsMaxLength() throws Exception {
        URI uri = java.util.Objects.requireNonNull(getClass().getClassLoader()
                        .getResource("knowledge/assistant-policy.md"))
                .toURI();
        Document document = new MarkdownDocumentLoader().load(Path.of(uri));

        List<Chunk> byHeading = chunker.splitByHeading(document);
        List<Chunk> chunks = chunker.splitByHeadingThenWindow(document, 80, 10);

        assertTrue(chunks.size() > byHeading.size(), "应有节被窗口再切");
        String body = document.getContent();
        for (Chunk chunk : chunks) {
            assertTrue(chunk.getContent().length() <= 80);
            assertEquals(
                    body.substring(chunk.getStartOffset(), chunk.getEndOffset()),
                    chunk.getContent());
        }
    }
}