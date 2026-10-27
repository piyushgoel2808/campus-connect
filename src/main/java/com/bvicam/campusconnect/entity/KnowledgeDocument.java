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
        @Index(name = "idx_doc_created", columnList = "created_at")
})
@Getter
@Setter
public class KnowledgeDocument {

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
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
