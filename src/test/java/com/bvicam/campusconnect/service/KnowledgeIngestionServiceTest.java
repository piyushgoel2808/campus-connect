package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.KnowledgeDocumentDto;
import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.entity.KnowledgeDocument;
import com.bvicam.campusconnect.entity.SourceType;
import com.bvicam.campusconnect.repository.DocumentChunkRepository;
import com.bvicam.campusconnect.repository.KnowledgeDocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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

    @Test
    @DisplayName("ingestFile with scanned image-only PDF triggers Ollama Vision OCR and indexes extracted text")
    void ingestFile_scannedImageOnlyPdf_runsVisionOcrAndPersists() throws IOException {
        byte[] scannedPdfBytes = createScannedImageOnlyPdfBytes("BVICAM MCA END-TERM EXAM DATE SHEET");
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "Final Date Sheet End-Term Exam MCA November December 2026.pdf",
                "application/pdf",
                scannedPdfBytes
        );

        when(ollamaClientService.extractTextFromImage(anyString(), anyString()))
                .thenReturn("| Date | Paper Code | Subject |\n| 12-12-2026 | MCA-101 | Advanced Java |");
        when(ollamaClientService.generateEmbedding(anyString()))
                .thenReturn(List.of(0.11, 0.22, 0.33));
        when(documentRepository.save(any(KnowledgeDocument.class)))
                .thenAnswer(invocation -> {
                    KnowledgeDocument doc = invocation.getArgument(0);
                    doc.setId(42L);
                    return doc;
                });

        KnowledgeDocumentDto result = ingestionService.ingestFile(
                pdfFile,
                "End-Term Exam MCA Date Sheet",
                DocumentCategory.NOTICE
        );

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getChunkCount()).isEqualTo(1);
        verify(ollamaClientService, times(1)).extractTextFromImage(anyString(), contains("Page 1 of 1"));
        verify(ollamaClientService, atLeastOnce()).generateEmbedding(contains("MCA-101"));
        verify(chunkRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("ingestFile with scanned PDF when Vision OCR returns empty throws clear actionable error")
    void ingestFile_scannedPdfVisionOffline_throwsDescriptiveError() throws IOException {
        byte[] scannedPdfBytes = createScannedImageOnlyPdfBytes("SCANNED");
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "scanned-notice.pdf",
                "application/pdf",
                scannedPdfBytes
        );

        when(ollamaClientService.extractTextFromImage(anyString(), anyString()))
                .thenReturn("");

        assertThatThrownBy(() -> ingestionService.ingestFile(pdfFile, "Scanned Notice", DocumentCategory.NOTICE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI Vision OCR");
    }

    @Test
    @DisplayName("ingestFile with PNG circular image extracts text via Ollama Vision OCR")
    void ingestFile_pngImage_runsVisionOcrAndPersists() throws IOException {
        BufferedImage img = new BufferedImage(200, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 60);
        g.setColor(Color.BLACK);
        g.drawString("BVICAM Notice", 20, 35);
        g.dispose();

        ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
        ImageIO.write(img, "png", pngOut);

        MockMultipartFile imageFile = new MockMultipartFile(
                "file",
                "exam-schedule.png",
                "image/png",
                pngOut.toByteArray()
        );

        when(ollamaClientService.extractTextFromImage(anyString(), eq("exam-schedule.png")))
                .thenReturn("BVICAM MCA End-Term Examination Schedule begins December 10, 2026.");
        when(ollamaClientService.generateEmbedding(anyString()))
                .thenReturn(List.of(0.5, -0.3, 0.2));
        when(documentRepository.save(any(KnowledgeDocument.class)))
                .thenAnswer(invocation -> {
                    KnowledgeDocument doc = invocation.getArgument(0);
                    doc.setId(55L);
                    return doc;
                });

        KnowledgeDocumentDto result = ingestionService.ingestFile(imageFile, "Schedule Image", DocumentCategory.NOTICE);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(55L);
        verify(ollamaClientService).extractTextFromImage(anyString(), eq("exam-schedule.png"));
        verify(chunkRepository).saveAll(anyList());
    }

    private byte[] createScannedImageOnlyPdfBytes(String label) throws IOException {
        BufferedImage renderedImage = new BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = renderedImage.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 300, 100);
        g.setColor(Color.BLACK);
        g.drawString(label, 20, 50);
        g.dispose();

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream pdfOut = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDImageXObject imageXObject = LosslessFactory.createFromImage(doc, renderedImage);
            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                contentStream.drawImage(imageXObject, 50, 600, 300, 100);
            }
            doc.save(pdfOut);
            return pdfOut.toByteArray();
        }
    }
}
