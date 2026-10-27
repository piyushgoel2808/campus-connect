package com.bvicam.campusconnect.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Entity representing an individual text chunk and its serialized vector embedding.
 */
@Entity
@Table(name = "document_chunks", indexes = {
        @Index(name = "idx_chunk_doc_id", columnList = "document_id"),
        @Index(name = "idx_chunk_index", columnList = "chunk_index")
})
@Getter
@Setter
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocument document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex = 0;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String chunkText;

    @Lob
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String embeddingJson;

    @Transient
    private double[] cachedEmbedding;
}
