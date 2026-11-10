package com.bvicam.campusconnect.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility for breaking long document and web text into overlapping chunks
 * suitable for vector embedding and retrieval-augmented generation (RAG).
 */
public final class TextChunker {

    private TextChunker() {
        // Utility class
    }

    /**
     * Splits input text into overlapping chunks with sliding window.
     *
     * @param text      raw source text
     * @param chunkSize maximum length of each chunk
     * @param overlap   overlap length between consecutive chunks
     * @return ordered list of chunk strings
     */
    public static List<String> chunkText(String text, int chunkSize, int overlap) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String cleaned = text.trim()
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n{2,} *", "\n\n")
                .replaceAll(" *(?<!\\n)\\n(?!\\n) *", " ")
                .trim();

        int targetChunkSize = (chunkSize > 0) ? chunkSize : 600;
        if (cleaned.length() <= targetChunkSize) {
            return List.of(cleaned);
        }
        int targetOverlap = Math.max(0, overlap);
        if (targetOverlap >= targetChunkSize) {
            targetOverlap = targetChunkSize / 2;
        }

        List<String> chunks = new ArrayList<>();
        int length = cleaned.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + targetChunkSize, length);

            // Favor natural word boundaries when splitting before end
            if (end < length) {
                int lastBreak = Math.max(cleaned.lastIndexOf(' ', end), cleaned.lastIndexOf('\n', end));
                if (lastBreak > start + (targetChunkSize / 2)) {
                    end = lastBreak;
                }
            }

            String chunk = cleaned.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= length) {
                break;
            }

            int nextStart = end - targetOverlap;
            if (nextStart <= start) {
                nextStart = start + 1;
            }
            start = nextStart;
        }

        return chunks;
    }
}
