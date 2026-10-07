package com.assistant.ai;

import com.assistant.ai.knowledge.Document;
import com.assistant.ai.knowledge.MarkdownDocumentLoader;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownDocumentLoaderTest {

    @Test
    void loadPolicyKeepsMetadataAndStripsFrontMatter() throws Exception {
        URI uri = Objects.requireNonNull(getClass().getClassLoader()
                        .getResource("knowledge/assistant-policy.md"))
                .toURI();
        Document doc = new MarkdownDocumentLoader().load(Path.of(uri));

        assertEquals("AI 助手业务与权限说明", doc.getTitle());
        assertEquals("assistant-policy.md", doc.getSourceName());
        assertEquals("markdown", doc.getSourceType());
        assertEquals("2026-09-14", doc.getVersion());
        assertTrue(doc.getContent().startsWith("# AI 助手业务与权限说明"));
        assertTrue(doc.getContent().contains("取消订单"));
        assertFalse(doc.getContent().contains("sourceType: markdown"));
    }
}