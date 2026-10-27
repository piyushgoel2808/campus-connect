package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.KnowledgeDocumentDto;
import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.entity.SourceType;
import com.bvicam.campusconnect.service.KnowledgeIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-seam integration test for {@link AdminKnowledgeController}.
 * Verifies upload, listing, and deletion of knowledge documents.
 */
@ExtendWith(MockitoExtension.class)
class AdminKnowledgeControllerIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private KnowledgeIngestionService ingestionService;

    @InjectMocks
    private AdminKnowledgeController adminKnowledgeController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminKnowledgeController).build();
    }

    @Test
    @DisplayName("POST /api/admin/knowledge/upload processes notice file and returns document summary")
    void uploadNoticeFile_validRequest_returnsDocumentDto() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notice.txt",
                "text/plain",
                "BVICAM Semester 3 Exam schedule details...".getBytes()
        );

        KnowledgeDocumentDto dto = new KnowledgeDocumentDto(
                1L,
                "Exam Schedule Notice",
                DocumentCategory.NOTICE,
                SourceType.FILE_UPLOAD,
                null,
                "notice.txt",
                2,
                LocalDateTime.now()
        );

        when(ingestionService.ingestFile(any(), eq("Exam Schedule Notice"), eq(DocumentCategory.NOTICE)))
                .thenReturn(dto);

        mockMvc.perform(multipart("/api/admin/knowledge/upload")
                        .file(file)
                        .param("title", "Exam Schedule Notice")
                        .param("category", "NOTICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Exam Schedule Notice"))
                .andExpect(jsonPath("$.chunkCount").value(2));

        verify(ingestionService).ingestFile(any(), eq("Exam Schedule Notice"), eq(DocumentCategory.NOTICE));
    }

    @Test
    @DisplayName("POST /api/admin/knowledge/scrape-url scrapes external URL and returns document summary")
    void scrapeUrl_validRequest_returnsDocumentDto() throws Exception {
        KnowledgeDocumentDto dto = new KnowledgeDocumentDto(
                2L,
                "BVICAM MCA Faculty",
                DocumentCategory.BVICAM_WEB,
                SourceType.WEB_URL,
                "https://bvicam.in/faculty",
                null,
                4,
                LocalDateTime.now()
        );

        when(ingestionService.scrapeAndIndexUrl(eq("https://bvicam.in/faculty"), eq("BVICAM MCA Faculty"), eq(DocumentCategory.BVICAM_WEB)))
                .thenReturn(dto);

        String jsonPayload = """
                {
                    "url": "https://bvicam.in/faculty",
                    "title": "BVICAM MCA Faculty",
                    "category": "BVICAM_WEB"
                }
                """;

        mockMvc.perform(post("/api/admin/knowledge/scrape-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.title").value("BVICAM MCA Faculty"))
                .andExpect(jsonPath("$.sourceType").value("WEB_URL"))
                .andExpect(jsonPath("$.sourceUrl").value("https://bvicam.in/faculty"))
                .andExpect(jsonPath("$.chunkCount").value(4));

        verify(ingestionService).scrapeAndIndexUrl(eq("https://bvicam.in/faculty"), eq("BVICAM MCA Faculty"), eq(DocumentCategory.BVICAM_WEB));
    }

    @Test
    @DisplayName("GET /api/admin/knowledge/documents returns list of all indexed documents")
    void getAllDocuments_returnsList() throws Exception {
        KnowledgeDocumentDto dto = new KnowledgeDocumentDto(
                1L,
                "Syllabus 2026",
                DocumentCategory.SYLLABUS,
                SourceType.FILE_UPLOAD,
                null,
                "syllabus.pdf",
                5,
                LocalDateTime.now()
        );

        when(ingestionService.getAllDocuments()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/knowledge/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Syllabus 2026"))
                .andExpect(jsonPath("$[0].chunkCount").value(5));
    }

    @Test
    @DisplayName("DELETE /api/admin/knowledge/documents/{id} delegates deletion to service")
    void deleteDocument_delegatesToService() throws Exception {
        mockMvc.perform(delete("/api/admin/knowledge/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Document deleted successfully"));

        verify(ingestionService).deleteDocument(1L);
    }
}
