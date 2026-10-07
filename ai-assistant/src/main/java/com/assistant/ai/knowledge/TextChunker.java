package com.assistant.ai.knowledge;

import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;

public class TextChunker {

    @NonNull
    private static List<Chunk> createChunkList(Document document, List<Integer> starts, String content) {
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : content.length();
            String slice = content.substring(start, end);
            if (slice.isBlank()) {
                continue;
            }
            Chunk chunk = new Chunk();
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(chunks.size());
            chunk.setStartOffset(start);
            chunk.setEndOffset(end);
            chunk.setContent(slice);
            chunks.add(chunk);
        }
        return chunks;
    }

    public List<Chunk> split(Document document, int maxLength, int overlap) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength 必须大于 0");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap 不能为负");
        }
        if (overlap >= maxLength) {
            throw new IllegalArgumentException("overlap 必须小于 maxLength");
        }

        String content = document.getContent();
        if (content == null || content.isEmpty()) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < content.length()) {
            int end = Math.min(start + maxLength, content.length());

            Chunk chunk = new Chunk();
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(index);
            chunk.setStartOffset(start);
            chunk.setEndOffset(end);
            chunk.setContent(content.substring(start, end));
            chunks.add(chunk);

            if (end == content.length()) {
                break;
            }
            start = end - overlap;
            index++;
        }
        return chunks;
    }

    public List<Chunk> splitByHeading(Document document) {
        String content = document.getContent();
        if (content == null || content.isEmpty()) {
            return List.of();
        }

        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        int searchFrom = 0;
        while (true) {
            int heading = indexOfHeadingLine(content, searchFrom);
            if (heading < 0) {
                break;
            }
            if (heading > 0) {
                starts.add(heading);
            }
            searchFrom = heading + 1;
        }

        return createChunkList(document, starts, content);
    }

    public List<Chunk> splitByHeadingThenWindow(Document document, int maxLength, int overlap) {
        List<Chunk> headingChunks = splitByHeading(document);
        List<Chunk> result = new ArrayList<>();
        for (Chunk heading : headingChunks) {
            if (heading.getContent().length() <= maxLength) {
                heading.setChunkIndex(result.size());
                result.add(heading);
                continue;
            }
            Document piece = new Document();
            piece.setId(document.getId());
            piece.setContent(heading.getContent());
            int base = heading.getStartOffset();
            for (Chunk window : split(piece, maxLength, overlap)) {
                window.setStartOffset(window.getStartOffset() + base);
                window.setEndOffset(window.getEndOffset() + base);
                window.setChunkIndex(result.size());
                result.add(window);
            }
        }
        return result;
    }

    private int indexOfHeadingLine(String content, int from) {
        int i = from;
        while (i < content.length()) {
            boolean atLineStart = (i == 0 || content.charAt(i - 1) == '\n');
            if (atLineStart && content.startsWith("## ", i)) {
                return i;
            }
            int nl = content.indexOf('\n', i);
            if (nl < 0) {
                return -1;
            }
            i = nl + 1;
        }
        return -1;
    }
}