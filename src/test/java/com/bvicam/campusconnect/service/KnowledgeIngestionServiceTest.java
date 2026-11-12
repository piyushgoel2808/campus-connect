package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.dto.KnowledgeDocumentDto;
import com.bvicam.campusconnect.dto.KnowledgeUploadJobDto;
import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.entity.KnowledgeDocument;
import com.bvicam.campusconnect.entity.SourceType;
import com.bvicam.campusconnect.repository.DocumentChunkRepository;
import com.bvicam.campusconnect.repository.KnowledgeDocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;

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

    @Test
    @DisplayName("ingestFile with multi-page scanned PDF renders compact JPEG pages within dimension bounds")
    void ingestFile_multiPageScannedPdf_rendersCompactJpegPages() throws IOException {
        byte[] scannedPdfBytes = createScannedImageOnlyPdfBytes(4, new PDRectangle(4000f, 3000f));
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "Datesheet MCA NOV 2026.pdf",
                "application/pdf",
                scannedPdfBytes
        );

        when(ollamaClientService.extractTextFromImage(anyString(), anyString()))
                .thenReturn("Date 12-12-2026 | MCA-101 | Data Structures");
        when(ollamaClientService.generateEmbedding(anyString()))
                .thenReturn(List.of(0.1, 0.2, 0.3));
        when(documentRepository.save(any(KnowledgeDocument.class)))
                .thenAnswer(invocation -> {
                    KnowledgeDocument doc = invocation.getArgument(0);
                    doc.setId(99L);
                    return doc;
                });

        KnowledgeDocumentDto result = ingestionService.ingestFile(
                pdfFile,
                "Datesheet MCA NOV 2026",
                DocumentCategory.NOTICE
        );

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(99L);

        ArgumentCaptor<String> imageCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> labelCaptor = ArgumentCaptor.forClass(String.class);
        verify(ollamaClientService, times(4)).extractTextFromImage(imageCaptor.capture(), labelCaptor.capture());

        assertThat(labelCaptor.getAllValues()).containsExactly(
                "Page 1 of 4", "Page 2 of 4", "Page 3 of 4", "Page 4 of 4"
        );
        for (String b64 : imageCaptor.getAllValues()) {
            byte[] decodedBytes = Base64.getDecoder().decode(b64);
            BufferedImage rendered = ImageIO.read(new ByteArrayInputStream(decodedBytes));
            assertThat(rendered).isNotNull();
            assertThat(Math.max(rendered.getWidth(), rendered.getHeight())).isLessThanOrEqualTo(1600);
        }
    }

    @Test
    @DisplayName("startAsyncFileUpload completes asynchronously and exposes pollable job status")
    void startAsyncFileUpload_completesSuccessfully() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "circular.txt",
                "text/plain",
                "BVICAM MCA practical schedule starts December 2, 2026.".getBytes()
        );

        when(ollamaClientService.generateEmbedding(anyString()))
                .thenReturn(List.of(0.3, 0.4, 0.5));
        when(documentRepository.save(any(KnowledgeDocument.class)))
                .thenAnswer(invocation -> {
                    KnowledgeDocument doc = invocation.getArgument(0);
                    doc.setId(77L);
                    return doc;
                });

        KnowledgeUploadJobDto initial = ingestionService.startAsyncFileUpload(
                textFile, "Practical Circular", DocumentCategory.NOTICE, "admin@bvicam.in");
        assertThat(initial.jobId()).isNotBlank();

        KnowledgeUploadJobDto finalState = waitForTerminalJobState(initial.jobId());
        assertThat(finalState.status()).isEqualTo("COMPLETED");
        assertThat(finalState.document()).isNotNull();
        assertThat(finalState.document().getId()).isEqualTo(77L);
        assertThat(finalState.error()).isNull();
    }

    @Test
    @DisplayName("startAsyncFileUpload records FAILED state when Vision OCR yields empty text")
    void startAsyncFileUpload_visionFailure_setsFailedStatus() throws Exception {
        byte[] scannedPdfBytes = createScannedImageOnlyPdfBytes("SCANNED");
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "unreadable.pdf",
                "application/pdf",
                scannedPdfBytes
        );

        when(ollamaClientService.extractTextFromImage(anyString(), anyString())).thenReturn("");

        KnowledgeUploadJobDto initial = ingestionService.startAsyncFileUpload(
                pdfFile, "Unreadable Scan", DocumentCategory.NOTICE, "admin@bvicam.in");

        KnowledgeUploadJobDto finalState = waitForTerminalJobState(initial.jobId());
        assertThat(finalState.status()).isEqualTo("FAILED");
        assertThat(finalState.error()).contains("AI Vision OCR");
    }

    @Test
    @DisplayName("getUploadJobStatus throws NoSuchElementException for unknown jobId")
    void getUploadJobStatus_unknownJob_throwsNotFound() {
        assertThatThrownBy(() -> ingestionService.getUploadJobStatus("missing-job-id"))
                .isInstanceOf(NoSuchElementException.class);
    }

    private KnowledgeUploadJobDto waitForTerminalJobState(String jobId) throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            KnowledgeUploadJobDto status = ingestionService.getUploadJobStatus(jobId);
            if ("COMPLETED".equals(status.status()) || "FAILED".equals(status.status())) {
                return status;
            }
            Thread.sleep(25);
        }
        return ingestionService.getUploadJobStatus(jobId);
    }

    private byte[] createScannedImageOnlyPdfBytes(String label) throws IOException {
        return createScannedImageOnlyPdfBytes(1, PDRectangle.LETTER, label);
    }

    private byte[] createScannedImageOnlyPdfBytes(int pages, PDRectangle pageSize) throws IOException {
        return createScannedImageOnlyPdfBytes(pages, pageSize, "BVICAM PAGE");
    }

    private byte[] createScannedImageOnlyPdfBytes(int pages, PDRectangle pageSize, String label) throws IOException {
        BufferedImage renderedImage = new BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = renderedImage.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 300, 100);
        g.setColor(Color.BLACK);
        g.drawString(label, 20, 50);
        g.dispose();

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream pdfOut = new ByteArrayOutputStream()) {
            PDImageXObject imageXObject = LosslessFactory.createFromImage(doc, renderedImage);
            float drawY = Math.max(20f, pageSize.getHeight() - 150f);
            for (int p = 0; p < pages; p++) {
                PDPage page = new PDPage(pageSize);
                doc.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                    contentStream.drawImage(imageXObject, 50, drawY, 300, 100);
                }
            }
            doc.save(pdfOut);
            return pdfOut.toByteArray();
        }
    }
}