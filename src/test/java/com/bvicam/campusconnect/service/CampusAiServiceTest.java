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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        when(chunkRepository.findRecentWithDocument(any())).thenReturn(List.of(chunk));

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
        when(chunkRepository.findRecentWithDocument(any())).thenReturn(List.of());

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

    @Test
    @DisplayName("updateRunnerEndpoint fails closed when token unconfigured or invalid")
    void updateRunnerEndpoint_authChecks_failClosed() {
        when(ollamaClientService.hasAuthToken()).thenReturn(false);
        assertThatThrownBy(() -> campusAiService.updateRunnerEndpoint("any", "https://fast-fox.trycloudflare.com"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        when(ollamaClientService.hasAuthToken()).thenReturn(true);
        when(ollamaClientService.matchesAuthToken("wrong")).thenReturn(false);
        assertThatThrownBy(() -> campusAiService.updateRunnerEndpoint("wrong", "https://fast-fox.trycloudflare.com"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(ollamaClientService, never()).updateBaseUrl(anyString());
    }

    @Test
    @DisplayName("updateRunnerEndpoint rejects non-HTTPS, disallowed domains, ports, paths, and private IPs")
    void updateRunnerEndpoint_ssrfAndSchemeGuards_rejectBadUrls() {
        when(ollamaClientService.hasAuthToken()).thenReturn(true);
        when(ollamaClientService.matchesAuthToken("good-secret")).thenReturn(true);

        List<String> invalidUrls = List.of(
                "http://fast-fox.trycloudflare.com",
                "https://trycloudflare.com",
                "https://evil.example.com",
                "https://user:pass@fast-fox.trycloudflare.com",
                "https://fast-fox.trycloudflare.com:8443",
                "https://fast-fox.trycloudflare.com/qdrant",
                "https://fast-fox.trycloudflare.com?foo=bar",
                "https://127.0.0.1"
        );
        for (String badUrl : invalidUrls) {
            assertThatThrownBy(() -> campusAiService.updateRunnerEndpoint("good-secret", badUrl))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }

        // Private RFC1918 DNS resolution SSRF test via package-private override
        QdrantClientService mockQdrant = mock(QdrantClientService.class);
        CampusAiService ssrfTestService = new CampusAiService(
                chunkRepository, eventRepository, jobRepository, userRepository, ollamaClientService, mockQdrant) {
            @Override
            InetAddress[] resolveHostAddresses(String host) {
                return new InetAddress[]{InetAddress.getLoopbackAddress()};
            }
        };
        assertThatThrownBy(() -> ssrfTestService.updateRunnerEndpoint("good-secret", "https://rebind-test.trycloudflare.com"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(ollamaClientService, never()).updateBaseUrl(anyString());
        verify(mockQdrant, never()).updateBaseUrl(anyString());
    }

    @Test
    @DisplayName("updateRunnerEndpoint probes tunnel first (502 if down) and updates both Ollama and Qdrant when reachable")
    void updateRunnerEndpoint_probeAndAtomicSwap() throws Exception {
        QdrantClientService mockQdrant = mock(QdrantClientService.class);
        InetAddress publicIp = InetAddress.getByAddress("fast-fox.trycloudflare.com", new byte[]{104, 16, 1, 1});
        CampusAiService spyService = new CampusAiService(
                chunkRepository, eventRepository, jobRepository, userRepository, ollamaClientService, mockQdrant) {
            @Override
            InetAddress[] resolveHostAddresses(String host) {
                return new InetAddress[]{publicIp};
            }
        };
        spyService.setHealthProbeRetryDelayMs(0L);

        when(ollamaClientService.hasAuthToken()).thenReturn(true);
        when(ollamaClientService.matchesAuthToken("good-secret")).thenReturn(true);
        when(ollamaClientService.isHealthyAt("https://fast-fox.trycloudflare.com")).thenReturn(false);

        assertThatThrownBy(() -> spyService.updateRunnerEndpoint("good-secret", "https://fast-fox.trycloudflare.com/"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        verify(ollamaClientService, never()).updateBaseUrl(anyString());

        when(ollamaClientService.isHealthyAt("https://fast-fox.trycloudflare.com")).thenReturn(true);
        when(ollamaClientService.getChatModel()).thenReturn("gemma4:e4b");
        when(ollamaClientService.getEmbeddingModel()).thenReturn("embeddinggemma-2:latest");
        when(mockQdrant.isHealthy()).thenReturn(true);

        Map<String, Object> result = spyService.updateRunnerEndpoint("good-secret", "https://Fast-Fox.trycloudflare.com/");

        assertThat(result.get("status")).isEqualTo("UPDATED");
        assertThat(result.get("baseUrl")).isEqualTo("https://fast-fox.trycloudflare.com");
        assertThat(result.get("ollamaOnline")).isEqualTo(true);
        assertThat(result.get("qdrantOnline")).isEqualTo(true);
        verify(ollamaClientService).updateBaseUrl("https://fast-fox.trycloudflare.com");
        verify(mockQdrant).updateBaseUrl("https://fast-fox.trycloudflare.com/qdrant");
    }
}
