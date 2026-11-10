package com.bvicam.campusconnect.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing an ingested document or page in the GradBot knowledge base.
 */
@Entity
@Table(name = "knowledge_documents", indexes = {
        @Index(name = "idx_doc_category", columnList = "category"),
        @Index(name = "idx_doc_created", columnList = "created_at"),
        @Index(name = "idx_doc_source_type", columnList = "source_type")
})
@Getter
@Setter
public class KnowledgeDocument {

    public enum SyncStatus {
        INDEXED,
        DB_ONLY,
        PENDING_QDRANT_SYNC
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentCategory category = DocumentCategory.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SourceType sourceType = SourceType.FILE_UPLOAD;

    @Column(length = 1024)
    private String sourceUrl;

    private String originalFileName;

    @Column(nullable = false)
    private int chunkCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private SyncStatus syncStatus = SyncStatus.INDEXED;

    /**
     * Stable identifier for idempotent automated syncing (e.g., {@code circular:4337} or canonical URL).
     */
    @Column(name = "external_key", unique = true, length = 512)
    private String externalKey;

    /**
     * SHA-256 checksum of normalized document content for change detection during automated sync.
     */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /**
     * Official circular or academic publication date string when available (e.g., {@code 19-08-2026}).
     */
    @Column(name = "published_date", length = 64)
    private String publishedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private User uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DocumentChunk> chunks = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.syncStatus == null) {
            this.syncStatus = SyncStatus.INDEXED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
