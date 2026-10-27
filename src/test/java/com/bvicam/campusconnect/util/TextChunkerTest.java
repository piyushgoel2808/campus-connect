package com.bvicam.campusconnect.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TextChunker}.
 * Verifies chunk sizing, overlapping boundaries, and edge case safety.
 */
class TextChunkerTest {

    @Test
    @DisplayName("chunkText with short text returns single chunk")
    void chunkText_shortText_returnsSingleChunk() {
        String shortText = "Bharati Vidyapeeth's Institute of Computer Applications and Management.";
        List<String> chunks = TextChunker.chunkText(shortText, 500, 100);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(shortText);
    }

    @Test
    @DisplayName("chunkText with long text splits into overlapping chunks")
    void chunkText_longText_splitsWithOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            sb.append("Sentence ").append(i).append(": BVICAM MCA provides high-quality software engineering education. ");
        }
        String longText = sb.toString();

        List<String> chunks = TextChunker.chunkText(longText, 300, 50);

        assertThat(chunks.size()).isGreaterThan(1);
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(350);
            assertThat(chunk).isNotBlank();
        }

        // Verify overlap: start of second chunk contains words from end of first chunk
        assertThat(longText).contains(chunks.get(0));
    }

    @Test
    @DisplayName("chunkText with null or blank string returns empty list")
    void chunkText_emptyString_returnsEmptyList() {
        assertThat(TextChunker.chunkText(null, 500, 100)).isEmpty();
        assertThat(TextChunker.chunkText("   ", 500, 100)).isEmpty();
    }
}
