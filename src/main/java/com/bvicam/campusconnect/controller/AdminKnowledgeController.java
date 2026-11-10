package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.KnowledgeDocumentDto;
import com.bvicam.campusconnect.entity.DocumentCategory;
import com.bvicam.campusconnect.dto.BvicamSyncStatusDto;
import com.bvicam.campusconnect.service.BvicamPortalSyncService;
import com.bvicam.campusconnect.service.KnowledgeIngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Admin knowledge base management (file upload, web scraping, document listing and deletion).
 */
@RestController
@RequestMapping("/api/admin/knowledge")
@PreAuthorize("hasRole('ADMIN')")
public class AdminKnowledgeController {

    private final KnowledgeIngestionService ingestionService;

    @Autowired(required = false)
    private BvicamPortalSyncService portalSyncService;

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
            @RequestParam(value = "category", defaultValue = "NOTICE") DocumentCategory category,
            Principal principal) throws IOException {
        KnowledgeDocumentDto result = (principal != null && principal.getName() != null)
                ? ingestionService.ingestFile(file, title, category, principal.getName())
                : ingestionService.ingestFile(file, title, category);
        return ResponseEntity.ok(result);
    }

    /**
     * Scrapes an external web URL (e.g. bvicam.in) and indexes it into the knowledge base.
     */
    @PostMapping("/scrape-url")
    public ResponseEntity<KnowledgeDocumentDto> scrapeUrl(
            @RequestBody ScrapeUrlRequest request,
            Principal principal) throws IOException {
        DocumentCategory category = request.category() != null ? request.category() : DocumentCategory.BVICAM_WEB;
        KnowledgeDocumentDto result = (principal != null && principal.getName() != null)
                ? ingestionService.scrapeAndIndexUrl(request.url(), request.title(), category, principal.getName())
                : ingestionService.scrapeAndIndexUrl(request.url(), request.title(), category);
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
     * Triggers an asynchronous, polite, read-only BVICAM portal sync (past + latest content).
     */
    @PostMapping("/sync-bvicam")
    public ResponseEntity<Map<String, Object>> triggerBvicamSync(
            @RequestParam(defaultValue = "FULL") String mode) {
        if (portalSyncService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "BVICAM sync service is unavailable."));
        }
        BvicamPortalSyncService.SyncMode syncMode;
        try {
            syncMode = BvicamPortalSyncService.SyncMode.valueOf(mode.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid sync mode '" + mode + "'. Allowed: FULL, NOTICES, SYLLABUS_AND_INFO, COURSE_MATERIALS.");
        }

        BvicamSyncStatusDto current = portalSyncService.getStatus();
        if (current.running()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "A BVICAM portal sync is already running (" + current.phase() + ").",
                    "status", current
            ));
        }

        portalSyncService.triggerSyncAsync(syncMode);
        return ResponseEntity.accepted().body(Map.of(
                "message", "BVICAM portal sync started (" + syncMode + "). Running politely in the background.",
                "mode", syncMode.name(),
                "status", portalSyncService.getStatus()
        ));
    }

    /**
     * Returns the current or last BVICAM automated sync pipeline status.
     */
    @GetMapping("/sync-status")
    public ResponseEntity<BvicamSyncStatusDto> getBvicamSyncStatus() {
        if (portalSyncService == null) {
            return ResponseEntity.ok(BvicamSyncStatusDto.idle());
        }
        return ResponseEntity.ok(portalSyncService.getStatus());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    /**
     * DTO payload for web URL scraping request.
     */
    public record ScrapeUrlRequest(String url, String title, DocumentCategory category) {
    }
}
