package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.BotAskResponse;
import com.bvicam.campusconnect.dto.CitationDto;
import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.entity.SourceType;
import com.bvicam.campusconnect.service.CampusAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-seam integration test for {@link BotController}.
 * Verifies that POST /api/bot/ask delegates user queries to CampusAiService
 * and formats the JSON response with answer, model metadata, and source citations.
 */
@ExtendWith(MockitoExtension.class)
class BotControllerIntegrationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private CampusAiService campusAiService;

    @InjectMocks
    private BotController botController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(botController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/bot/ask delegates to CampusAiService and returns AI generated answer with citations")
    void askBot_withValidQuestion_delegatesToServiceAndReturnsResponse() throws Exception {
        CitationDto citation = new CitationDto(
                10L,
                "BVICAM Overview",
                DocumentCategory.BVICAM_WEB,
                SourceType.WEB_URL,
                "https://bvicam.in",
                0.92
        );
        BotAskResponse expectedResponse = new BotAskResponse(
                "BVICAM is a premier institute established in 2002 under Bharati Vidyapeeth.",
                "gemma4:e4b",
                List.of(citation)
        );

        when(campusAiService.answerQuestion(eq("Tell me about BVICAM")))
                .thenReturn(expectedResponse);

        Map<String, String> payload = Map.of("question", "Tell me about BVICAM");

        mockMvc.perform(post("/api/bot/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("BVICAM is a premier institute established in 2002 under Bharati Vidyapeeth."))
                .andExpect(jsonPath("$.model").value("gemma4:e4b"))
                .andExpect(jsonPath("$.citations[0].title").value("BVICAM Overview"))
                .andExpect(jsonPath("$.citations[0].similarity").value(0.92));

        verify(campusAiService).answerQuestion(eq("Tell me about BVICAM"));
    }

    @Test
    @DisplayName("POST /api/bot/ask handles blank question safely via CampusAiService")
    void askBot_withBlankQuestion_delegatesToService() throws Exception {
        BotAskResponse expectedResponse = BotAskResponse.promptOnly(
                "Please ask a question about BVICAM courses, placements, events, notices, or batchmates.",
                "gemma4:e4b"
        );

        when(campusAiService.answerQuestion(eq("   ")))
                .thenReturn(expectedResponse);

        Map<String, String> payload = Map.of("question", "   ");

        mockMvc.perform(post("/api/bot/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Please ask a question about BVICAM courses, placements, events, notices, or batchmates."));
    }

    @Test
    @DisplayName("POST /api/bot/ask handles explicit JSON null question safely via CampusAiService")
    void askBot_withNullQuestion_delegatesToService() throws Exception {
        BotAskResponse expectedResponse = BotAskResponse.promptOnly(
                "Please ask a question about BVICAM courses, placements, events, notices, or batchmates.",
                "gemma4:e4b"
        );

        when(campusAiService.answerQuestion(null))
                .thenReturn(expectedResponse);

        String jsonWithNull = "{\"question\": null}";

        mockMvc.perform(post("/api/bot/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithNull))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Please ask a question about BVICAM courses, placements, events, notices, or batchmates."));
    }

    @Test
    @DisplayName("GET /api/bot/health returns status of AI runner")
    void checkHealth_returnsStatusMap() throws Exception {
        when(campusAiService.getHealthStatus())
                .thenReturn(Map.of(
                        "status", "ONLINE",
                        "model", "gemma4:e4b",
                        "mode", "Mac AI Runner Connected (gemma4:e4b)"
                ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/bot/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONLINE"))
                .andExpect(jsonPath("$.model").value("gemma4:e4b"));
    }

    @Test
    @DisplayName("POST /api/bot/runner-endpoint forwards shared secret and updates live endpoint")
    void updateRunnerEndpoint_validSecret_returnsUpdated() throws Exception {
        when(campusAiService.getRunnerAuthHeader()).thenReturn("X-GradBot-Key");
        when(campusAiService.updateRunnerEndpoint(eq("runner-secret-42"), eq("https://swift-owl.trycloudflare.com")))
                .thenReturn(Map.of(
                        "status", "UPDATED",
                        "baseUrl", "https://swift-owl.trycloudflare.com",
                        "ollamaOnline", true,
                        "qdrantOnline", true
                ));

        mockMvc.perform(post("/api/bot/runner-endpoint")
                        .header("X-GradBot-Key", "runner-secret-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://swift-owl.trycloudflare.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPDATED"))
                .andExpect(jsonPath("$.baseUrl").value("https://swift-owl.trycloudflare.com"))
                .andExpect(jsonPath("$.ollamaOnline").value(true))
                .andExpect(jsonPath("$.qdrantOnline").value(true));

        verify(campusAiService).updateRunnerEndpoint("runner-secret-42", "https://swift-owl.trycloudflare.com");
    }

    @Test
    @DisplayName("POST /api/bot/runner-endpoint propagates 401 when shared secret is rejected")
    void updateRunnerEndpoint_invalidSecret_returnsUnauthorized() throws Exception {
        when(campusAiService.getRunnerAuthHeader()).thenReturn("X-GradBot-Key");
        when(campusAiService.updateRunnerEndpoint(eq("wrong-key"), eq("https://swift-owl.trycloudflare.com")))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid runner key"));

        mockMvc.perform(post("/api/bot/runner-endpoint")
                        .header("X-GradBot-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://swift-owl.trycloudflare.com\"}"))
                .andExpect(status().isUnauthorized());
    }
}
