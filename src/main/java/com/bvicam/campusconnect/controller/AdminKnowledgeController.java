package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.KnowledgeDocumentDto;
import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.service.KnowledgeIngestionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Admin knowledge base management (file upload, web scraping, document listing and deletion).
 */
@RestController
@RequestMapping("/api/admin/knowledge")
public class AdminKnowledgeController {

    private final KnowledgeIngestionService ingestionService;

    public AdminKnowledgeController(KnowledgeIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Uploads and indexes a notice, circular, or syllabus document into GradBot's RAG knowledge base.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDocumentDto> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", defaultValue = "NOTICE") DocumentCategory category) throws IOException {
        KnowledgeDocumentDto result = ingestionService.ingestFile(file, title, category);
        return ResponseEntity.ok(result);
    }

    /**
     * Scrapes an external web URL (e.g. bvicam.in) and indexes it into the knowledge base.
     */
    @PostMapping("/scrape-url")
    public ResponseEntity<KnowledgeDocumentDto> scrapeUrl(
            @RequestBody ScrapeUrlRequest request) throws IOException {
        KnowledgeDocumentDto result = ingestionService.scrapeAndIndexUrl(
                request.url(),
                request.title(),
                request.category() != null ? request.category() : DocumentCategory.BVICAM_WEB
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Lists all indexed documents in the knowledge base.
     */
    @GetMapping("/documents")
    public ResponseEntity<List<KnowledgeDocumentDto>> getAllDocuments() {
        return ResponseEntity.ok(ingestionService.getAllDocuments());
    }

    /**
     * Deletes a knowledge document and its associated chunks.
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, String>> deleteDocument(@PathVariable Long id) {
        ingestionService.deleteDocument(id);
        return ResponseEntity.ok(Map.of("message", "Document deleted successfully"));
    }

    /**
     * DTO payload for web URL scraping request.
     */
    public record ScrapeUrlRequest(String url, String title, DocumentCategory category) {
    }
}
