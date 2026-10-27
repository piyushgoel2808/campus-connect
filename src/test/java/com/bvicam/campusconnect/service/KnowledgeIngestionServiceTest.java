package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.KnowledgeDocumentDto;
import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.entity.KnowledgeDocument;
import com.bvicam.campusconnect.entity.SourceType;
import com.bvicam.campusconnect.repository.DocumentChunkRepository;
import com.bvicam.campusconnect.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KnowledgeIngestionService}.
 * Verifies document extraction, embedding generation delegation, chunk persistence, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeIngestionServiceTest {

    @Mock
    private KnowledgeDocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Mock
    private OllamaClientService ollamaClientService;

    @InjectMocks
    private KnowledgeIngestionService ingestionService;

    @Test
    @DisplayName("ingestFile with valid text file chunks and persists document with embeddings")
    void ingestFile_validTextFile_success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "exam-notice.txt",
                "text/plain",
                "Mid-term examination for MCA 1st and 3rd semester starts from November 15, 2026.".getBytes()
        );

        when(ollamaClientService.generateEmbedding(anyString()))
                .thenReturn(List.of(0.12, -0.45, 0.88));

        when(documentRepository.save(any(KnowledgeDocument.class)))
                .thenAnswer(invocation -> {
                    KnowledgeDocument doc = invocation.getArgument(0);
                    doc.setId(10L);
                    return doc;
                });

        KnowledgeDocumentDto result = ingestionService.ingestFile(file, "Exam Notice", DocumentCategory.NOTICE);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getTitle()).isEqualTo("Exam Notice");
        assertThat(result.getCategory()).isEqualTo(DocumentCategory.NOTICE);
        assertThat(result.getSourceType()).isEqualTo(SourceType.FILE_UPLOAD);
        assertThat(result.getChunkCount()).isGreaterThan(0);

        verify(chunkRepository).saveAll(anyList());
        verify(ollamaClientService, atLeastOnce()).generateEmbedding(anyString());
    }

    @Test
    @DisplayName("ingestFile with null or empty file throws IllegalArgumentException")
    void ingestFile_emptyFile_throwsException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> ingestionService.ingestFile(null, "Title", DocumentCategory.NOTICE))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ingestionService.ingestFile(emptyFile, "Title", DocumentCategory.NOTICE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("scrapeAndIndexUrl with invalid protocol throws IllegalArgumentException")
    void scrapeAndIndexUrl_invalidUrl_throwsException() {
        assertThatThrownBy(() -> ingestionService.scrapeAndIndexUrl("ftp://bvicam.in/notice", "Title", DocumentCategory.BVICAM_WEB))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with http:// or https://");

        assertThatThrownBy(() -> ingestionService.scrapeAndIndexUrl(null, "Title", DocumentCategory.BVICAM_WEB))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deleteDocument delegates deletion to repository")
    void deleteDocument_delegatesToRepository() {
        ingestionService.deleteDocument(5L);
        verify(documentRepository).deleteById(5L);
    }
}
