package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.repository.EventRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.service.OllamaClientService;
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

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-seam integration test for {@link BotController}.
 * Verifies that POST /api/bot/ask delegates user queries to OllamaClientService
 * and formats the JSON response with the generated answer and model metadata.
 */
@ExtendWith(MockitoExtension.class)
class BotControllerIntegrationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private OllamaClientService ollamaClientService;

    @InjectMocks
    private BotController botController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(botController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/bot/ask delegates to OllamaClientService and returns AI generated answer with model metadata")
    void askBot_withValidQuestion_delegatesToOllamaAndReturnsResponse() throws Exception {
        when(ollamaClientService.generateChatResponse(anyString(), anyString()))
                .thenReturn("BVICAM is a premier institute established in 2002 under Bharati Vidyapeeth.");
        when(ollamaClientService.getChatModel()).thenReturn("gemma4:e4b");

        Map<String, String> payload = Map.of("question", "Tell me about BVICAM");

        mockMvc.perform(post("/api/bot/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("BVICAM is a premier institute established in 2002 under Bharati Vidyapeeth."))
                .andExpect(jsonPath("$.model").value("gemma4:e4b"));

        verify(ollamaClientService).generateChatResponse(anyString(), anyString());
    }

    @Test
    @DisplayName("POST /api/bot/ask handles blank question by returning prompt instruction")
    void askBot_withBlankQuestion_returnsHelpfulPrompt() throws Exception {
        Map<String, String> payload = Map.of("question", "   ");

        mockMvc.perform(post("/api/bot/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Please ask a question about BVICAM courses, placements, events, or batchmates."));
    }

    @Test
    @DisplayName("POST /api/bot/ask handles explicit JSON null question safely")
    void askBot_withNullQuestion_returnsHelpfulPrompt() throws Exception {
        String jsonWithNull = "{\"question\": null}";

        mockMvc.perform(post("/api/bot/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithNull))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Please ask a question about BVICAM courses, placements, events, or batchmates."));
    }
}
