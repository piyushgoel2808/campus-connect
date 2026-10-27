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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
}
