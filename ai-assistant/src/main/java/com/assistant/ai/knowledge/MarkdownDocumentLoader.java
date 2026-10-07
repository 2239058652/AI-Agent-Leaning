package com.assistant.ai.knowledge;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class MarkdownDocumentLoader {

    public Document load(Path path) throws Exception {
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        return parse(raw, path.getFileName().toString());
    }

    Document parse(String raw, String fallbackSourceName) {
        String text = raw.replace("\r\n", "\n");
        Map<String, String> meta = new LinkedHashMap<>();
        String body = text;

        if (text.startsWith("---\n")) {
            int end = text.indexOf("\n---\n", 4);
            if (end >= 0) {
                String front = text.substring(4, end);
                body = text.substring(end + 5).stripLeading();
                for (String line : front.split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon <= 0) {
                        continue;
                    }
                    meta.put(line.substring(0, colon).trim(),
                            line.substring(colon + 1).trim());
                }
            }
        }

        Document document = new Document();
        document.setTitle(meta.getOrDefault("title", fallbackSourceName));
        document.setSourceName(meta.getOrDefault("source", fallbackSourceName));
        document.setSourceType(meta.getOrDefault("sourceType", "markdown"));
        document.setVersion(meta.get("version"));
        document.setContent(body);
        return document;
    }
}