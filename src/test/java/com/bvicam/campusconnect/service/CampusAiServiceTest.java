package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.BotAskResponse;
import com.bvicam.campusconnect.entity.*;
import com.bvicam.campusconnect.repository.DocumentChunkRepository;
import com.bvicam.campusconnect.repository.EventRepository;
import com.bvicam.campusconnect.repository.JobRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import com.bvicam.campusconnect.util.VectorUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CampusAiService}.
 * Verifies RAG retrieval ranking, citation aggregation, live context injection, and Ollama delegation.
 */
@ExtendWith(MockitoExtension.class)
class CampusAiServiceTest {

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OllamaClientService ollamaClientService;

    @InjectMocks
    private CampusAiService campusAiService;

    @Test
    @DisplayName("answerQuestion with blank question returns helpful prompt without calling Ollama")
    void answerQuestion_blankQuery_returnsHelpfulPrompt() {
        BotAskResponse response = campusAiService.answerQuestion("   ");

        assertThat(response).isNotNull();
        assertThat(response.answer()).contains("Please ask a question about BVICAM");
        assertThat(response.citations()).isEmpty();
        verify(ollamaClientService, never()).generateChatResponse(anyString(), anyString());
        verify(ollamaClientService, never()).generateEmbedding(anyString());
    }

    @Test
    @DisplayName("answerQuestion retrieves matching document chunks via cosine similarity and returns citations")
    void answerQuestion_withMatchingKnowledgeChunks_returnsCitationsAndSynthesizedAnswer() {
        // Query embedding: [1.0, 0.0]
        when(ollamaClientService.generateEmbedding("When is MCA semester 3 exam?"))
                .thenReturn(List.of(1.0, 0.0));
        when(ollamaClientService.getChatModel()).thenReturn("gemma4:e4b");

        // Mock a matching document and chunk
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(101L);
        doc.setTitle("Exam Schedule Nov 2026");
        doc.setCategory(DocumentCategory.NOTICE);
        doc.setSourceType(SourceType.FILE_UPLOAD);

        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(501L);
        chunk.setDocument(doc);
        chunk.setChunkIndex(0);
        chunk.setChunkText("MCA 3rd semester examinations commence on November 20, 2026.");
        // Chunk vector: [0.95, 0.05] (high cosine similarity)
        chunk.setEmbeddingJson(VectorUtil.toJson(List.of(0.95, 0.05)));

        when(chunkRepository.findAllWithDocument()).thenReturn(List.of(chunk));

        when(ollamaClientService.generateChatResponse(anyString(), eq("When is MCA semester 3 exam?")))
                .thenReturn("The MCA 3rd semester examinations will begin on November 20, 2026 as per the Exam Schedule Notice.");

        BotAskResponse response = campusAiService.answerQuestion("When is MCA semester 3 exam?");

        assertThat(response).isNotNull();
        assertThat(response.answer()).contains("November 20, 2026");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).title()).isEqualTo("Exam Schedule Nov 2026");
        assertThat(response.citations().get(0).category()).isEqualTo(DocumentCategory.NOTICE);
        assertThat(response.citations().get(0).similarity()).isGreaterThan(0.9);

        // Verify that retrieved chunk text was injected into system prompt
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollamaClientService).generateChatResponse(promptCaptor.capture(), eq("When is MCA semester 3 exam?"));
        assertThat(promptCaptor.getValue()).contains("MCA 3rd semester examinations commence on November 20, 2026");
    }

    @Test
    @DisplayName("answerQuestion injects live database events when query asks about upcoming campus events")
    void answerQuestion_withEventQuery_injectsLiveEvents() {
        when(ollamaClientService.generateEmbedding("Any upcoming events?"))
                .thenReturn(List.of(0.5, 0.5));
        when(ollamaClientService.getChatModel()).thenReturn("gemma4:e4b");
        when(chunkRepository.findAllWithDocument()).thenReturn(List.of());

        Event event = new Event();
        event.setTitle("INDIACom 2026 Conference");
        event.setLocation("Auditorium A");
        event.setDateTime(LocalDateTime.now().plusDays(5));

        when(eventRepository.findTop5ByDateTimeAfterOrderByDateTimeAsc(any()))
                .thenReturn(List.of(event));

        when(ollamaClientService.generateChatResponse(anyString(), anyString()))
                .thenReturn("Yes! INDIACom 2026 Conference is scheduled in Auditorium A.");

        BotAskResponse response = campusAiService.answerQuestion("Any upcoming events?");

        assertThat(response).isNotNull();
        assertThat(response.answer()).contains("INDIACom 2026");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollamaClientService).generateChatResponse(promptCaptor.capture(), eq("Any upcoming events?"));
        assertThat(promptCaptor.getValue()).contains("INDIACom 2026 Conference at Auditorium A");
    }
}
