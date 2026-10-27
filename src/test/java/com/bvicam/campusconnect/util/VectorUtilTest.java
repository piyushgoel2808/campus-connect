package com.bvicam.campusconnect.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link VectorUtil}.
 * Verifies cosine similarity calculations, edge case guards, and JSON serialization.
 */
class VectorUtilTest {

    @Test
    @DisplayName("cosineSimilarity returns 1.0 for identical vectors")
    void cosineSimilarity_identicalVectors_returnsOne() {
        double[] v1 = {1.0, 2.0, 3.0};
        double[] v2 = {1.0, 2.0, 3.0};

        double similarity = VectorUtil.cosineSimilarity(v1, v2);
        assertThat(similarity).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("cosineSimilarity returns 0.0 for orthogonal vectors")
    void cosineSimilarity_orthogonalVectors_returnsZero() {
        double[] v1 = {1.0, 0.0};
        double[] v2 = {0.0, 1.0};

        double similarity = VectorUtil.cosineSimilarity(v1, v2);
        assertThat(similarity).isCloseTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("cosineSimilarity returns 0.0 when either vector is null, empty, or mismatched")
    void cosineSimilarity_invalidVectors_returnsZero() {
        assertThat(VectorUtil.cosineSimilarity(null, new double[]{1.0})).isEqualTo(0.0);
        assertThat(VectorUtil.cosineSimilarity(new double[]{1.0}, null)).isEqualTo(0.0);
        assertThat(VectorUtil.cosineSimilarity(new double[]{}, new double[]{})).isEqualTo(0.0);
        assertThat(VectorUtil.cosineSimilarity(new double[]{1.0}, new double[]{1.0, 2.0})).isEqualTo(0.0);
    }

    @Test
    @DisplayName("cosineSimilarity returns 0.0 for zero vectors")
    void cosineSimilarity_zeroVectors_returnsZero() {
        double[] v1 = {0.0, 0.0, 0.0};
        double[] v2 = {1.0, 2.0, 3.0};

        assertThat(VectorUtil.cosineSimilarity(v1, v2)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("embeddingToJson and embeddingFromJson serialize and deserialize float vectors")
    void embeddingJson_roundtrip_preservesCoordinates() {
        List<Double> vector = List.of(0.1234, -0.5678, 0.9999);

        String json = VectorUtil.toJson(vector);
        assertThat(json).isNotBlank();

        double[] parsed = VectorUtil.fromJson(json);
        assertThat(parsed).hasSize(3);
        assertThat(parsed[0]).isCloseTo(0.1234, within(1e-4));
        assertThat(parsed[1]).isCloseTo(-0.5678, within(1e-4));
        assertThat(parsed[2]).isCloseTo(0.9999, within(1e-4));
    }
}
