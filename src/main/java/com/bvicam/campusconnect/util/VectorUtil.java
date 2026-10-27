package com.bvicam.campusconnect.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Utility functions for vector math, cosine similarity ranking, and embedding serialization.
 */
public final class VectorUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private VectorUtil() {
        // Utility class
    }

    /**
     * Calculates the cosine similarity between two double vectors.
     *
     * @param v1 first vector
     * @param v2 second vector
     * @return similarity score between -1.0 and 1.0, or 0.0 if invalid or orthogonal
     */
    public static double cosineSimilarity(double[] v1, double[] v2) {
        if (v1 == null || v2 == null || v1.length == 0 || v1.length != v2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Calculates cosine similarity between two Boxed Double lists.
     */
    public static double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.isEmpty() || v1.size() != v2.size()) {
            return 0.0;
        }

        double[] arr1 = toArray(v1);
        double[] arr2 = toArray(v2);
        return cosineSimilarity(arr1, arr2);
    }

    /**
     * Serializes a vector of doubles into a compact JSON array string.
     */
    public static String toJson(List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * Serializes a primitive double array into a compact JSON array string.
     */
    public static String toJson(double[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * Deserializes a JSON array string into a primitive double array.
     */
    public static double[] fromJson(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return new double[0];
        }
        try {
            return MAPPER.readValue(json, double[].class);
        } catch (JsonProcessingException e) {
            return new double[0];
        }
    }

    /**
     * Converts a List of Doubles to a primitive double array.
     */
    public static double[] toArray(List<Double> list) {
        if (list == null) return new double[0];
        double[] result = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Double val = list.get(i);
            result[i] = val != null ? val : 0.0;
        }
        return result;
    }
}
