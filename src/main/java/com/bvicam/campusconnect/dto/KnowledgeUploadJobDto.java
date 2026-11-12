package com.bvicam.campusconnect.dto;

import java.time.LocalDateTime;

/**
 * Progress snapshot for an asynchronous admin knowledge document ingestion job
 * (used to prevent reverse-proxy HTTP timeouts during multi-page AI Vision OCR).
 */
public record KnowledgeUploadJobDto(
        String jobId,
        String status,
        String phase,
        String message,
        int currentPage,
        int totalPages,
        KnowledgeDocumentDto document,
        String error,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}