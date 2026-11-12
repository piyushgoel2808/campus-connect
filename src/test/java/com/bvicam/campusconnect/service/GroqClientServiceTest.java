package com.bvicam.campusconnect.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GroqClientServiceTest {

    @Test
    void isConfigured_falseWhenBlank() {
        GroqClientService service = new GroqClientService("", "qwen/qwen3.8-27b", "https://api.groq.com/openai/v1", 30, new ObjectMapper());
        assertFalse(service.isConfigured());
        assertFalse(service.isHealthy());
        assertNull(service.generateChatResponse("System", "User"));
        assertEquals("", service.extractTextFromImage("base64", "prompt"));
    }

    @Test
    void isConfigured_trueWhenKeyProvided() {
        GroqClientService service = new GroqClientService("gsk_test123", "qwen/qwen3.8-27b", "https://api.groq.com/openai/v1", 30, new ObjectMapper());
        assertTrue(service.isConfigured());
        assertEquals("qwen/qwen3.8-27b", service.getModel());
    }
}
